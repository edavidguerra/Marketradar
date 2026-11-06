package co.edu.unicesar.marketradar.adapter.aliexpress;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AdaptadorAliExpressTest {

    /** Firma calculada aparte (Python, hmac + sha256) para los parámetros de la búsqueda "olla arrocera". */
    private static final String FIRMA_ESPERADA = "A96B14C72818A0337D2FC44B8965346FFD570635B9FA997EFD14E54A63988E6C";

    private static final String BUSQUEDA = """
            {"aliexpress_affiliate_product_query_response":{"resp_result":{"resp_code":200,"resp_msg":"Call succeeds",
             "result":{"current_page_no":1,"current_record_count":3,"total_record_count":3,"products":{"product":[
               {"product_id":1005006123456789,"product_title":"Olla arrocera eléctrica 1.8 L",
                "target_sale_price":"24.99","target_sale_price_currency":"USD",
                "product_main_image_url":"https://ae01.alicdn.com/kf/olla.jpg",
                "promotion_link":"https://s.click.aliexpress.com/e/_olla","evaluate_rate":"96.5%"},
               {"product_id":1005006999999999,"product_title":"Olla sin enlace de afiliado",
                "target_sale_price":"10.00","target_sale_price_currency":"USD",
                "product_detail_url":"https://www.aliexpress.com/item/1005006999999999.html"},
               {"product_id":1005000000000001,"product_title":"Producto sin precio"}
             ]}}}},"request_id":"abc123"}""";

    private RestClient.Builder builder;
    private MockRestServiceServer servidor;

    @BeforeEach
    void preparar() {
        builder = RestClient.builder().baseUrl("http://localhost/ae");
        servidor = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void convierteLaBusquedaEnOfertas() {
        servidor.expect(once(), requestTo("http://localhost/ae/sync"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "method", "aliexpress.affiliate.product.query",
                        "app_key", "12345",
                        "sign_method", "sha256",
                        "timestamp", "1793361600000",
                        "keywords", "olla arrocera",
                        "tracking_id", "marketradar",
                        "target_currency", "USD",
                        "ship_to_country", "CO",
                        "sign", FIRMA_ESPERADA)))
                .andRespond(withSuccess(BUSQUEDA, MediaType.APPLICATION_JSON));

        List<OfertaNormalizada> ofertas = crearAdaptador(true).consultarApi(consulta("olla arrocera"));

        servidor.verify();
        assertEquals(2, ofertas.size());
        OfertaNormalizada olla = ofertas.get(0);
        assertEquals("aliexpress", olla.getCodigoMarketplace());
        assertEquals("1005006123456789", olla.getIdExterno());
        assertEquals("Olla arrocera eléctrica 1.8 L", olla.getTitulo());
        assertEquals(new BigDecimal("24.99"), olla.getPrecio());
        assertEquals("USD", olla.getMoneda());
        assertEquals("https://s.click.aliexpress.com/e/_olla", olla.getUrlDestino());
        assertEquals("https://ae01.alicdn.com/kf/olla.jpg", olla.getUrlImagen());
        assertEquals("nuevo", olla.getCondicion());
        assertEquals(new BigDecimal("4.8"), olla.getCalificacion());
        assertNull(olla.getTotalResenas());
        assertNull(olla.getCostoEnvio());

        OfertaNormalizada sinAfiliado = ofertas.get(1);
        assertEquals("https://www.aliexpress.com/item/1005006999999999.html", sinAfiliado.getUrlDestino());
        assertNull(sinAfiliado.getCalificacion());
    }

    @Test
    void sinResultadosDevuelveListaVacia() {
        servidor.expect(once(), requestTo("http://localhost/ae/sync"))
                .andRespond(withSuccess("""
                        {"aliexpress_affiliate_product_query_response":{"resp_result":
                         {"resp_code":405,"resp_msg":"No results"}}}""", MediaType.APPLICATION_JSON));

        assertTrue(crearAdaptador(true).consultarApi(consulta("xyzxyz")).isEmpty());
    }

    @Test
    void errorDeFirmaLanzaExcepcion() {
        servidor.expect(once(), requestTo("http://localhost/ae/sync"))
                .andRespond(withSuccess("""
                        {"type":"ISV","code":"IncompleteSignature",
                         "message":"The request signature does not conform to platform standards",
                         "request_id":"xyz"}""", MediaType.APPLICATION_JSON));

        MarketplaceException error = assertThrows(MarketplaceException.class,
                () -> crearAdaptador(true).consultarApi(consulta("lupa")));
        assertTrue(error.getMessage().contains("IncompleteSignature"));
    }

    @Test
    void errorHttpLanzaExcepcion() {
        servidor.expect(once(), requestTo("http://localhost/ae/sync"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        MarketplaceException error = assertThrows(MarketplaceException.class,
                () -> crearAdaptador(true).consultarApi(consulta("lupa")));
        assertTrue(error.getMessage().contains("503"));
    }

    @Test
    void sinTrackingIdNoEstaHabilitado() {
        assertTrue(crearAdaptador(true).estaHabilitado());
        assertFalse(crearAdaptador(false).estaHabilitado());
    }

    @Test
    void firmaComoLaDocumentacion() {
        // Ignora el parámetro sign y ordena por nombre, sin importar el orden de entrada.
        String firma = AdaptadorAliExpress.firmar(Map.of("method", "x", "app_key", "12345", "sign", "viejo"), "secreto");
        assertEquals("E86E87096AB0BA1EE427D62B4A1F5B7AD46CEB315FD432B76BCE3EDA173BAB89", firma);
    }

    @Test
    void pasaElPorcentajeAEstrellas() {
        assertEquals(new BigDecimal("5.0"), AdaptadorAliExpress.porcentajeAEstrellas("100%"));
        assertEquals(new BigDecimal("4.8"), AdaptadorAliExpress.porcentajeAEstrellas("96.5%"));
        assertNull(AdaptadorAliExpress.porcentajeAEstrellas("sin dato"));
        assertNull(AdaptadorAliExpress.porcentajeAEstrellas("120%"));
        assertNull(AdaptadorAliExpress.porcentajeAEstrellas(null));
    }

    private AdaptadorAliExpress crearAdaptador(boolean conTrackingId) {
        PropiedadesMarketradar base = PropiedadesPrueba.crear();
        PropiedadesMarketradar.AliExpress aliExpress = new PropiedadesMarketradar.AliExpress(true, "12345",
                "secreto", conTrackingId ? "marketradar" : "");
        PropiedadesMarketradar propiedades = new PropiedadesMarketradar(base.modoDesarrollo(), base.busqueda(),
                base.cors(), base.moneda(),
                new PropiedadesMarketradar.Marketplaces(aliExpress,
                        base.marketplaces().shopify(), base.marketplaces().mercadolibre()),
                base.asistente(), base.administrador(), base.mvp());
        Clock reloj = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);
        return new AdaptadorAliExpress(propiedades, null, builder.build(), reloj);
    }

    private static ConsultaBusqueda consulta(String termino) {
        return ConsultaBusqueda.crear(termino, null, Set.of(), 1, 10);
    }
}
