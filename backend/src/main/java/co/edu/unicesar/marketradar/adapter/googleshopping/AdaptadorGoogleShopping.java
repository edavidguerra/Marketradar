package co.edu.unicesar.marketradar.adapter.googleshopping;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplaceBase;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    static final int LIMITE_RESULTADOS = 20;

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
                    .uri(uri -> uri.path("/search.json")
                            .queryParam("engine", "google_shopping")
                            .queryParam("q", consulta.getTermino())
                            .queryParam("gl", "co")
                            .queryParam("hl", "es")
                            .queryParam("google_domain", "google.com.co")
                            .queryParam("api_key", apiKey)
                            .build())
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
        if (respuesta.resultados() == null) {
            return List.of();
        }
        List<OfertaNormalizada> ofertas = new ArrayList<>();
        for (Resultado resultado : respuesta.resultados()) {
            OfertaNormalizada oferta = aOferta(resultado);
            if (oferta != null) {
                ofertas.add(oferta);
            }
            if (ofertas.size() == LIMITE_RESULTADOS) {
                break;
            }
        }
        return ofertas;
    }

    private static OfertaNormalizada aOferta(Resultado resultado) {
        String enlace = tieneValor(resultado.productLink()) ? resultado.productLink() : resultado.link();
        if (!tieneValor(resultado.titulo()) || resultado.precio() == null || resultado.precio().signum() <= 0
                || !esWeb(enlace)) {
            return null;
        }
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
                .urlImagen(esWeb(resultado.imagen()) ? resultado.imagen() : null)
                .calificacion(calificacion(resultado.calificacion()))
                .totalResenas(resultado.resenas() != null && resultado.resenas() > 0 ? resultado.resenas() : null)
                .build();
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

    private static boolean esWeb(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://")) && url.length() <= 2048;
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
                     @JsonProperty("error") String error) {
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
                     @JsonProperty("delivery") String envio) {
    }
}
