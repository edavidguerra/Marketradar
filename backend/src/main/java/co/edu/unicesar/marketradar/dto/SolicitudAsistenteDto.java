package co.edu.unicesar.marketradar.dto;

import java.util.List;

/**
 * Cuerpo de {@code POST /api/v1/asistente/mensajes}.
 *
 * @param mensaje pregunta nueva del usuario
 * @param historial mensajes anteriores de la conversación
 */
public record SolicitudAsistenteDto(String mensaje, List<MensajeHistorialDto> historial) {
}
