package co.edu.unicesar.marketradar.dto;

/**
 * Respuesta del chatbot.
 *
 * @param respuesta texto de la respuesta
 * @param disponible si respondió un modelo de IA real
 */
public record RespuestaAsistenteDto(String respuesta, boolean disponible) {
}
