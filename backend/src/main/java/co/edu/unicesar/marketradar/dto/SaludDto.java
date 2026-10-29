package co.edu.unicesar.marketradar.dto;

import java.util.Map;

/**
 * Estado del servicio para {@code GET /api/v1/salud}.
 *
 * @param estado {@code OK} si el servicio responde
 * @param adaptadores disponibilidad de cada adaptador, por código
 * @param asistenteDisponible si el chatbot tiene un proveedor de IA configurado
 */
public record SaludDto(String estado, Map<String, Boolean> adaptadores, boolean asistenteDisponible) {
}
