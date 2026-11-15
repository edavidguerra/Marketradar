package co.edu.unicesar.marketradar.correo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Manda los correos por la API HTTPS (puerto 443) de Brevo o de Resend. Es la forma que funciona en el plan gratis de
 * Render, que bloquea los puertos SMTP. La clave de la API solo vive en el servidor y nunca se escribe en los registros.
 *
 * <ul>
 *   <li>Brevo: {@code POST https://api.brevo.com/v3/smtp/email} con la cabecera {@code api-key}; responde 201 con
 *       {@code messageId}. El remitente debe estar verificado en Brevo (sirve un correo propio, sin dominio).</li>
 *   <li>Resend: {@code POST https://api.resend.com/emails} con {@code Authorization: Bearer}; responde 200 con
 *       {@code id}. Sin dominio verificado, Resend solo deja mandar al correo del dueño de la cuenta.</li>
 * </ul>
 *
 * <p>Una respuesta 2xx quiere decir que el proveedor <b>aceptó</b> el correo, no que llegó. Los errores 429, 5xx y los
 * tiempos de espera se marcan como transitorios (vale la pena reintentar); los demás 4xx no.</p>
 */
public class TransporteCorreoHttp implements TransporteCorreo {

    private static final Logger LOG = LoggerFactory.getLogger(TransporteCorreoHttp.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PropiedadesCorreo propiedades;
    private final PropiedadesCorreo.Modo modo;
    private final HttpClient http;
    private final URI destino;

    /**
     * @param propiedades configuración (modo BREVO o RESEND)
     * @param http cliente HTTP
     */
    public TransporteCorreoHttp(PropiedadesCorreo propiedades, HttpClient http) {
        this.propiedades = propiedades;
        this.modo = propiedades.modo();
        if (modo != PropiedadesCorreo.Modo.BREVO && modo != PropiedadesCorreo.Modo.RESEND) {
            throw new IllegalArgumentException("TransporteCorreoHttp solo sirve para Brevo o Resend");
        }
        this.http = http;
        String base = !propiedades.urlApi().isEmpty() ? propiedades.urlApi()
                : modo == PropiedadesCorreo.Modo.BREVO ? "https://api.brevo.com" : "https://api.resend.com";
        this.destino = URI.create(base + (modo == PropiedadesCorreo.Modo.BREVO ? "/v3/smtp/email" : "/emails"));
    }

    @Override
    public boolean estaConfigurado() {
        return propiedades.configurado();
    }

    @Override
    public String nombre() {
        return modo == PropiedadesCorreo.Modo.BREVO ? "brevo" : "resend";
    }

    @Override
    public ResultadoEnvio enviar(MensajeCorreo mensaje) {
        if (!estaConfigurado()) {
            throw new CorreoNoEnviadoException("El envío de correos no está configurado (falta CORREO_API_KEY o "
                    + "CORREO_REMITENTE)", false);
        }
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(destino)
                .timeout(Duration.ofSeconds(propiedades.timeoutSegundos()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo(mensaje), StandardCharsets.UTF_8));
        if (modo == PropiedadesCorreo.Modo.BREVO) {
            solicitud.header("api-key", propiedades.apiKey());
        } else {
            solicitud.header("Authorization", "Bearer " + propiedades.apiKey());
            if (mensaje.claveIdempotencia() != null) {
                // Resend no manda dos veces un correo con la misma clave (durante 24 horas).
                solicitud.header("Idempotency-Key", mensaje.claveIdempotencia());
            }
        }
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(solicitud.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new CorreoNoEnviadoException(nombre() + " no respondió a tiempo", true);
        } catch (IOException e) {
            throw new CorreoNoEnviadoException("No se pudo conectar con " + nombre() + " ("
                    + e.getClass().getSimpleName() + ")", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CorreoNoEnviadoException("Se interrumpió el envío", true);
        }
        int estado = respuesta.statusCode();
        if (estado >= 200 && estado < 300) {
            String id = idMensaje(respuesta.body());
            LOG.info("Correo aceptado por {} para {} (id {})", nombre(), mensaje.paraEnRegistro(), id);
            return new ResultadoEnvio(nombre(), id);
        }
        boolean transitorio = estado == 429 || estado >= 500;
        String motivo = motivo(respuesta.body());
        throw new CorreoNoEnviadoException(nombre() + " rechazó el correo (HTTP " + estado
                + (motivo == null ? "" : ": " + motivo) + ")", transitorio);
    }

    private String cuerpo(MensajeCorreo mensaje) {
        ObjectNode raiz = JSON.createObjectNode();
        if (modo == PropiedadesCorreo.Modo.BREVO) {
            raiz.putObject("sender").put("name", propiedades.nombreRemitente()).put("email", propiedades.remitente());
            raiz.putArray("to").addObject().put("email", mensaje.para());
            raiz.put("subject", mensaje.asunto());
            raiz.put("htmlContent", mensaje.html());
            raiz.put("textContent", mensaje.texto());
            if (mensaje.claveIdempotencia() != null) {
                raiz.putObject("headers").put("X-Marketradar-Id", mensaje.claveIdempotencia());
            }
        } else {
            raiz.put("from", propiedades.nombreRemitente().replaceAll("[<>\"]", "") + " <" + propiedades.remitente() + ">");
            raiz.putArray("to").add(mensaje.para());
            raiz.put("subject", mensaje.asunto());
            raiz.put("html", mensaje.html());
            raiz.put("text", mensaje.texto());
        }
        return raiz.toString();
    }

    private static String idMensaje(String cuerpo) {
        try {
            JsonNode nodo = JSON.readTree(cuerpo == null || cuerpo.isBlank() ? "{}" : cuerpo);
            String id = nodo.path("messageId").asText(nodo.path("id").asText(null));
            return id == null || id.isBlank() ? null : id;
        } catch (IOException e) {
            return null;
        }
    }

    /** Solo el código o el mensaje corto del proveedor (nunca trae la clave), recortado. */
    private static String motivo(String cuerpo) {
        try {
            JsonNode nodo = JSON.readTree(cuerpo == null || cuerpo.isBlank() ? "{}" : cuerpo);
            String texto = nodo.path("code").asText(nodo.path("name").asText(""));
            String detalle = nodo.path("message").asText("");
            String junto = (texto + (detalle.isEmpty() ? "" : " " + detalle)).strip();
            return junto.isEmpty() ? null : junto.length() > 160 ? junto.substring(0, 160) : junto;
        } catch (IOException e) {
            return null;
        }
    }
}
