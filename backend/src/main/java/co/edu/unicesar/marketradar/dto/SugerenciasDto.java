package co.edu.unicesar.marketradar.dto;

import java.util.List;

/**
 * Respuesta de {@code GET /api/v1/busqueda/sugerencias}.
 *
 * @param sugerencias productos parecidos a lo que la persona va escribiendo
 */
public record SugerenciasDto(List<String> sugerencias) {
}
