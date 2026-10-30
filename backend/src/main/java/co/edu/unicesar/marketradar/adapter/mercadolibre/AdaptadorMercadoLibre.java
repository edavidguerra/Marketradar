package co.edu.unicesar.marketradar.adapter.mercadolibre;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplaceBase;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Integración con la API oficial de Mercado Libre Colombia (sitio MCO).
 *
 * <p>Busca con {@code GET /sites/MCO/search} y un token de acceso OAuth que se renueva solo con el refresh
 * token (ver {@link TokenMercadoLibre}). La búsqueda no trae calificaciones, así que para los primeros
 * resultados se consultan en paralelo en {@code GET /reviews/item/{id}}; si esa consulta falla, la oferta
 * se muestra sin reseñas.</p>
 */
@Component
public class AdaptadorMercadoLibre extends AdaptadorMarketplaceBase {

    public static final String CODIGO = "mercadolibre";

    /** Resultados que se piden por búsqueda; es el valor por defecto de la API. */
    static final int LIMITE_RESULTADOS = 50;

    private static final Duration ESPERA_RESENAS = Duration.ofSeconds(2);

    private final PropiedadesMarketradar.MercadoLibre configuracion;
    private final RestClient cliente;
    private final TokenMercadoLibre token;

    /**
     * Constructor que usa Spring: crea el cliente HTTP con tiempos de espera cortos.
     *
     * @param propiedades configuración de Marketradar
     * @param conversor conversor de moneda
     * @param builder constructor de clientes HTTP de Spring
     */
    @Autowired
    public AdaptadorMercadoLibre(PropiedadesMarketradar propiedades, ConversorMoneda conversor,
                                 RestClient.Builder builder) {
        this(propiedades, conversor, builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(Duration.ofSeconds(3))
                                .withReadTimeout(Duration.ofSeconds(4))))
                .baseUrl(propiedades.marketplaces().mercadolibre().urlApi())
                .build(), Clock.systemUTC());
    }

    /**
     * Constructor para las pruebas, con el cliente HTTP y el reloj ya creados.
     *
     * @param propiedades configuración de Marketradar
     * @param conversor conversor de moneda
     * @param cliente cliente HTTP con la dirección base de la API
     * @param reloj reloj para el vencimiento del token
     */
    AdaptadorMercadoLibre(PropiedadesMarketradar propiedades, ConversorMoneda conversor, RestClient cliente, Clock reloj) {
        super(CODIGO, conversor, 5);
        this.configuracion = propiedades.marketplaces().mercadolibre();
        this.cliente = cliente;
        Path archivo = tieneValor(configuracion.archivoToken()) ? Path.of(configuracion.archivoToken()) : null;
        this.token = new TokenMercadoLibre(cliente, configuracion.clientId(), configuracion.clientSecret(),
                configuracion.refreshToken(), archivo, reloj);
    }

    @Override
    public boolean estaHabilitado() {
        return configuracion.habilitado()
                && tieneValor(configuracion.clientId())
                && tieneValor(configuracion.clientSecret())
                && tieneValor(configuracion.refreshToken());
    }

    @Override
    protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
        RespuestaBusqueda respuesta = buscarConReintento(consulta.getTermino());
        if (respuesta == null || respuesta.results() == null) {
            return List.of();
        }
        Map<String, Resenas> resenas = consultarResenas(respuesta.results());
        List<OfertaNormalizada> ofertas = new ArrayList<>();
        for (Resultado resultado : respuesta.results()) {
            if (resultado.id() == null || resultado.title() == null || resultado.price() == null
                    || resultado.currencyId() == null || resultado.permalink() == null) {
                continue;
            }
            ofertas.add(aOferta(resultado, resenas.get(resultado.id())));
        }
        return ofertas;
    }

    /**
     * Busca con el token vigente. Si la API responde 401 (token vencido o revocado), pide otro token y
     * repite la búsqueda una vez.
     */
    private RespuestaBusqueda buscarConReintento(String termino) {
        try {
            return buscar(termino);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() != HttpStatus.UNAUTHORIZED.value()) {
                throw errorDeBusqueda(e);
            }
            token.invalidar();
        }
        try {
            return buscar(termino);
        } catch (HttpClientErrorException e) {
            throw errorDeBusqueda(e);
        }
    }

    private RespuestaBusqueda buscar(String termino) {
        return cliente.get()
                .uri(uri -> uri.path("/sites/{sitio}/search")
                        .queryParam("q", termino)
                        .queryParam("limit", LIMITE_RESULTADOS)
                        .build(configuracion.sitio()))
                .header("Authorization", "Bearer " + token.vigente())
                .retrieve()
                .body(RespuestaBusqueda.class);
    }

    private static MarketplaceException errorDeBusqueda(HttpClientErrorException e) {
        int codigo = e.getStatusCode().value();
        if (codigo == HttpStatus.FORBIDDEN.value()) {
            return new MarketplaceException(CODIGO,
                    "Mercado Libre negó el acceso a la búsqueda (403). Revise que la aplicación tenga permiso de lectura.", e);
        }
        if (codigo == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new MarketplaceException(CODIGO, "Mercado Libre limitó las consultas (429). Intente más tarde.", e);
        }
        return new MarketplaceException(CODIGO, "Mercado Libre respondió con error " + codigo, e);
    }

    /**
     * Consulta en paralelo las reseñas de los primeros resultados. Las que fallan o tardan más de dos
     * segundos se omiten.
     */
    private Map<String, Resenas> consultarResenas(List<Resultado> resultados) {
        int cantidad = Math.min(configuracion.resenasPorBusqueda(), resultados.size());
        Map<String, Resenas> encontradas = new ConcurrentHashMap<>();
        if (cantidad <= 0) {
            return encontradas;
        }
        String acceso = token.vigente();
        try (ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> tareas = new ArrayList<>();
            for (Resultado resultado : resultados.subList(0, cantidad)) {
                if (resultado.id() == null) {
                    continue;
                }
                tareas.add(hilos.submit(() -> {
                    Resenas resenas = cliente.get()
                            .uri("/reviews/item/{id}", resultado.id())
                            .header("Authorization", "Bearer " + acceso)
                            .retrieve()
                            .body(Resenas.class);
                    if (resenas != null) {
                        encontradas.put(resultado.id(), resenas);
                    }
                }));
            }
            long limite = System.nanoTime() + ESPERA_RESENAS.toNanos();
            for (Future<?> tarea : tareas) {
                try {
                    tarea.get(Math.max(0, limite - System.nanoTime()), TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    tarea.cancel(true);
                }
            }
            hilos.shutdownNow();
        }
        return encontradas;
    }

    private static OfertaNormalizada aOferta(Resultado resultado, Resenas resenas) {
        OfertaNormalizada.Builder oferta = OfertaNormalizada.builder()
                .codigoMarketplace(CODIGO)
                .idExterno(resultado.id())
                .titulo(resultado.title())
                .precio(resultado.price())
                .moneda(resultado.currencyId())
                .urlDestino(resultado.permalink())
                .urlImagen(aHttps(resultado.thumbnail()))
                .condicion(traducirCondicion(resultado.condition()))
                .vendedor(resultado.seller() == null ? null : resultado.seller().nickname());
        if (resultado.shipping() != null && Boolean.TRUE.equals(resultado.shipping().freeShipping())) {
            oferta.costoEnvio(BigDecimal.ZERO);
        }
        if (resenas != null && resenas.total() > 0 && resenas.ratingAverage() != null) {
            oferta.calificacion(resenas.ratingAverage().setScale(1, RoundingMode.HALF_UP))
                    .totalResenas(resenas.total());
        }
        return oferta.build();
    }

    /**
     * Traduce la condición de Mercado Libre a las palabras que muestra Marketradar.
     *
     * @param condicion {@code new}, {@code used}, {@code refurbished} u otra
     * @return condición en español, o {@code null} si no viene
     */
    static String traducirCondicion(String condicion) {
        if (condicion == null) {
            return null;
        }
        return switch (condicion) {
            case "new" -> "nuevo";
            case "used" -> "usado";
            case "refurbished" -> "reacondicionado";
            default -> condicion;
        };
    }

    private static String aHttps(String url) {
        return url != null && url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
    }

    /**
     * Respuesta de {@code GET /sites/{sitio}/search}.
     *
     * @param results resultados de la búsqueda
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaBusqueda(List<Resultado> results) {
    }

    /**
     * Publicación dentro de la búsqueda.
     *
     * @param id identificador, por ejemplo {@code MCO123456}
     * @param title título
     * @param price precio
     * @param currencyId moneda ({@code COP} en Colombia)
     * @param permalink enlace a la publicación
     * @param thumbnail imagen pequeña
     * @param condition {@code new}, {@code used} o {@code refurbished}
     * @param shipping datos del envío
     * @param seller vendedor; no siempre viene
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultado(String id, String title, BigDecimal price,
                     @JsonProperty("currency_id") String currencyId,
                     String permalink, String thumbnail, String condition,
                     Envio shipping, Vendedor seller) {
    }

    /** @param freeShipping si el envío es gratis */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envio(@JsonProperty("free_shipping") Boolean freeShipping) {
    }

    /** @param nickname apodo del vendedor */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Vendedor(String nickname) {
    }

    /**
     * Respuesta de {@code GET /reviews/item/{id}}.
     *
     * @param ratingAverage promedio de estrellas
     * @param ratingLevels cantidad de calificaciones por número de estrellas
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resenas(@JsonProperty("rating_average") BigDecimal ratingAverage,
                   @JsonProperty("rating_levels") Map<String, Integer> ratingLevels) {

        /**
         * @return total de calificaciones, sumando todos los niveles de estrellas
         */
        int total() {
            return ratingLevels == null ? 0
                    : ratingLevels.values().stream().filter(v -> v != null).mapToInt(Integer::intValue).sum();
        }
    }
}
