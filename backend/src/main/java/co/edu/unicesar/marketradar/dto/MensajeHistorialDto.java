package co.edu.unicesar.marketradar.dto;

/**
 * Mensaje previo de la conversación que envía el frontend.
 *
 * @param rol {@code usuario} o {@code asistente}
 * @param contenido texto del mensaje
 */
public record MensajeHistorialDto(String rol, String contenido) {
}
