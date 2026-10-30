package co.edu.unicesar.marketradar.adapter.mercadolibre;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AdaptadorMercadoLibreTest {

    private static final String TOKEN = """
            {"access_token":"APP_USR-acceso-1","token_type":"Bearer","expires_in":21600,
             "scope":"offline_access read","refresh_token":"TG-nuevo"}""";

    private static final String BUSQUEDA = """
            {"site_id":"MCO","query":"olla arrocera","paging":{"total":2,"offset":0,"limit":50},
             "results":[
               {"id":"MCO111","title":"Olla Arrocera Oster 1.8 L","price":159900,"currency_id":"COP",
                "condition":"new","permalink":"https://articulo.mercadolibre.com.co/MCO-111",
                "thumbnail":"http://http2.mlstatic.com/D_111-I.jpg",
                "shipping":{"free_shipping":true,"mode":"me2"},"seller":{"id":1,"nickname":"ELECTRO HOGAR"}},
               {"id":"MCO222","title":"Olla Arrocera Usada","price":80000,"currency_id":"COP",
                "condition":"used","permalink":"https://articulo.mercadolibre.com.co/MCO-222",
                "shipping":{"free_shipping":false}}
             ]}""";

    private static final String RESENAS = """
            {"paging":{},"reviews":[],"rating_average":4.76,
             "rating_levels":{"one_star":2,"two_star":1,"three_star":0,"four_star":9,"five_star":80}}""";

    @TempDir
    Path carpeta;

    private RestClient.Builder builder;
    private MockRestServiceServer servidor;
    private Path archivoToken;

    @BeforeEach
    void preparar() {
        builder = RestClient.builder().baseUrl("http://localhost/ml");
        servidor = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        archivoToken = carpeta.resolve("ml-token.txt");
    }

    @Test
    void convierteLaBusquedaEnOfertasConResenas() {
        servidor.expect(once(), requestTo("http://localhost/ml/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "refresh_token", "refresh_token", "TG-inicial", "client_id", "123")))
                .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo("http://localhost/ml/sites/MCO/search?q=olla%20arrocera&limit=50"))
                .andExpect(header("Authorization", "Bearer APP_USR-acceso-1"))
                .andRespond(withSuccess(BUSQUEDA, MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo("http://localhost/ml/reviews/item/MCO111"))
                .andRespond(withSuccess(RESENAS, MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo("http://localhost/ml/reviews/item/MCO222"))
                .andRespond(withSuccess("{\"rating_average\":0,\"rating_levels\":{}}", MediaType.APPLICATION_JSON));

        List<OfertaNormalizada> ofertas = crearAdaptador(true).consultarApi(consulta("olla arrocera"));

        servidor.verify();
        assertEquals(2, ofertas.size());
        OfertaNormalizada nueva = ofertas.get(0);
        assertEquals("mercadolibre", nueva.getCodigoMarketplace());
        assertEquals("MCO111", nueva.getIdExterno());
        assertEquals(new BigDecimal("159900"), nueva.getPrecio());
        assertEquals("COP", nueva.getMoneda());
        assertEquals(BigDecimal.ZERO, nueva.getCostoEnvio());
        assertEquals("nuevo", nueva.getCondicion());
        assertEquals("ELECTRO HOGAR", nueva.getVendedor());
        assertEquals("https://articulo.mercadolibre.com.co/MCO-111", nueva.getUrlDestino());
        assertEquals("https://http2.mlstatic.com/D_111-I.jpg", nueva.getUrlImagen());
        assertEquals(new BigDecimal("4.8"), nueva.getCalificacion());
        assertEquals(92, nueva.getTotalResenas());

        OfertaNormalizada usada = ofertas.get(1);
        assertEquals("usado", usada.getCondicion());
        assertNull(usada.getCostoEnvio(), "Sin envío gratis el costo no se conoce");
        assertFalse(usada.tieneResenas());
    }

    @Test
    void guardaElNuevoRefreshTokenYReusaElTokenDeAcceso() throws Exception {
        servidor.expect(once(), requestTo("http://localhost/ml/oauth/token"))
                .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        servidor.expect(org.springframework.test.web.client.ExpectedCount.times(2),
                        requestTo("http://localhost/ml/sites/MCO/search?q=lupa&limit=50"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        AdaptadorMercadoLibre adaptador = crearAdaptador(true);
        adaptador.consultarApi(consulta("lupa"));
        adaptador.consultarApi(consulta("lupa"));

        servidor.verify();
        assertEquals("TG-nuevo", Files.readString(archivoToken));
    }

    @Test
    void usaElRefreshTokenGuardadoEnElArchivo() throws Exception {
        Files.writeString(archivoToken, "TG-guardado\n");
        servidor.expect(once(), requestTo("http://localhost/ml/oauth/token"))
                .andExpect(content().formDataContains(java.util.Map.of("refresh_token", "TG-guardado")))
                .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo("http://localhost/ml/sites/MCO/search?q=lupa&limit=50"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        crearAdaptador(true).consultarApi(consulta("lupa"));

        servidor.verify();
    }

    @Test
    void conRespuesta401PideOtroTokenYRepiteLaBusqueda() {
        servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(once(), requestTo("http://localhost/ml/oauth/token"))
                .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo("http://localhost/ml/sites/MCO/search?q=lupa&limit=50"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        servidor.expect(once(), requestTo("http://localhost/ml/oauth/token"))
                .andRespond(withSuccess(TOKEN.replace("acceso-1", "acceso-2").replace("TG-nuevo", "TG-otro"),
                        MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo("http://localhost/ml/sites/MCO/search?q=lupa&limit=50"))
                .andExpect(header("Authorization", "Bearer APP_USR-acceso-2"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(crearAdaptador(true).consultarApi(consulta("lupa")).isEmpty());
        servidor.verify();
    }

    @Test
    void unaRespuesta403SeInformaComoErrorDelMarketplace() {
        servidor.expect(once(), requestTo("http://localhost/ml/oauth/token"))
                .andRespond(withSuccess(TOKEN, MediaType.APPLICATION_JSON));
        servidor.expect(once(), requestTo("http://localhost/ml/sites/MCO/search?q=lupa&limit=50"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        MarketplaceException error = assertThrows(MarketplaceException.class,
                () -> crearAdaptador(true).consultarApi(consulta("lupa")));
        assertTrue(error.getMessage().contains("403"));
    }

    @Test
    void unRefreshTokenRechazadoSeInformaComoErrorDelMarketplace() {
        servidor.expect(once(), requestTo("http://localhost/ml/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

        MarketplaceException error = assertThrows(MarketplaceException.class,
                () -> crearAdaptador(true).consultarApi(consulta("lupa")));
        assertTrue(error.getMessage().contains("refresh token"));
    }

    @Test
    void sinRefreshTokenNoEstaHabilitado() {
        assertTrue(crearAdaptador(true).estaHabilitado());
        assertFalse(crearAdaptador(false).estaHabilitado());
    }

    @Test
    void traduceLaCondicion() {
        assertEquals("nuevo", AdaptadorMercadoLibre.traducirCondicion("new"));
        assertEquals("usado", AdaptadorMercadoLibre.traducirCondicion("used"));
        assertEquals("reacondicionado", AdaptadorMercadoLibre.traducirCondicion("refurbished"));
        assertNull(AdaptadorMercadoLibre.traducirCondicion(null));
    }

    private AdaptadorMercadoLibre crearAdaptador(boolean conRefreshToken) {
        PropiedadesMarketradar base = PropiedadesPrueba.crear();
        PropiedadesMarketradar.MercadoLibre mercadoLibre = new PropiedadesMarketradar.MercadoLibre(true, "123",
                "secreto", conRefreshToken ? "TG-inicial" : "", archivoToken.toString(), "http://localhost/ml", "MCO", 2);
        PropiedadesMarketradar propiedades = new PropiedadesMarketradar(base.modoDesarrollo(), base.busqueda(),
                base.cors(), base.moneda(),
                new PropiedadesMarketradar.Marketplaces(base.marketplaces().aliexpress(),
                        base.marketplaces().shopify(), mercadoLibre),
                base.asistente(), base.administrador(), base.mvp());
        Clock reloj = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);
        return new AdaptadorMercadoLibre(propiedades, null, builder.build(), reloj);
    }

    private static ConsultaBusqueda consulta(String termino) {
        return ConsultaBusqueda.crear(termino, null, Set.of(), 1, 10);
    }
}
