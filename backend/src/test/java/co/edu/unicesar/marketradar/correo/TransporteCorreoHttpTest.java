package co.edu.unicesar.marketradar.correo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manda correos a un servidor HTTP falso en la misma máquina que responde como Brevo o Resend: nada sale a internet ni
 * se usa una clave real.
 */
class TransporteCorreoHttpTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CLAVE = "xkeysib-clave-de-prueba-que-no-debe-aparecer";

    private HttpServer servidor;
    private final List<Map<String, Object>> recibidos = new ArrayList<>();
    private volatile int estado = 201;
    private volatile String cuerpo = "{\"messageId\":\"<202611141200.1@smtp-relay.mailin.fr>\"}";
    private volatile long demoraMs;

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            Map<String, Object> pedido = new ConcurrentHashMap<>();
            pedido.put("ruta", intercambio.getRequestURI().getPath());
            pedido.put("cabeceras", intercambio.getRequestHeaders());
            pedido.put("cuerpo", new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            recibidos.add(pedido);
            if (demoraMs > 0) {
                try {
                    Thread.sleep(demoraMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] respuesta = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(estado, respuesta.length);
            intercambio.getResponseBody().write(respuesta);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    private PropiedadesCorreo propiedades(String proveedor, int timeout) {
        return new PropiedadesCorreo(null, 0, null, null, "avisos@marketradar.test", "Marketradar", false, false,
                timeout, "http://localhost:8080", proveedor, CLAVE,
                "http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    private static MensajeCorreo mensaje() {
        return new MensajeCorreo("ana@example.com", "Confirme su correo", "texto", "<p>html</p>", "verificacion-abc");
    }

    @Test
    void brevoAceptaElCorreoYDevuelveSuIdentificador() throws Exception {
        TransporteCorreoHttp transporte = new TransporteCorreoHttp(propiedades("brevo", 5), HttpClient.newHttpClient());
        assertTrue(transporte.estaConfigurado());
        ResultadoEnvio resultado = transporte.enviar(mensaje());
        assertEquals("brevo", resultado.proveedor());
        assertEquals("<202611141200.1@smtp-relay.mailin.fr>", resultado.idMensaje());

        Map<String, Object> pedido = recibidos.get(0);
        assertEquals("/v3/smtp/email", pedido.get("ruta"));
        @SuppressWarnings("unchecked")
        Map<String, List<String>> cabeceras = (Map<String, List<String>>) pedido.get("cabeceras");
        assertEquals(CLAVE, cabeceras.get("Api-key").get(0), "La clave va en la cabecera, no en el cuerpo");
        JsonNode enviado = JSON.readTree((String) pedido.get("cuerpo"));
        assertEquals("avisos@marketradar.test", enviado.path("sender").path("email").asText());
        assertEquals("ana@example.com", enviado.path("to").get(0).path("email").asText());
        assertEquals("<p>html</p>", enviado.path("htmlContent").asText());
        assertEquals("texto", enviado.path("textContent").asText());
        assertFalse(((String) pedido.get("cuerpo")).contains(CLAVE));
    }

    @Test
    void resendUsaBearerYLaClaveDeIdempotencia() throws Exception {
        cuerpo = "{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}";
        estado = 200;
        TransporteCorreoHttp transporte = new TransporteCorreoHttp(propiedades("resend", 5), HttpClient.newHttpClient());
        ResultadoEnvio resultado = transporte.enviar(mensaje());
        assertEquals("resend", resultado.proveedor());
        assertEquals("49a3999c-0ce1-4ea6-ab68-afcd6dc2e794", resultado.idMensaje());
        Map<String, Object> pedido = recibidos.get(0);
        assertEquals("/emails", pedido.get("ruta"));
        @SuppressWarnings("unchecked")
        Map<String, List<String>> cabeceras = (Map<String, List<String>>) pedido.get("cabeceras");
        assertEquals("Bearer " + CLAVE, cabeceras.get("Authorization").get(0));
        assertEquals("verificacion-abc", cabeceras.get("Idempotency-key").get(0));
        JsonNode enviado = JSON.readTree((String) pedido.get("cuerpo"));
        assertEquals("Marketradar <avisos@marketradar.test>", enviado.path("from").asText());
    }

    @Test
    void unaClaveInvalidaEsUnFalloDefinitivoYNoMuestraLaClave() {
        estado = 401;
        cuerpo = "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}";
        CorreoNoEnviadoException e = assertThrows(CorreoNoEnviadoException.class,
                () -> new TransporteCorreoHttp(propiedades("brevo", 5), HttpClient.newHttpClient()).enviar(mensaje()));
        assertFalse(e.esTransitorio(), "Reintentar no arregla una clave inválida");
        assertTrue(e.getMessage().contains("HTTP 401"));
        assertFalse(e.getMessage().contains(CLAVE));
    }

    @Test
    void unErrorDelProveedorOUnLimiteSonPasajeros() {
        for (int codigo : new int[] {429, 500, 503}) {
            estado = codigo;
            cuerpo = "{}";
            CorreoNoEnviadoException e = assertThrows(CorreoNoEnviadoException.class,
                    () -> new TransporteCorreoHttp(propiedades("brevo", 5), HttpClient.newHttpClient()).enviar(mensaje()));
            assertTrue(e.esTransitorio(), "HTTP " + codigo + " se puede reintentar");
        }
    }

    @Test
    void siElProveedorNoRespondeATiempoEsPasajero() {
        demoraMs = 2500;
        CorreoNoEnviadoException e = assertThrows(CorreoNoEnviadoException.class,
                () -> new TransporteCorreoHttp(propiedades("brevo", 1), HttpClient.newHttpClient()).enviar(mensaje()));
        assertTrue(e.esTransitorio());
        assertTrue(e.getMessage().contains("no respondió a tiempo"));
    }

    @Test
    void sinConexionEsPasajero() {
        PropiedadesCorreo caido = new PropiedadesCorreo(null, 0, null, null, "avisos@marketradar.test", null, false, false,
                2, null, "brevo", CLAVE, "http://127.0.0.1:1");
        CorreoNoEnviadoException e = assertThrows(CorreoNoEnviadoException.class,
                () -> new TransporteCorreoHttp(caido, HttpClient.newHttpClient()).enviar(mensaje()));
        assertTrue(e.esTransitorio());
        assertFalse(e.getMessage().contains(CLAVE));
    }

    @Test
    void sinRemitenteNoEstaConfiguradoYNoManda() {
        PropiedadesCorreo sinRemitente = new PropiedadesCorreo(null, 0, null, null, "", null, false, false, 5, null,
                "brevo", CLAVE, "http://127.0.0.1:" + servidor.getAddress().getPort());
        TransporteCorreoHttp transporte = new TransporteCorreoHttp(sinRemitente, HttpClient.newHttpClient());
        assertFalse(transporte.estaConfigurado());
        assertThrows(CorreoNoEnviadoException.class, () -> transporte.enviar(mensaje()));
        assertTrue(recibidos.isEmpty());
    }

    @Test
    void elModoAutomaticoEligeLaApiSiHayClaveYNuncaMuestraLaClave() {
        PropiedadesCorreo brevo = new PropiedadesCorreo("smtp.gmail.com", 587, "u", "c", "a@b.co", null, true, false, 10,
                null, "auto", "xkeysib-123", null);
        assertEquals(PropiedadesCorreo.Modo.BREVO, brevo.modo());
        assertTrue(brevo.configurado());
        assertFalse(brevo.toString().contains("xkeysib-123"));
        assertEquals(PropiedadesCorreo.Modo.RESEND, new PropiedadesCorreo(null, 0, null, null, "a@b.co", null, false,
                false, 10, null, null, "re_abc", null).modo());
        PropiedadesCorreo smtp = new PropiedadesCorreo("smtp.gmail.com", 587, "u", "c", "a@b.co", null, true, false, 10,
                null, "auto", "", null);
        assertEquals(PropiedadesCorreo.Modo.SMTP, smtp.modo());
        assertTrue(smtp.puertoSmtpBloqueadoEnRenderGratis(), "587 es uno de los puertos que bloquea Render gratis");
        PropiedadesCorreo nada = new PropiedadesCorreo(null, 0, null, null, "a@b.co", null, false, false, 10, null, null,
                null, null);
        assertEquals(PropiedadesCorreo.Modo.NINGUNO, nada.modo());
        assertFalse(nada.configurado());
        assertNull(new ResultadoEnvio("brevo", "  ").idMensaje());
    }

    @Test
    void laConfiguracionCreaElTransporteQueCorresponde() {
        ConfiguracionCorreo configuracion = new ConfiguracionCorreo();
        assertEquals("brevo", configuracion.transporteCorreo(propiedades("brevo", 5)).nombre());
        assertEquals("resend", configuracion.transporteCorreo(propiedades("resend", 5)).nombre());
        assertEquals("smtp", configuracion.transporteCorreo(new PropiedadesCorreo("127.0.0.1", 2525, null, null,
                "a@b.co", null, false, false, 5, null)).nombre());
    }
}
