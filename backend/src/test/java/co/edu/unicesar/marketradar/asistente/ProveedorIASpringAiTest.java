package co.edu.unicesar.marketradar.asistente;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProveedorIASpringAiTest {

    private static final List<MensajeChat> CONVERSACION = List.of(
            new MensajeChat(RolMensaje.SISTEMA, "Instrucciones"),
            new MensajeChat(RolMensaje.USUARIO, "Hola"),
            new MensajeChat(RolMensaje.ASISTENTE, "Buenos días"),
            new MensajeChat(RolMensaje.USUARIO, "¿Cuánto vale un tensiómetro?"));

    private ChatModel modelo;
    private final HerramientasAsistente herramientas = mock(HerramientasAsistente.class);

    @BeforeEach
    void preparar() {
        modelo = mock(ChatModel.class);
        when(modelo.getDefaultOptions()).thenReturn(ToolCallingChatOptions.builder().build());
    }

    @Test
    void sinClaveNoLlamaAlModelo() {
        ProveedorIASpringAi proveedor = proveedor("");

        assertFalse(proveedor.estaConfigurado());
        assertEquals(ProveedorIASpringAi.SIN_CONFIGURAR, proveedor.responder(CONVERSACION));
        verify(modelo, never()).call(any(Prompt.class));
    }

    @Test
    void enviaLaConversacionConSusRolesYDevuelveLaRespuesta() {
        when(modelo.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(" Cuesta $120.000 ")))));
        ProveedorIASpringAi proveedor = proveedor("clave");

        assertTrue(proveedor.estaConfigurado());
        assertEquals("Cuesta $120.000", proveedor.responder(CONVERSACION));

        ArgumentCaptor<Prompt> enviado = ArgumentCaptor.forClass(Prompt.class);
        verify(modelo).call(enviado.capture());
        List<MessageType> roles = enviado.getValue().getInstructions().stream().map(m -> m.getMessageType()).toList();
        assertEquals(List.of(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER), roles);
    }

    @Test
    void siElModeloFallaRespondeConUnMensajeAmable() {
        when(modelo.call(any(Prompt.class))).thenThrow(new IllegalStateException("429 demasiadas peticiones"));

        assertEquals(ProveedorIASpringAi.NO_DISPONIBLE, proveedor("clave").responder(CONVERSACION));
    }

    private ProveedorIASpringAi proveedor(String clave) {
        PropiedadesMarketradar base = PropiedadesPrueba.crear();
        PropiedadesMarketradar propiedades = new PropiedadesMarketradar(base.modoDesarrollo(), base.busqueda(),
                base.cors(), base.moneda(), base.marketplaces(),
                new PropiedadesMarketradar.Asistente("spring-ai", clave), base.administrador(), base.mvp());
        return new ProveedorIASpringAi(ChatClient.builder(modelo), herramientas, propiedades, "gemini-2.5-flash");
    }
}
