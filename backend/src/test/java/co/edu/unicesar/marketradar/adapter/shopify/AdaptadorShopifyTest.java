package co.edu.unicesar.marketradar.adapter.shopify;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AdaptadorShopifyTest {

    private static final String URL = "http://localhost/shopify";
    private static final String PERFIL = "https://agente.test/perfil.json";

    private static final String RESPUESTA = """
            {"jsonrpc":"2.0","id":1,"result":{"structuredContent":{
              "ucp":{"version":"2026-08-25"},
              "products":[
                {"id":"gid://shopify/p/111","title":"Olla a presión 6 L","url":"https://tienda.test/p/111",
                 "price_range":{"min":{"amount":8900,"currency":"USD"},"max":{"amount":9900,"currency":"USD"}},
                 "media":[{"type":"image","url":"https://cdn.test/111.jpg","alt_text":"olla"}],
                 "rating":{"value":4.0,"scale_max":5,"count":37},
                 "variants":[{"id":"gid://shopify/v/1","checkout_url":"https://tienda.test/checkout/1",
                              "condition":["new"],"seller":{"name":"Tienda Test"}}]},
                {"id":"gid://shopify/p/222","title":"Olla usada","url":"https://tienda.test/p/222",
                 "price_range":{"min":{"amount":5000,"currency":"USD"}},
                 "variants":[{"id":"gid://shopify/v/2","condition":["secondhand"]}]},
                {"id":"gid://shopify/p/333","title":"Sin precio"}
              ]}}}""";

    private RestClient.Builder builder;
    private MockRestServiceServer servidor;

    @BeforeEach
    void preparar() {
        builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void convierteLaBusquedaEnOfertas() {
        servidor.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-prueba"))
                .andExpect(jsonPath("$.method").value("tools/call"))
                .andExpect(jsonPath("$.params.name").value("search_catalog"))
                .andExpect(jsonPath("$.params.arguments.catalog.query").value("olla a presion"))
                .andExpect(jsonPath("$.params.arguments.meta.ucp-agent.profile").value(PERFIL))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        List<OfertaNormalizada> ofertas = crearAdaptador(true).consultarApi(consulta("olla a presion"));

        servidor.verify();
        assertEquals(2, ofertas.size());
        OfertaNormalizada nueva = ofertas.get(0);
        assertEquals("shopify", nueva.getCodigoMarketplace());
        assertEquals("gid://shopify/v/1", nueva.getIdExterno());
        assertEquals("Olla a presión 6 L", nueva.getTitulo());
        assertEquals(new BigDecimal("89.00"), nueva.getPrecio());
        assertEquals("USD", nueva.getMoneda());
        assertEquals("https://tienda.test/checkout/1", nueva.getUrlDestino());
        assertEquals("https://cdn.test/111.jpg", nueva.getUrlImagen());
        assertEquals("nuevo", nueva.getCondicion());
        assertEquals("Tienda Test", nueva.getVendedor());
        assertEquals(new BigDecimal("4.0"), nueva.getCalificacion());
        assertEquals(37, nueva.getTotalResenas());

        OfertaNormalizada usada = ofertas.get(1);
        assertEquals(new BigDecimal("50.00"), usada.getPrecio());
        assertEquals("https://tienda.test/p/222", usada.getUrlDestino());
        assertEquals("usado", usada.getCondicion());
        assertNull(usada.getVendedor());
        assertFalse(usada.tieneResenas());
    }

    @Test
    void sinProductosDevuelveListaVacia() {
        servidor.expect(once(), requestTo(URL))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"structuredContent\":{\"products\":[]}}}",
                        MediaType.APPLICATION_JSON));

        assertTrue(crearAdaptador(true).consultarApi(consulta("nada")).isEmpty());
    }

    @Test
    void unErrorJsonRpcSeInformaComoErrorDelMarketplace() {
        servidor.expect(once(), requestTo(URL))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"message\":\"perfil inválido\"}}",
                        MediaType.APPLICATION_JSON));

        MarketplaceException error = assertThrows(MarketplaceException.class,
                () -> crearAdaptador(true).consultarApi(consulta("lupa")));
        assertTrue(error.getMessage().contains("perfil inválido"));
    }

    @Test
    void unaRespuesta401SeInformaComoErrorDelMarketplace() {
        servidor.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        MarketplaceException error = assertThrows(MarketplaceException.class,
                () -> crearAdaptador(true).consultarApi(consulta("lupa")));
        assertTrue(error.getMessage().contains("401"));
    }

    @Test
    void unaRespuesta429SeInformaComoLimiteDeConsultas() {
        servidor.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        MarketplaceException error = assertThrows(MarketplaceException.class,
                () -> crearAdaptador(true).consultarApi(consulta("lupa")));
        assertTrue(error.getMessage().contains("429"));
    }

    @Test
    void sinTokenNoEstaHabilitado() {
        assertTrue(crearAdaptador(true).estaHabilitado());
        assertFalse(crearAdaptador(false).estaHabilitado());
    }

    @Test
    void traduceLaCondicion() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertEquals("nuevo", AdaptadorShopify.traducirCondicion(json.readTree("[\"new\"]")));
        assertEquals("usado", AdaptadorShopify.traducirCondicion(json.readTree("[\"secondhand\"]")));
        assertEquals("reacondicionado", AdaptadorShopify.traducirCondicion(json.readTree("\"refurbished\"")));
        assertNull(AdaptadorShopify.traducirCondicion(json.readTree("[]")));
        assertNull(AdaptadorShopify.traducirCondicion(null));
    }

    @Test
    void conviertePreciosDeUnidadesMenores() {
        assertEquals(new BigDecimal("89.00"), AdaptadorShopify.aUnidadesMayores(new BigDecimal("8900")));
        assertEquals(new BigDecimal("0.99"), AdaptadorShopify.aUnidadesMayores(new BigDecimal("99")));
    }

    private AdaptadorShopify crearAdaptador(boolean conToken) {
        PropiedadesMarketradar base = PropiedadesPrueba.crear();
        PropiedadesMarketradar.Shopify shopify = new PropiedadesMarketradar.Shopify(true, conToken ? "token-prueba" : "");
        PropiedadesMarketradar propiedades = new PropiedadesMarketradar(base.modoDesarrollo(), base.busqueda(),
                base.cors(), base.moneda(),
                new PropiedadesMarketradar.Marketplaces(base.marketplaces().aliexpress(), shopify,
                        base.marketplaces().mercadolibre()),
                base.asistente(), base.administrador(), base.mvp());
        return new AdaptadorShopify(propiedades, null, builder.build(), PERFIL);
    }

    private static ConsultaBusqueda consulta(String termino) {
        return ConsultaBusqueda.crear(termino, null, Set.of(), 1, 10);
    }
}
