package co.edu.unicesar.marketradar.asistente;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Proveedor del chatbot con Claude, usando el SDK oficial de Anthropic.
 * Claude puede llamar a {@link HerramientasAsistente} para consultar precios reales; esta clase
 * ejecuta esas herramientas y le devuelve el resultado hasta que Claude da la respuesta final.
 */
@Component
@ConditionalOnExpression("'${marketradar.asistente.proveedor:claude}' != 'ninguno'")
public class ProveedorIAClaude implements ProveedorIA {

    private static final Logger LOG = LoggerFactory.getLogger(ProveedorIAClaude.class);

    static final String SIN_CONFIGURAR = "El asistente todavía no está disponible. Mientras tanto, escriba el nombre "
            + "del producto en el buscador y le mostraremos dónde está más barato.";
    static final String NO_DISPONIBLE = "En este momento no pude responder. Intente de nuevo en un minuto o use el "
            + "buscador de la página.";
    static final String NO_PERMITIDO = "No puedo ayudarle con esa pregunta. Pregúnteme por un producto, un precio o "
            + "cómo usar Marketradar.";

    static final String HERRAMIENTA_BUSCAR = "buscarMejorOpcion";
    static final String HERRAMIENTA_DOLAR = "valorDelDolar";
    static final String HERRAMIENTA_PUNTAJE = "explicarPuntaje";

    /** Máximo de idas y vueltas con herramientas en una sola pregunta, para no gastar créditos de más. */
    static final int MAXIMO_RONDAS = 4;
    private static final long MAXIMO_TOKENS = 16_000L;

    private static final List<Tool> HERRAMIENTAS = List.of(
            Tool.builder()
                    .name(HERRAMIENTA_BUSCAR)
                    .description("Busca un producto en las tiendas conectadas a Marketradar. Devuelve lo que "
                            + "pidió la persona, la mejor opción de compra (si alguna cumple todo), las ofertas más "
                            + "baratas que cumplen y las alternativas que no cumplen, con lo que les falta, en pesos "
                            + "colombianos. Cada enlace dice a dónde lleva.")
                    .inputSchema(Tool.InputSchema.builder()
                            .properties(Tool.InputSchema.Properties.builder()
                                    .putAdditionalProperty("producto", JsonValue.from(Map.of(
                                            "type", "string",
                                            "description", "Nombre del producto, por ejemplo 'tensiómetro de brazo'")))
                                    .build())
                            .required(List.of("producto"))
                            .build())
                    .build(),
            Tool.builder()
                    .name(HERRAMIENTA_DOLAR)
                    .description("Devuelve cuántos pesos colombianos vale un dólar según Marketradar, con la fuente "
                            + "y la fecha de la tasa.")
                    .inputSchema(Tool.InputSchema.builder()
                            .properties(Tool.InputSchema.Properties.builder().build())
                            .build())
                    .build(),
            Tool.builder()
                    .name(HERRAMIENTA_PUNTAJE)
                    .description("Explica cómo elige Marketradar la mejor opción de compra y cómo calcula el puntaje "
                            + "de 0 a 100: qué datos usa, cuánto pesa cada uno y qué pasa cuando falta un dato.")
                    .inputSchema(Tool.InputSchema.builder()
                            .properties(Tool.InputSchema.Properties.builder().build())
                            .build())
                    .build());

    private final AnthropicClient cliente;
    private final HerramientasAsistente herramientas;
    private final String modelo;

    @Autowired
    public ProveedorIAClaude(PropiedadesMarketradar propiedades, HerramientasAsistente herramientas) {
        this(crearCliente(propiedades.asistente().apiKey()), herramientas, propiedades.asistente().modelo());
    }

    ProveedorIAClaude(AnthropicClient cliente, HerramientasAsistente herramientas, String modelo) {
        this.cliente = cliente;
        this.herramientas = herramientas;
        this.modelo = modelo == null || modelo.isBlank() ? "claude-opus-5-5" : modelo.strip();
    }

    private static AnthropicClient crearCliente(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey.strip())
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .build();
    }

    @Override
    public String nombre() {
        return "Claude (" + modelo + ")";
    }

    @Override
    public boolean estaConfigurado() {
        return cliente != null;
    }

    @Override
    public String responder(List<MensajeChat> conversacion) {
        if (cliente == null) {
            return SIN_CONFIGURAR;
        }
        try {
            return conversar(conversacion);
        } catch (AnthropicException e) {
            LOG.warn("Claude no respondió: {}", e.getMessage());
            return NO_DISPONIBLE;
        }
    }

    private String conversar(List<MensajeChat> conversacion) {
        MessageCreateParams.Builder peticion = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(MAXIMO_TOKENS)
                // Respuestas cortas de chat: poco esfuerzo es más rápido y gasta menos créditos.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .tools(HERRAMIENTAS.stream().map(ToolUnion::ofTool).toList());
        instrucciones(conversacion).ifPresent(peticion::system);
        agregarMensajes(peticion, conversacion);

        for (int ronda = 0; ronda < MAXIMO_RONDAS; ronda++) {
            Message respuesta = cliente.messages().create(peticion.build());
            Optional<StopReason> motivo = respuesta.stopReason();
            if (motivo.isPresent() && motivo.get().equals(StopReason.REFUSAL)) {
                return NO_PERMITIDO;
            }
            if (motivo.isEmpty() || !motivo.get().equals(StopReason.TOOL_USE)) {
                return textoDe(respuesta);
            }
            // Se devuelve la respuesta completa (con sus bloques de razonamiento) y luego los resultados.
            peticion.addMessage(respuesta);
            peticion.addMessage(MessageParam.ofUserOfContentBlockParams(ejecutarHerramientas(respuesta)));
        }
        LOG.warn("Claude pidió herramientas más de {} veces seguidas", MAXIMO_RONDAS);
        return NO_DISPONIBLE;
    }

    private List<ContentBlockParam> ejecutarHerramientas(Message respuesta) {
        List<ContentBlockParam> resultados = new ArrayList<>();
        for (ContentBlock bloque : respuesta.content()) {
            bloque.toolUse().ifPresent(uso -> resultados.add(ContentBlockParam.ofToolResult(
                    ToolResultBlockParam.builder()
                            .toolUseId(uso.id())
                            .content(ejecutar(uso))
                            .build())));
        }
        return resultados;
    }

    String ejecutar(ToolUseBlock uso) {
        try {
            return switch (uso.name()) {
                case HERRAMIENTA_BUSCAR -> herramientas.buscarMejorOpcion(texto(uso._input(), "producto"));
                case HERRAMIENTA_DOLAR -> herramientas.valorDelDolar();
                case HERRAMIENTA_PUNTAJE -> herramientas.explicarPuntaje();
                default -> "Herramienta desconocida: " + uso.name();
            };
        } catch (RuntimeException e) {
            LOG.warn("Falló la herramienta {}: {}", uso.name(), e.getMessage());
            return "No se pudo consultar en este momento.";
        }
    }

    private static String texto(JsonValue entrada, String campo) {
        Map<String, Object> campos = entrada.convert(new TypeReference<Map<String, Object>>() { });
        Object valor = campos == null ? null : campos.get(campo);
        return valor == null ? "" : valor.toString();
    }

    private static String textoDe(Message respuesta) {
        String texto = respuesta.content().stream()
                .flatMap(bloque -> bloque.text().stream())
                .map(bloqueTexto -> bloqueTexto.text())
                .collect(Collectors.joining("\n"))
                .strip();
        return texto.isEmpty() ? NO_DISPONIBLE : texto;
    }

    private static Optional<String> instrucciones(List<MensajeChat> conversacion) {
        String texto = conversacion.stream()
                .filter(mensaje -> mensaje.getRol() == RolMensaje.SISTEMA)
                .map(MensajeChat::getContenido)
                .collect(Collectors.joining("\n\n"));
        return texto.isBlank() ? Optional.empty() : Optional.of(texto);
    }

    /**
     * Pasa la conversación al formato de Claude: sin las instrucciones del sistema (van aparte) y
     * empezando siempre por un mensaje de la persona.
     */
    static void agregarMensajes(MessageCreateParams.Builder peticion, List<MensajeChat> conversacion) {
        boolean hayMensajes = false;
        for (MensajeChat mensaje : conversacion) {
            if (mensaje.getRol() == RolMensaje.USUARIO) {
                peticion.addUserMessage(mensaje.getContenido());
                hayMensajes = true;
            } else if (mensaje.getRol() == RolMensaje.ASISTENTE && hayMensajes) {
                peticion.addAssistantMessage(mensaje.getContenido());
            }
        }
    }
}
