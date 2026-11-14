package co.edu.unicesar.marketradar.adapter.alibaba;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClienteAlibabaApiShTest {

    private static final String URL = "http://localhost/apish";
    private static final String CLAVE = "clave-apish-de-prueba";

    private RestClient.Builder builder;
    private MockRestServiceServer servidor;

    @BeforeEach
    void preparar() {
        builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void mandaLaClaveEnXApiKeyYLeeLaBusqueda() {
        servidor.expect(once(), requestTo(URL + "/alibaba/v1/search?q=cotton%20socks&page=2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", CLAVE))
                .andRespond(withSuccess(LectorRespuestasAlibabaTest.BUSQUEDA, MediaType.APPLICATION_JSON));
        LectorRespuestasAlibaba.Pagina pagina = new ClienteAlibabaApiSh(builder.build(), CLAVE).buscar("cotton socks", 2);
        servidor.verify();
        assertEquals(1, pagina.ofertas().size());
    }

    @Test
    void sinClaveNoConsulta() {
        ClienteAlibabaApiSh cliente = new ClienteAlibabaApiSh(builder.build(), "  ");
        assertFalse(cliente.estaConfigurado());
        assertEquals(ApiShException.Tipo.CLAVE_INVALIDA,
                assertThrows(ApiShException.class, () -> cliente.buscar("x", 1)).getTipo());
        servidor.verify();
    }

    @Test
    void traduceLosErroresDocumentados() {
        assertEquals(ApiShException.Tipo.PARAMETRO_INVALIDO, ClienteAlibabaApiSh.traducir(400, null).getTipo());
        assertEquals(ApiShException.Tipo.CLAVE_INVALIDA, ClienteAlibabaApiSh.traducir(401, null).getTipo());
        assertEquals(ApiShException.Tipo.SIN_CREDITOS, ClienteAlibabaApiSh.traducir(402, null).getTipo());
        assertEquals(ApiShException.Tipo.CLAVE_INVALIDA, ClienteAlibabaApiSh.traducir(403, null).getTipo());
        assertEquals(ApiShException.Tipo.NO_ENCONTRADO, ClienteAlibabaApiSh.traducir(404, null).getTipo());
        assertEquals(ApiShException.Tipo.NO_DISPONIBLE, ClienteAlibabaApiSh.traducir(502, null).getTipo());
        assertEquals(ApiShException.Tipo.NO_DISPONIBLE, ClienteAlibabaApiSh.traducir(503, null).getTipo());
    }

    @Test
    void con429RespetaRetryAfterYElMensajeNoLlevaLaClave() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.add("retry-after", "30");
        servidor.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/alibaba/v1/product")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(cabeceras));
        ApiShException e = assertThrows(ApiShException.class,
                () -> new ClienteAlibabaApiSh(builder.build(), CLAVE).producto("1600000000001"));
        assertEquals(ApiShException.Tipo.LIMITE, e.getTipo());
        assertEquals(30L, e.getReintentarEnSegundos());
        assertFalse(e.getMessage().contains(CLAVE));
    }

    @Test
    void sinRespuestaEsNoDisponible() {
        servidor.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/alibaba/v1/search")))
                .andRespond(withException(new SocketTimeoutException("lento")));
        assertEquals(ApiShException.Tipo.NO_DISPONIBLE, assertThrows(ApiShException.class,
                () -> new ClienteAlibabaApiSh(builder.build(), CLAVE).buscar("x", 1)).getTipo());
    }

    @Test
    void unJsonRotoEsRespuestaInvalida() {
        servidor.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/alibaba/v1/search")))
                .andRespond(withSuccess("<html>", MediaType.TEXT_HTML));
        assertEquals(ApiShException.Tipo.RESPUESTA_INVALIDA, assertThrows(ApiShException.class,
                () -> new ClienteAlibabaApiSh(builder.build(), CLAVE).buscar("x", 1)).getTipo());
    }
}
