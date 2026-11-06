package co.edu.unicesar.marketradar.asistente;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Prueba el proveedor de Claude contra un servidor falso que responde como la API de Anthropic.
 */
class ProveedorIAClaudeTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODELO = "claude-opus-5-5";

    private static final List<MensajeChat> CONVERSACION = List.of(
            new MensajeChat(RolMensaje.SISTEMA, "Instrucciones"),
            new MensajeChat(RolMensaje.ASISTENTE, "Saludo suelto que no debe ir primero"),
            new MensajeChat(RolMensaje.USUARIO, "Hola"),
            new MensajeChat(RolMensaje.ASISTENTE, "Buenos días"),
            new MensajeChat(RolMensaje.USUARIO, "¿Cuánto vale una olla arrocera?"));

    private HttpServer servidor;
    private final Deque<Respuesta> respuestas = new ArrayDeque<>();
    private final List<JsonNode> peticiones = new ArrayList<>();
    private final HerramientasAsistente herramientas = mock(HerramientasAsistente.class);

    private record Respuesta(int estado, String cuerpo) {
    }

    @BeforeEach
    void iniciarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/messages", intercambio -> {
            peticiones.add(JSON.readTree(intercambio.getRequestBody()));
            Respuesta respuesta = respuestas.isEmpty() ? new Respuesta(500, "{}") : respuestas.poll();
            byte[] cuerpo = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(respuesta.estado(), cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    private ProveedorIAClaude proveedor() {
        AnthropicClient cliente = AnthropicOkHttpClient.builder()
                .apiKey("clave-de-prueba")
                .baseUrl("http://127.0.0.1:" + servidor.getAddress().getPort())
                .maxRetries(0)
                .build();
        return new ProveedorIAClaude(cliente, herramientas, MODELO);
    }

    private static String mensaje(String motivo, String contenido) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[%s],"stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """.formatted(contenido, motivo);
    }

    private static String texto(String texto) {
        return "{\"type\":\"text\",\"text\":\"" + texto + "\"}";
    }

    @Test
    void sinClaveNoLlamaAClaude() {
        ProveedorIAClaude proveedor = new ProveedorIAClaude(null, herramientas, MODELO);

        assertFalse(proveedor.estaConfigurado());
        assertEquals(ProveedorIAClaude.SIN_CONFIGURAR, proveedor.responder(CONVERSACION));
        assertTrue(peticiones.isEmpty());
    }

    @Test
    void enviaInstruccionesYConversacionYDevuelveElTexto() {
        respuestas.add(new Respuesta(200, mensaje("end_turn", texto(" Cuesta $120.000 "))));
        ProveedorIAClaude proveedor = proveedor();

        assertTrue(proveedor.estaConfigurado());
        assertEquals("Claude (claude-opus-5-5)", proveedor.nombre());
        assertEquals("Cuesta $120.000", proveedor.responder(CONVERSACION));

        JsonNode peticion = peticiones.get(0);
        assertEquals(MODELO, peticion.get("model").asText());
        assertEquals("Instrucciones", peticion.get("system").asText());
        assertEquals("low", peticion.get("output_config").get("effort").asText());
        assertEquals(2, peticion.get("tools").size());
        JsonNode mensajes = peticion.get("messages");
        assertEquals(3, mensajes.size());
        assertEquals("user", mensajes.get(0).get("role").asText());
        assertEquals("assistant", mensajes.get(1).get("role").asText());
        assertEquals("user", mensajes.get(2).get("role").asText());
    }

    @Test
    void ejecutaLaHerramientaDeBusquedaYDevuelveElResultadoAClaude() {
        when(herramientas.buscarMejorOpcion("olla arrocera")).thenReturn("MEJOR OPCIÓN: olla a $95.000");
        respuestas.add(new Respuesta(200, mensaje("tool_use",
                "{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"firma\"},"
                        + "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"buscarMejorOpcion\","
                        + "\"input\":{\"producto\":\"olla arrocera\"}}")));
        respuestas.add(new Respuesta(200, mensaje("end_turn", texto("La mejor es la de $95.000"))));

        assertEquals("La mejor es la de $95.000", proveedor().responder(CONVERSACION));

        verify(herramientas).buscarMejorOpcion("olla arrocera");
        JsonNode mensajes = peticiones.get(1).get("messages");
        JsonNode turnoClaude = mensajes.get(mensajes.size() - 2);
        assertEquals("assistant", turnoClaude.get("role").asText());
        assertEquals("thinking", turnoClaude.get("content").get(0).get("type").asText());
        assertEquals("firma", turnoClaude.get("content").get(0).get("signature").asText());
        JsonNode resultado = mensajes.get(mensajes.size() - 1).get("content").get(0);
        assertEquals("tool_result", resultado.get("type").asText());
        assertEquals("toolu_1", resultado.get("tool_use_id").asText());
        assertTrue(resultado.get("content").toString().contains("$95.000"));
    }

    @Test
    void ejecutaLaHerramientaDelDolar() {
        when(herramientas.valorDelDolar()).thenReturn("1 dólar = $4.000");
        respuestas.add(new Respuesta(200, mensaje("tool_use",
                "{\"type\":\"tool_use\",\"id\":\"toolu_2\",\"name\":\"valorDelDolar\",\"input\":{}}")));
        respuestas.add(new Respuesta(200, mensaje("end_turn", texto("Hoy el dólar está en $4.000"))));

        assertEquals("Hoy el dólar está en $4.000", proveedor().responder(CONVERSACION));
        verify(herramientas).valorDelDolar();
    }

    @Test
    void siClaudeSeNiegaDaUnMensajeAmable() {
        respuestas.add(new Respuesta(200, mensaje("refusal", "")));

        assertEquals(ProveedorIAClaude.NO_PERMITIDO, proveedor().responder(CONVERSACION));
    }

    @Test
    void noSeQuedaEnUnCicloInfinitoDeHerramientas() {
        when(herramientas.valorDelDolar()).thenReturn("1 dólar = $4.000");
        for (int i = 0; i < ProveedorIAClaude.MAXIMO_RONDAS; i++) {
            respuestas.add(new Respuesta(200, mensaje("tool_use",
                    "{\"type\":\"tool_use\",\"id\":\"toolu_" + i + "\",\"name\":\"valorDelDolar\",\"input\":{}}")));
        }

        assertEquals(ProveedorIAClaude.NO_DISPONIBLE, proveedor().responder(CONVERSACION));
        assertEquals(ProveedorIAClaude.MAXIMO_RONDAS, peticiones.size());
    }

    @Test
    void siLaApiFallaDevuelveUnMensajeAmable() {
        respuestas.add(new Respuesta(500, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"x\"}}"));

        assertEquals(ProveedorIAClaude.NO_DISPONIBLE, proveedor().responder(CONVERSACION));
        verifyNoInteractions(herramientas);
    }
}
