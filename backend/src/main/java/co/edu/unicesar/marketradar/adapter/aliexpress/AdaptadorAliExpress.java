package co.edu.unicesar.marketradar.adapter.aliexpress;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplaceBase;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Integración con la API de afiliados de AliExpress Open Platform.
 *
 * <p>Busca con el método {@code aliexpress.affiliate.product.query} en la puerta de enlace
 * {@code https://api-sg.aliexpress.com/sync}. Cada petición va firmada con HMAC-SHA256 usando el App
 * Secret (ver {@link #firmar(Map, String)}). Los precios se piden en dólares y la conversión a pesos la
 * hace {@link ConversorMoneda}. La API no informa el costo de envío ni el número de reseñas; solo el
 * porcentaje de opiniones positivas, que se pasa a estrellas.</p>
 */
@Component
public class AdaptadorAliExpress extends AdaptadorMarketplaceBase {

    public static final String CODIGO = "aliexpress";

    /** Dirección de la puerta de enlace de AliExpress Open Platform. */
    static final String URL_API = "https://api-sg.aliexpress.com";

    static final String METODO = "aliexpress.affiliate.product.query";

    /** Resultados que se piden por búsqueda; la API acepta hasta 50. */
    static final int LIMITE_RESULTADOS = 50;

    private static final String NODO_RESPUESTA = "aliexpress_affiliate_product_query_response";
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal VEINTE = new BigDecimal("20");

    private final PropiedadesMarketradar.AliExpress configuracion;
    private final RestClient cliente;
    private final Clock reloj;

    /**
     * Constructor que usa Spring: crea el cliente HTTP con tiempos de espera cortos.
     *
     * @param propiedades configuración de Marketradar
     * @param conversor conversor de moneda
     * @param builder constructor de clientes HTTP de Spring
     */
    @Autowired
    public AdaptadorAliExpress(PropiedadesMarketradar propiedades, ConversorMoneda conversor,
                               RestClient.Builder builder) {
        this(propiedades, conversor, builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(Duration.ofSeconds(3))
                                .withReadTimeout(Duration.ofSeconds(5))))
                .baseUrl(URL_API)
                .build(), Clock.systemUTC());
    }

    /**
     * Constructor para las pruebas, con el cliente HTTP y el reloj ya creados.
     *
     * @param propiedades configuración de Marketradar
     * @param conversor conversor de moneda
     * @param cliente cliente HTTP con la dirección base de la API
     * @param reloj reloj para la marca de tiempo de la firma
     */
    AdaptadorAliExpress(PropiedadesMarketradar propiedades, ConversorMoneda conversor, RestClient cliente, Clock reloj) {
        super(CODIGO, conversor, 5);
        this.configuracion = propiedades.marketplaces().aliexpress();
        this.cliente = cliente;
        this.reloj = reloj;
    }

    @Override
    public boolean estaHabilitado() {
        return configuracion.habilitado()
                && tieneValor(configuracion.appKey())
                && tieneValor(configuracion.appSecret())
                && tieneValor(configuracion.trackingId());
    }

    @Override
    protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
        JsonNode resultado = leerResultado(buscar(consulta.getTermino()));
        List<OfertaNormalizada> ofertas = new ArrayList<>();
        for (JsonNode producto : resultado.path("products").path("product")) {
            OfertaNormalizada oferta = aOferta(producto);
            if (oferta != null) {
                ofertas.add(oferta);
            }
        }
        return ofertas;
    }

    private JsonNode buscar(String termino) {
        Map<String, String> parametros = new TreeMap<>();
        parametros.put("app_key", configuracion.appKey());
        parametros.put("method", METODO);
        parametros.put("sign_method", "sha256");
        parametros.put("timestamp", String.valueOf(reloj.millis()));
        parametros.put("keywords", termino);
        parametros.put("tracking_id", configuracion.trackingId());
        parametros.put("target_currency", "USD");
        parametros.put("target_language", "ES");
        parametros.put("ship_to_country", "CO");
        parametros.put("page_no", "1");
        parametros.put("page_size", String.valueOf(LIMITE_RESULTADOS));
        parametros.put("sign", firmar(parametros, configuracion.appSecret()));

        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        parametros.forEach(formulario::add);
        try {
            return cliente.post()
                    .uri("/sync")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new MarketplaceException(CODIGO, "AliExpress respondió con error " + e.getStatusCode().value(), e);
        }
    }

    /**
     * Revisa la respuesta y devuelve el nodo {@code result}. Los errores de la puerta de enlace (firma,
     * App Key, límite de llamadas) llegan con estado 200 y un {@code code} distinto de {@code "0"}.
     */
    private static JsonNode leerResultado(JsonNode respuesta) {
        if (respuesta == null) {
            throw new MarketplaceException(CODIGO, "AliExpress respondió vacío");
        }
        if (respuesta.has("error_response")) {
            JsonNode error = respuesta.path("error_response");
            throw new MarketplaceException(CODIGO, "AliExpress rechazó la consulta: "
                    + error.path("code").asText() + " " + error.path("msg").asText());
        }
        String codigo = respuesta.path("code").asText("0");
        if (!"0".equals(codigo)) {
            throw new MarketplaceException(CODIGO, "AliExpress rechazó la consulta: " + codigo + " "
                    + respuesta.path("message").asText());
        }
        JsonNode resultado = respuesta.path(NODO_RESPUESTA).path("resp_result");
        int codigoResultado = resultado.path("resp_code").asInt(200);
        // 405 significa que la búsqueda no encontró productos: no es un error.
        if (codigoResultado != 200 && codigoResultado != 405) {
            throw new MarketplaceException(CODIGO, "AliExpress respondió con código " + codigoResultado + " "
                    + resultado.path("resp_msg").asText());
        }
        return resultado.path("result");
    }

    /**
     * Convierte un producto de la API en oferta. Devuelve {@code null} si le falta un dato obligatorio.
     */
    private static OfertaNormalizada aOferta(JsonNode producto) {
        String id = texto(producto, "product_id");
        String titulo = texto(producto, "product_title");
        BigDecimal precio = decimal(texto(producto, "target_sale_price"));
        String moneda = texto(producto, "target_sale_price_currency");
        String enlace = texto(producto, "promotion_link");
        if (enlace == null) {
            enlace = texto(producto, "product_detail_url");
        }
        if (id == null || titulo == null || precio == null || enlace == null) {
            return null;
        }
        OfertaNormalizada.Builder oferta = OfertaNormalizada.builder()
                .codigoMarketplace(CODIGO)
                .idExterno(id)
                .titulo(titulo)
                .precio(precio)
                .moneda(moneda == null ? "USD" : moneda)
                .urlDestino(enlace)
                .urlImagen(texto(producto, "product_main_image_url"))
                .condicion("nuevo")
                .calificacion(porcentajeAEstrellas(texto(producto, "evaluate_rate")));
        return oferta.build();
    }

    /**
     * Pasa el porcentaje de opiniones positivas a estrellas: 100 % son 5 estrellas.
     *
     * @param porcentaje texto como {@code "96.5%"}
     * @return estrellas con un decimal, o {@code null} si el texto no es un porcentaje válido
     */
    static BigDecimal porcentajeAEstrellas(String porcentaje) {
        if (porcentaje == null) {
            return null;
        }
        BigDecimal valor = decimal(porcentaje.replace("%", "").trim());
        if (valor == null || valor.signum() < 0 || valor.compareTo(CIEN) > 0) {
            return null;
        }
        return valor.divide(VEINTE, 1, RoundingMode.HALF_UP);
    }

    /**
     * Firma la petición como lo pide AliExpress Open Platform: ordena los parámetros por nombre (sin
     * {@code sign}), une cada nombre con su valor sin separadores y calcula HMAC-SHA256 con el App Secret
     * como clave. El resultado va en hexadecimal y en mayúsculas.
     *
     * @param parametros parámetros de la petición
     * @param secreto App Secret
     * @return firma para el parámetro {@code sign}
     */
    static String firmar(Map<String, String> parametros, String secreto) {
        StringBuilder texto = new StringBuilder();
        new TreeMap<>(parametros).forEach((nombre, valor) -> {
            if (!"sign".equals(nombre) && valor != null) {
                texto.append(nombre).append(valor);
            }
        });
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().withUpperCase()
                    .formatHex(mac.doFinal(texto.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC-SHA256", e);
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        String texto = valor.asText().trim();
        return texto.isEmpty() ? null : texto;
    }

    private static BigDecimal decimal(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
