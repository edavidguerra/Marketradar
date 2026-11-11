package co.edu.unicesar.marketradar.ia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Optional;

/**
 * Cliente de la API de Gemini (Google AI Studio) para pedir respuestas en JSON.
 *
 * <p>Usa la API REST {@code generateContent} con salida estructurada ({@code responseMimeType} y
 * {@code responseSchema}), así la respuesta siempre trae los campos esperados. La clave va en la cabecera
 * {@code x-goog-api-key}, nunca en la URL, para que no aparezca en errores ni registros.</p>
 *
 * <p>Si no hay clave, si Gemini tarda o si responde con error, se devuelve vacío y la búsqueda sigue con los
 * filtros de siempre: la IA ayuda, pero la página no depende de ella.</p>
 */
@Component
public class ClienteGemini {

    private static final Logger LOG = LoggerFactory.getLogger(ClienteGemini.class);

    private final RestClient cliente;
    private final ObjectMapper json = new ObjectMapper();
    private final boolean habilitado;
    private final String apiKey;
    private final String modelo;

    /**
     * @param builder constructor de clientes HTTP de Spring
     * @param habilitado si se usa Gemini en las búsquedas
     * @param apiKey clave de Google AI Studio
     * @param modelo modelo de Gemini, por ejemplo {@code gemini-3.5-flash-lite}
     * @param url dirección base de la API
     */
    @Autowired
    public ClienteGemini(RestClient.Builder builder,
                         @Value("${marketradar.ia-busqueda.habilitado:true}") boolean habilitado,
                         @Value("${marketradar.ia-busqueda.api-key:}") String apiKey,
                         @Value("${marketradar.ia-busqueda.modelo:gemini-3.5-flash-lite}") String modelo,
                         @Value("${marketradar.ia-busqueda.url:https://generativelanguage.googleapis.com}") String url) {
        this(builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(Duration.ofSeconds(3))
                                .withReadTimeout(Duration.ofSeconds(10))))
                .baseUrl(url)
                .build(), habilitado, apiKey, modelo);
    }

    /** Para las pruebas, con el cliente HTTP ya creado. */
    ClienteGemini(RestClient cliente, boolean habilitado, String apiKey, String modelo) {
        this.cliente = cliente;
        this.habilitado = habilitado;
        this.apiKey = apiKey;
        this.modelo = modelo == null || modelo.isBlank() ? "gemini-3.5-flash-lite" : modelo.strip();
    }

    /** @return {@code true} si hay clave y no se apagó con {@code GEMINI_HABILITADO=false} */
    public boolean estaHabilitado() {
        return habilitado && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Pide a Gemini una respuesta en JSON.
     *
     * @param instrucciones qué debe hacer el modelo
     * @param texto datos de la consulta
     * @param esquema esquema de la respuesta (formato OpenAPI que acepta Gemini)
     * @return el JSON de la respuesta, o vacío si no se pudo
     */
    public Optional<JsonNode> pedirJson(String instrucciones, String texto, ObjectNode esquema) {
        if (!estaHabilitado()) {
            return Optional.empty();
        }
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.putObject("systemInstruction").putArray("parts").addObject().put("text", instrucciones);
        ObjectNode mensaje = cuerpo.putArray("contents").addObject();
        mensaje.put("role", "user");
        mensaje.putArray("parts").addObject().put("text", texto);
        ObjectNode configuracion = cuerpo.putObject("generationConfig");
        configuracion.put("responseMimeType", "application/json");
        configuracion.set("responseSchema", esquema);
        configuracion.put("temperature", 0);
        try {
            String respuesta = cliente.post()
                    .uri("/v1beta/models/{modelo}:generateContent", modelo)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(cuerpo))
                    .retrieve()
                    .body(String.class);
            return leerTexto(respuesta).flatMap(this::leerJson);
        } catch (RestClientResponseException e) {
            // 429: se acabó la cuota gratis del minuto o del día. 400/404: modelo mal escrito o retirado.
            LOG.warn("Gemini respondió {} con el modelo {}", e.getStatusCode().value(), modelo);
            return Optional.empty();
        } catch (RestClientException | JsonProcessingException e) {
            LOG.warn("No se pudo consultar Gemini: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Junta el texto de la primera respuesta, sin las partes de razonamiento. */
    private Optional<String> leerTexto(String respuesta) throws JsonProcessingException {
        if (respuesta == null) {
            return Optional.empty();
        }
        JsonNode partes = json.readTree(respuesta).path("candidates").path(0).path("content").path("parts");
        StringBuilder texto = new StringBuilder();
        if (partes instanceof ArrayNode lista) {
            for (JsonNode parte : lista) {
                if (!parte.path("thought").asBoolean(false) && parte.hasNonNull("text")) {
                    texto.append(parte.get("text").asText());
                }
            }
        }
        return texto.isEmpty() ? Optional.empty() : Optional.of(texto.toString());
    }

    private Optional<JsonNode> leerJson(String texto) {
        try {
            JsonNode nodo = json.readTree(texto.strip());
            return nodo != null && nodo.isObject() ? Optional.of(nodo) : Optional.empty();
        } catch (JsonProcessingException e) {
            LOG.warn("Gemini no devolvió un JSON válido");
            return Optional.empty();
        }
    }

    /** @return objeto vacío para armar esquemas */
    public ObjectNode nuevoEsquema() {
        return json.createObjectNode();
    }
}
