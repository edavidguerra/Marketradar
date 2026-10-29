package co.edu.unicesar.marketradar.dto;

/**
 * Formulario para radicar una PQR.
 *
 * @param tipo PETICION, QUEJA, RECLAMO o SUGERENCIA
 * @param asunto resumen corto
 * @param descripcion detalle
 */
public record SolicitudPqrDto(String tipo, String asunto, String descripcion) {
}
