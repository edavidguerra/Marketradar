package co.edu.unicesar.marketradar.asistente;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Proveedor del chatbot con Spring AI. Usa el cliente compatible con OpenAI, así que sirve para
 * Gemini (endpoint OpenAI de Google, por defecto) o para OpenAI cambiando solo la configuración.
 * El modelo puede llamar a {@link HerramientasAsistente} para consultar precios reales.
 */
@Component
@ConditionalOnProperty(prefix = "marketradar.asistente", name = "proveedor", havingValue = "spring-ai")
public class ProveedorIASpringAi implements ProveedorIA {

    private static final Logger LOG = LoggerFactory.getLogger(ProveedorIASpringAi.class);

    static final String SIN_CONFIGURAR = "El asistente todavía no está disponible. Mientras tanto, escriba el nombre "
            + "del producto en el buscador y le mostraremos dónde está más barato.";
    static final String NO_DISPONIBLE = "En este momento no pude responder. Intente de nuevo en un minuto o use el "
            + "buscador de la página.";

    private final ChatClient chatClient;
    private final HerramientasAsistente herramientas;
    private final boolean configurado;
    private final String modelo;

    public ProveedorIASpringAi(ChatClient.Builder builder, HerramientasAsistente herramientas,
                               PropiedadesMarketradar propiedades,
                               @Value("${spring.ai.openai.chat.options.model:}") String modelo) {
        this.chatClient = builder.build();
        this.herramientas = herramientas;
        this.configurado = propiedades.asistente().apiKey() != null && !propiedades.asistente().apiKey().isBlank();
        this.modelo = modelo;
    }

    @Override
    public String nombre() {
        return "Spring AI (" + modelo + ")";
    }

    @Override
    public boolean estaConfigurado() {
        return configurado;
    }

    @Override
    public String responder(List<MensajeChat> conversacion) {
        if (!configurado) {
            return SIN_CONFIGURAR;
        }
        try {
            String respuesta = chatClient.prompt(new Prompt(aMensajes(conversacion)))
                    .tools(herramientas)
                    .call()
                    .content();
            return respuesta == null || respuesta.isBlank() ? NO_DISPONIBLE : respuesta.strip();
        } catch (RuntimeException e) {
            LOG.warn("El proveedor de IA no respondió: {}", e.getMessage());
            return NO_DISPONIBLE;
        }
    }

    static List<Message> aMensajes(List<MensajeChat> conversacion) {
        return conversacion.stream().map(ProveedorIASpringAi::aMensaje).toList();
    }

    private static Message aMensaje(MensajeChat mensaje) {
        return switch (mensaje.getRol()) {
            case SISTEMA -> new SystemMessage(mensaje.getContenido());
            case USUARIO -> new UserMessage(mensaje.getContenido());
            case ASISTENTE -> new AssistantMessage(mensaje.getContenido());
        };
    }
}
