package co.edu.unicesar.marketradar.correo;

/**
 * Un correo para mandar, con texto plano y HTML (el lector de correo elige cuál mostrar).
 *
 * @param para dirección del destinatario
 * @param asunto asunto
 * @param texto contenido en texto plano
 * @param html contenido en HTML, ya escapado
 */
public record MensajeCorreo(String para, String asunto, String texto, String html) {
}
