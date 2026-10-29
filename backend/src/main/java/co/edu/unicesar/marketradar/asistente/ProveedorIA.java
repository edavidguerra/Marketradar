package co.edu.unicesar.marketradar.asistente;

import java.util.List;

/**
 * Contrato del modelo de lenguaje que usa el chatbot. El proveedor concreto (OpenAI, Gemini,
 * Claude u otro) está pendiente de decisión del equipo; cambiarlo no afecta al resto del sistema.
 */
public interface ProveedorIA {

    /**
     * Nombre del proveedor, para el endpoint de salud.
     *
     * @return nombre legible
     */
    String nombre();

    /**
     * Indica si el proveedor tiene credenciales y puede responder.
     *
     * @return {@code true} si está listo
     */
    boolean estaConfigurado();

    /**
     * Genera la respuesta del asistente a partir de la conversación.
     *
     * @param conversacion mensajes en orden, empezando por las instrucciones del sistema
     * @return texto de la respuesta
     */
    String responder(List<MensajeChat> conversacion);
}
