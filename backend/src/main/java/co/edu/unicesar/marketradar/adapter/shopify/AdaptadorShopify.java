package co.edu.unicesar.marketradar.adapter.shopify;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplaceBase;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Integración con el Global Catalog de Shopify (UCP sobre MCP).
 *
 * <p>Llama a la herramienta {@code search_catalog} con JSON-RPC 2.0 en {@code POST /api/ucp/mcp}. Los
 * resultados no se pueden guardar en caché ni reutilizar fuera del producto, según los términos de Shopify.</p>
 */
@Component
public class AdaptadorShopify extends AdaptadorMarketplaceBase {

    public static final String CODIGO = "shopify";

    static final int LIMITE_RESULTADOS = 20;

    private static final String PERFIL_POR_DEFECTO =
            "https://shopify.dev/ucp/agent-profiles/examples/2026-08-25/valid-with-capabilities.json";

    private final PropiedadesMarketradar.Shopify configuracion;
    private final RestClient cliente;
    private final String urlPerfil;

    /**
     * Constructor que usa Spring: crea el cliente HTTP con tiempos de espera cortos.
     *
     * @param propiedades configuración de Marketradar
     * @param conversor conversor de moneda
     * @param builder constructor de clientes HTTP de Spring
     * @param urlApi dirección del punto MCP del catálogo
     * @param urlPerfil dirección del perfil de agente que se envía en cada consulta
     */
    @Autowired
    public AdaptadorShopify(PropiedadesMarketradar propiedades, ConversorMoneda conversor,
                            RestClient.Builder builder,
                            @Value("${marketradar.marketplaces.shopify.url-api:https://catalog.shopify.com/api/ucp/mcp}") String urlApi,
                            @Value("${marketradar.marketplaces.shopify.perfil-agente:" + PERFIL_POR_DEFECTO + "}") String urlPerfil) {
        this(propiedades, conversor, builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(Duration.ofSeconds(3))
                                .withReadTimeout(Duration.ofSeconds(4))))
                .baseUrl(urlApi)
                .build(), urlPerfil);
    }

    /**
     * Constructor para las pruebas, con el cliente HTTP ya creado.
     *
     * @param propiedades configuración de Marketradar
     * @param conversor conversor de moneda
     * @param cliente cliente HTTP con la dirección del punto MCP
     * @param urlPerfil dirección del perfil de agente
     */
    AdaptadorShopify(PropiedadesMarketradar propiedades, ConversorMoneda conversor, RestClient cliente,
                     String urlPerfil) {
        super(CODIGO, conversor, 2);
        this.configuracion = propiedades.marketplaces().shopify();
        this.cliente = cliente;
        this.urlPerfil = urlPerfil;
    }

    @Override
    public boolean estaHabilitado() {
        return configuracion.habilitado() && tieneValor(configuracion.token());
    }

    @Override
    protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
        RespuestaRpc respuesta;
        try {
            respuesta = cliente.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + configuracion.token())
                    .body(crearSolicitud(consulta.getTermino()))
                    .retrieve()
                    .body(RespuestaRpc.class);
        } catch (RestClientResponseException e) {
            throw errorHttp(e);
        }
        if (respuesta == null) {
            return List.of();
        }
        if (respuesta.error() != null) {
            throw new MarketplaceException(CODIGO, "Shopify respondió con error: " + respuesta.error().message());
        }
        if (respuesta.result() == null || respuesta.result().structuredContent() == null
                || respuesta.result().structuredContent().products() == null) {
            return List.of();
        }
        List<OfertaNormalizada> ofertas = new ArrayList<>();
        for (Producto producto : respuesta.result().structuredContent().products()) {
            OfertaNormalizada oferta = aOferta(producto);
            if (oferta != null) {
                ofertas.add(oferta);
            }
        }
        return ofertas;
    }

    private Map<String, Object> crearSolicitud(String termino) {
        Map<String, Object> catalogo = Map.of(
                "query", termino,
                "pagination", Map.of("limit", LIMITE_RESULTADOS));
        Map<String, Object> argumentos = Map.of(
                "meta", Map.of("ucp-agent", Map.of("profile", urlPerfil)),
                "catalog", catalogo);
        return Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "tools/call",
                "params", Map.of("name", "search_catalog", "arguments", argumentos));
    }

    private static MarketplaceException errorHttp(RestClientResponseException e) {
        int codigo = e.getStatusCode().value();
        if (codigo == 401 || codigo == 403) {
            return new MarketplaceException(CODIGO,
                    "Shopify rechazó las credenciales (" + codigo + "). Revise el token y el perfil de agente.", e);
        }
        if (codigo == 429) {
            return new MarketplaceException(CODIGO, "Shopify limitó las consultas (429). Intente más tarde.", e);
        }
        return new MarketplaceException(CODIGO, "Shopify respondió con error " + codigo, e);
    }

    private static OfertaNormalizada aOferta(Producto producto) {
        Precio minimo = producto.priceRange() == null ? null : producto.priceRange().min();
        Variante variante = primeraVariante(producto);
        String urlDestino = variante != null && variante.checkoutUrl() != null ? variante.checkoutUrl() : producto.url();
        if (producto.id() == null || producto.title() == null || minimo == null || minimo.amount() == null
                || minimo.currency() == null || urlDestino == null) {
            return null;
        }
        Vendedor vendedor = variante != null && variante.seller() != null ? variante.seller() : producto.seller();
        OfertaNormalizada.Builder oferta = OfertaNormalizada.builder()
                .codigoMarketplace(CODIGO)
                .idExterno(variante != null && variante.id() != null ? variante.id() : producto.id())
                .titulo(producto.title())
                .precio(aUnidadesMayores(minimo.amount()))
                .moneda(minimo.currency())
                .urlDestino(urlDestino)
                .urlImagen(primeraImagen(producto.media()))
                .condicion(traducirCondicion(variante == null ? null : variante.condition()))
                .vendedor(vendedor == null ? null : vendedor.name());
        Calificacion calificacion = producto.rating();
        if (calificacion != null && calificacion.value() != null && calificacion.count() != null
                && calificacion.count() > 0) {
            BigDecimal escala = calificacion.scaleMax() == null || calificacion.scaleMax().signum() <= 0
                    ? BigDecimal.valueOf(5) : calificacion.scaleMax();
            oferta.calificacion(calificacion.value().multiply(BigDecimal.valueOf(5))
                            .divide(escala, 1, RoundingMode.HALF_UP))
                    .totalResenas(calificacion.count());
        }
        return oferta.build();
    }

    private static Variante primeraVariante(Producto producto) {
        return producto.variants() == null || producto.variants().isEmpty() ? null : producto.variants().get(0);
    }

    private static String primeraImagen(List<Medio> medios) {
        if (medios == null) {
            return null;
        }
        for (Medio medio : medios) {
            if (medio.url() != null && (medio.type() == null || "image".equalsIgnoreCase(medio.type()))) {
                return medio.url();
            }
        }
        return null;
    }

    static BigDecimal aUnidadesMayores(BigDecimal unidadesMenores) {
        return unidadesMenores.movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Traduce la condición que informa Shopify a las palabras que muestra Marketradar. Shopify la entrega
     * como lista de etiquetas ({@code new}, {@code secondhand}...) o como texto.
     *
     * @param condicion nodo JSON de la condición
     * @return condición en español, o {@code null} si no viene
     */
    static String traducirCondicion(JsonNode condicion) {
        if (condicion == null || condicion.isNull()) {
            return null;
        }
        String valor = condicion.isArray() ? (condicion.isEmpty() ? null : condicion.get(0).asText()) : condicion.asText();
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return switch (valor.toLowerCase()) {
            case "new" -> "nuevo";
            case "secondhand", "used" -> "usado";
            case "refurbished" -> "reacondicionado";
            default -> valor;
        };
    }

    /**
     * Respuesta JSON-RPC de {@code tools/call}.
     *
     * @param result resultado de la herramienta
     * @param error error de JSON-RPC, si lo hubo
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaRpc(Resultado result, ErrorRpc error) {
    }

    /** @param structuredContent contenido estructurado de la herramienta */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultado(StructuredContent structuredContent) {
    }

    /** @param message descripción del error */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorRpc(String message) {
    }

    /** @param products productos encontrados */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record StructuredContent(List<Producto> products) {
    }

    /**
     * Producto del catálogo.
     *
     * @param id identificador global del producto
     * @param title título
     * @param url enlace al producto
     * @param priceRange rango de precios
     * @param media imágenes y otros medios
     * @param variants variantes con enlace de compra
     * @param seller vendedor a nivel de producto, si viene
     * @param rating calificación, si viene
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Producto(String id, String title, String url,
                    @JsonProperty("price_range") RangoPrecio priceRange,
                    List<Medio> media, List<Variante> variants, Vendedor seller, Calificacion rating) {
    }

    /** @param min precio mínimo */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RangoPrecio(Precio min) {
    }

    /**
     * @param amount monto en unidades menores, por ejemplo centavos
     * @param currency código de moneda
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Precio(BigDecimal amount, String currency) {
    }

    /**
     * @param type tipo de medio, por ejemplo {@code image}
     * @param url dirección del medio
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Medio(String type, String url) {
    }

    /**
     * @param id identificador de la variante
     * @param checkoutUrl enlace de compra
     * @param condition condición del artículo
     * @param seller vendedor
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Variante(String id, @JsonProperty("checkout_url") String checkoutUrl, JsonNode condition,
                    Vendedor seller) {
    }

    /** @param name nombre del comercio */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Vendedor(String name) {
    }

    /**
     * @param value valor de la calificación
     * @param scaleMax máximo de la escala
     * @param count cantidad de calificaciones
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Calificacion(BigDecimal value, @JsonProperty("scale_max") BigDecimal scaleMax, Integer count) {
    }
}
