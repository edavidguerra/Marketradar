package co.edu.unicesar.marketradar.ia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClienteGeminiTest {

    private static final String URL = "http://localhost/gemini";
    private static final String CLAVE = "clave-gemini-de-prueba";

    private RestClient.Builder builder;
    private MockRestServiceServer servidor;

    @BeforeEach
    void preparar() {
        builder = RestClient.builder().baseUrl(URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
    }

    private ClienteGemini crear(String clave) {
        return new ClienteGemini(builder.build(), true, clave, "gemini-prueba");
    }

    private static ObjectNode esquema(ClienteGemini cliente) {
        ObjectNode esquema = cliente.nuevoEsquema().put("type", "OBJECT");
        esquema.putObject("properties").putObject("consulta").put("type", "STRING");
        return esquema;
    }

    @Test
    void pideJsonConLaClaveEnLaCabeceraYSinElRazonamiento() {
        servidor.expect(once(), requestTo(URL + "/v1beta/models/gemini-prueba:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", CLAVE))
                .andExpect(content().string(containsString("\"responseMimeType\":\"application/json\"")))
                .andExpect(content().string(containsString("\"responseSchema\":{\"type\":\"OBJECT\"")))
                .andExpect(content().string(containsString("\"text\":\"un ps5 pa mi nieto\"")))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"role":"model","parts":[
                          {"text":"pensando...","thought":true},
                          {"text":"{\\"consulta\\": \\"playstation 5\\"}"}]}}]}""", MediaType.APPLICATION_JSON));

        ClienteGemini cliente = crear(CLAVE);
        Optional<JsonNode> respuesta = cliente.pedirJson("instrucciones", "un ps5 pa mi nieto", esquema(cliente));

        servidor.verify();
        assertEquals("playstation 5", respuesta.orElseThrow().path("consulta").asText());
    }

    @Test
    void siSeAcaboLaCuotaSigueSinIA() {
        servidor.expect(once(), requestTo(containsString("generateContent")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        ClienteGemini cliente = crear(CLAVE);
        assertTrue(cliente.pedirJson("i", "t", esquema(cliente)).isEmpty());
    }

    @Test
    void siNoHayConexionOElJsonVieneMalSigueSinIA() {
        servidor.expect(once(), requestTo(containsString("generateContent")))
                .andRespond(withException(new IOException("sin conexión")));
        servidor.expect(once(), requestTo(containsString("generateContent")))
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"no es json\"}]}}]}",
                        MediaType.APPLICATION_JSON));

        ClienteGemini cliente = crear(CLAVE);
        assertTrue(cliente.pedirJson("i", "t", esquema(cliente)).isEmpty());
        assertTrue(cliente.pedirJson("i", "t", esquema(cliente)).isEmpty());
    }

    @Test
    void sinClaveNoSeConsulta() {
        ClienteGemini cliente = crear("");

        assertFalse(cliente.estaHabilitado());
        assertTrue(cliente.pedirJson("i", "t", esquema(cliente)).isEmpty());
        assertFalse(new ClienteGemini(builder.build(), false, CLAVE, null).estaHabilitado());
        servidor.verify();
    }
}
