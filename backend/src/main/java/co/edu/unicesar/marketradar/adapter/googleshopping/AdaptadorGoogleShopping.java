package co.edu.unicesar.marketradar.adapter.googleshopping;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplaceBase;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ResenasProducto;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Precios reales de tiendas colombianas a través de Google Shopping, con la API de SerpApi
 * ({@code GET https://serpapi.com/search.json?engine=google_shopping}).
 *
 * <p>Una sola consulta trae productos de muchas tiendas (Éxito, Falabella, Alkosto, Mercado Libre...).
 * La tienda real de cada producto queda en {@code vendedor}. La clave se crea en https://serpapi.com y va en
 * {@code SERPAPI_API_KEY}; nunca en el código.</p>
 */
@Component
public class AdaptadorGoogleShopping extends AdaptadorMarketplaceBase {

    public static final String CODIGO = "googleshopping";

    /** Las claves de reseñas de SerpApi son largas, pero no más que esto. */
    static final int LARGO_TOKEN = 4096;

    /** Opiniones escritas que se muestran de cada producto. */
    static final int MAXIMO_RESENAS = 10;

    /** Se piden más de los que caben en una página, para que el filtro de complementos tenga de dónde elegir. */
    static final int LIMITE_RESULTADOS = 40;
    /** Búsqueda de Google Shopping Colombia, para los productos que llegan sin enlace. */
    static final String BUSQUEDA_GOOGLE = "https://www.google.com.co/search?tbm=shop&gl=co&hl=es&q=";
    private static final Logger LOG = LoggerFactory.getLogger(AdaptadorGoogleShopping.class);

    private final boolean habilitado;
    private final String apiKey;
    private final RestClient cliente;

    /**
     * Constructor que usa Spring: crea el cliente HTTP con tiempos de espera.
     *
     * @param conversor conversor de moneda
     * @param builder constructor de clientes HTTP de Spring
     * @param habilitado si se consulta en las búsquedas
     * @param apiKey clave privada de SerpApi
     * @param urlApi dirección base de SerpApi
     */
    @Autowired
    public AdaptadorGoogleShopping(ConversorMoneda conversor, RestClient.Builder builder,
                                   @Value("${marketradar.marketplaces.google-shopping.habilitado:false}") boolean habilitado,
                                   @Value("${marketradar.marketplaces.google-shopping.api-key:}") String apiKey,
                                   @Value("${marketradar.marketplaces.google-shopping.url-api:https://serpapi.com}") String urlApi) {
        this(conversor, builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(Duration.ofSeconds(3))
                                .withReadTimeout(Duration.ofSeconds(8))))
                .baseUrl(urlApi)
                .build(), habilitado, apiKey);
    }

    /**
     * Constructor para las pruebas, con el cliente HTTP ya creado.
     *
     * @param conversor conversor de moneda
     * @param cliente cliente HTTP con la dirección base de SerpApi
     * @param habilitado si se consulta
     * @param apiKey clave de SerpApi
     */
    AdaptadorGoogleShopping(ConversorMoneda conversor, RestClient cliente, boolean habilitado, String apiKey) {
        super(CODIGO, conversor, 2);
        this.cliente = cliente;
        this.habilitado = habilitado;
        this.apiKey = apiKey;
    }

    @Override
    public boolean estaHabilitado() {
        return habilitado && tieneValor(apiKey);
    }

    @Override
    protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
        Respuesta respuesta;
        try {
            respuesta = cliente.get()
                    // Los valores van como variables para que se codifiquen completos ("+" como %2B, no como espacio).
                    .uri(uri -> uri.path("/search.json")
                            .queryParam("engine", "google_shopping")
                            .queryParam("q", "{q}")
                            .queryParam("gl", "co")
                            .queryParam("hl", "es")
                            .queryParam("google_domain", "google.com.co")
                            .queryParam("api_key", "{clave}")
                            .build(Map.of("q", consulta.getTermino(), "clave", apiKey)))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Respuesta.class);
        } catch (RestClientResponseException e) {
            throw errorHttp(e);
        } catch (RestClientException e) {
            // No se encadena la causa: su mensaje trae la URL completa, con la clave de SerpApi.
            throw new MarketplaceException(CODIGO, "No se pudo consultar SerpApi: " + e.getClass().getSimpleName());
        }
        if (respuesta == null) {
            return List.of();
        }
        if (respuesta.error() != null) {
            // SerpApi responde 200 con este texto cuando Google no encontró nada.
            if (respuesta.error().toLowerCase(Locale.ROOT).contains("hasn't returned any results")) {
                return List.of();
            }
            throw new MarketplaceException(CODIGO, "SerpApi respondió: " + respuesta.error());
        }
        Map<String, OfertaNormalizada> ofertas = new LinkedHashMap<>();
        List<Resultado> todos = todosLosResultados(respuesta);
        int sinEnlacePropio = 0;
        for (Resultado resultado : todos) {
            OfertaNormalizada oferta = aOferta(resultado);
            if (oferta != null) {
                ofertas.putIfAbsent(oferta.getIdExterno(), oferta);
                if (oferta.getUrlDestino().startsWith(BUSQUEDA_GOOGLE)) {
                    sinEnlacePropio++;
                }
            }
            if (ofertas.size() == LIMITE_RESULTADOS) {
                break;
            }
        }
        // Sirve para revisar en los registros de Render qué llegó, sin mostrar la clave.
        LOG.info("Google Shopping \"{}\": {} productos recibidos, {} usados ({} con enlace a Google Shopping)",
                consulta.getTermino(), todos.size(), ofertas.size(), sinEnlacePropio);
        return List.copyOf(ofertas.values());
    }

    /**
     * Google no siempre entrega los productos en la misma lista. Con productos populares ("playstation 5",
     * "iphone 15") los agrupa por categoría y {@code shopping_results} llega vacío o lleno de juegos y
     * accesorios; arriba de la página a veces hay otros productos destacados. Se juntan todas las listas, sin
     * repetir, y van primero las categorías, que es donde Google pone el producto en sí (la consola).
     */
    private static List<Resultado> todosLosResultados(Respuesta respuesta) {
        List<Resultado> todos = new ArrayList<>();
        if (respuesta.porCategoria() != null) {
            for (Categoria categoria : respuesta.porCategoria()) {
                if (categoria != null && categoria.resultados() != null) {
                    todos.addAll(categoria.resultados());
                }
            }
        }
        if (respuesta.resultados() != null) {
            todos.addAll(respuesta.resultados());
        }
        if (respuesta.destacados() != null) {
            todos.addAll(respuesta.destacados());
        }
        todos.removeIf(java.util.Objects::isNull);
        return todos;
    }

    private static OfertaNormalizada aOferta(Resultado resultado) {
        if (!tieneValor(resultado.titulo()) || resultado.precio() == null || resultado.precio().signum() <= 0) {
            return null;
        }
        // Se prefiere el enlace directo a la tienda; si no viene, la página del producto en Google Shopping. En
        // Colombia casi ningún producto trae esos enlaces, así que si faltan se busca el producto en Google
        // Shopping, donde aparece con la tienda que lo vende. Solo se aceptan enlaces https.
        String enlace = esSeguro(resultado.link()) ? resultado.link()
                : esSeguro(resultado.productLink()) ? resultado.productLink()
                : enlaceBusqueda(resultado.titulo(), resultado.tienda());
        String id = tieneValor(resultado.productId()) ? resultado.productId()
                : Integer.toHexString(enlace.hashCode());
        return OfertaNormalizada.builder()
                .codigoMarketplace(CODIGO)
                .idExterno(recortar(id, 120))
                .titulo(recortar(resultado.titulo(), 400))
                .precio(resultado.precio().setScale(2, RoundingMode.HALF_UP))
                .moneda(moneda(resultado.precioTexto()))
                .costoEnvio(costoEnvio(resultado.envio()))
                .vendedor(tieneValor(resultado.tienda()) ? recortar(resultado.tienda(), 150) : null)
                .urlDestino(enlace)
                .urlImagen(imagenSegura(resultado.imagen()))
                .calificacion(calificacion(resultado.calificacion()))
                .totalResenas(resultado.resenas() != null && resultado.resenas() > 0 ? resultado.resenas() : null)
                .tokenResenas(tieneValor(resultado.tokenResenas()) && resultado.tokenResenas().length() <= LARGO_TOKEN
                        ? resultado.tokenResenas() : null)
                .build();
    }

    /**
     * Reseñas de un producto con la API de producto de Google de SerpApi
     * ({@code engine=google_immersive_product}). Cada llamada gasta una búsqueda del plan, por eso solo se hace
     * cuando la persona presiona "Ver reseñas" y el resultado queda en caché.
     *
     * @param token {@code immersive_product_page_token} que vino con el producto en la búsqueda
     * @return calificación, cuántas opiniones hay de cada número de estrellas y algunas opiniones escritas
     */
    public ResenasProducto resenas(String token) {
        RespuestaProducto respuesta;
        try {
            respuesta = cliente.get()
                    // La clave del producto viene en base64 y casi siempre trae "+", "/" o "=": si no se codifican,
                    // SerpApi recibe otra clave y responde que no hay resultados.
                    .uri(uri -> uri.path("/search.json")
                            .queryParam("engine", "google_immersive_product")
                            .queryParam("page_token", "{token}")
                            .queryParam("api_key", "{clave}")
                            .build(Map.of("token", token, "clave", apiKey)))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(RespuestaProducto.class);
        } catch (RestClientResponseException e) {
            throw errorHttp(e);
        } catch (RestClientException e) {
            // Igual que en la búsqueda: la causa trae la URL con la clave, así que no se encadena.
            throw new MarketplaceException(CODIGO, "No se pudo consultar SerpApi: " + e.getClass().getSimpleName());
        }
        if (respuesta == null || respuesta.producto() == null) {
            if (respuesta != null && respuesta.error() != null
                    && !respuesta.error().toLowerCase(Locale.ROOT).contains("hasn't returned any results")) {
                throw new MarketplaceException(CODIGO, "SerpApi respondió: " + respuesta.error());
            }
            return new ResenasProducto(null, null, List.of(), List.of());
        }
        Producto producto = respuesta.producto();
        List<ResenasProducto.Estrellas> distribucion = new ArrayList<>();
        if (producto.estrellas() != null) {
            producto.estrellas().stream()
                    .filter(e -> e != null && e.estrellas() != null && e.estrellas() >= 1 && e.estrellas() <= 5
                            && e.cantidad() != null && e.cantidad() >= 0)
                    .sorted((a, b) -> b.estrellas() - a.estrellas())
                    .forEach(e -> distribucion.add(new ResenasProducto.Estrellas(e.estrellas(), e.cantidad())));
        }
        List<ResenasProducto.Resena> resenas = new ArrayList<>();
        if (producto.opiniones() != null) {
            for (Opinion opinion : producto.opiniones()) {
                if (opinion == null || !tieneValor(opinion.texto())) {
                    continue;
                }
                resenas.add(new ResenasProducto.Resena(recortarONulo(opinion.titulo(), 150),
                        recortar(opinion.texto().strip(), 800), recortarONulo(opinion.autor(), 80),
                        recortarONulo(opinion.fuente(), 80), calificacion(opinion.calificacion()),
                        recortarONulo(opinion.fecha(), 40)));
                if (resenas.size() == MAXIMO_RESENAS) {
                    break;
                }
            }
        }
        Integer total = producto.totalResenas() != null && producto.totalResenas() > 0 ? producto.totalResenas() : null;
        return new ResenasProducto(calificacion(producto.calificacion()), total, distribucion, resenas);
    }

    private static String recortarONulo(String texto, int maximo) {
        return tieneValor(texto) ? recortar(texto.strip(), maximo) : null;
    }

    /** Con {@code gl=co} los precios llegan en pesos; si el texto dice US$ o USD, vienen en dólares. */
    static String moneda(String precioTexto) {
        if (precioTexto != null) {
            String texto = precioTexto.toUpperCase(Locale.ROOT);
            if (texto.contains("US$") || texto.contains("USD")) {
                return "USD";
            }
        }
        return "COP";
    }

    /** "Envío gratis" o "Free delivery" es 0; si no lo dice, no se sabe y queda sin valor. */
    static BigDecimal costoEnvio(String envio) {
        if (envio == null) {
            return null;
        }
        String texto = envio.toLowerCase(Locale.ROOT);
        return texto.contains("gratis") || texto.contains("free") ? BigDecimal.ZERO : null;
    }

    private static BigDecimal calificacion(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0 || valor.compareTo(BigDecimal.valueOf(5)) > 0) {
            return null;
        }
        return valor.setScale(1, RoundingMode.HALF_UP);
    }

    /** Solo direcciones https válidas, de máximo 2048 letras. */
    /**
     * Búsqueda del producto en Google Shopping Colombia, para cuando SerpApi no trae ningún enlace.
     *
     * @param titulo nombre del producto
     * @param tienda tienda que lo vende, o {@code null}
     * @return enlace https
     */
    static String enlaceBusqueda(String titulo, String tienda) {
        String texto = tieneValor(tienda) ? titulo.strip() + " " + tienda.strip() : titulo.strip();
        return BUSQUEDA_GOOGLE + URLEncoder.encode(recortar(texto, 300), StandardCharsets.UTF_8);
    }

    static boolean esSeguro(String url) {
        if (url == null || url.length() > 2048 || !url.startsWith("https://")) {
            return false;
        }
        try {
            String servidor = java.net.URI.create(url).getHost();
            return servidor != null && servidor.contains(".");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Las imágenes que llegan por http se piden por https; las que no son web se descartan. */
    static String imagenSegura(String url) {
        if (url != null && url.startsWith("http://")) {
            url = "https://" + url.substring("http://".length());
        }
        return esSeguro(url) ? url : null;
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    private static MarketplaceException errorHttp(RestClientResponseException e) {
        int codigo = e.getStatusCode().value();
        if (codigo == 401) {
            return new MarketplaceException(CODIGO, "SerpApi rechazó la clave (401). Revise SERPAPI_API_KEY.", e);
        }
        if (codigo == 429) {
            return new MarketplaceException(CODIGO,
                    "Se acabaron las búsquedas del plan de SerpApi o se superó el límite por hora (429).", e);
        }
        return new MarketplaceException(CODIGO, "SerpApi respondió con error " + codigo, e);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(@JsonProperty("shopping_results") List<Resultado> resultados,
                     @JsonProperty("categorized_shopping_results") List<Categoria> porCategoria,
                     @JsonProperty("inline_shopping_results") List<Resultado> destacados,
                     @JsonProperty("error") String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Categoria(@JsonProperty("title") String titulo,
                     @JsonProperty("shopping_results") List<Resultado> resultados) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultado(@JsonProperty("title") String titulo,
                     @JsonProperty("product_id") String productId,
                     @JsonProperty("product_link") String productLink,
                     @JsonProperty("link") String link,
                     @JsonProperty("source") String tienda,
                     @JsonProperty("price") String precioTexto,
                     @JsonProperty("extracted_price") BigDecimal precio,
                     @JsonProperty("rating") BigDecimal calificacion,
                     @JsonProperty("reviews") Integer resenas,
                     @JsonProperty("thumbnail") String imagen,
                     @JsonProperty("delivery") String envio,
                     @JsonProperty("immersive_product_page_token") String tokenResenas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaProducto(@JsonProperty("product_results") Producto producto,
                             @JsonProperty("error") String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Producto(@JsonProperty("rating") BigDecimal calificacion,
                    @JsonProperty("reviews") Integer totalResenas,
                    @JsonProperty("ratings") List<Estrella> estrellas,
                    @JsonProperty("user_reviews") List<Opinion> opiniones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Estrella(@JsonProperty("stars") Integer estrellas, @JsonProperty("amount") Integer cantidad) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Opinion(@JsonProperty("title") String titulo,
                   @JsonProperty("text") String texto,
                   @JsonProperty("user_name") String autor,
                   @JsonProperty("source") String fuente,
                   @JsonProperty("rating") BigDecimal calificacion,
                   @JsonProperty("date") String fecha) {
    }
}
