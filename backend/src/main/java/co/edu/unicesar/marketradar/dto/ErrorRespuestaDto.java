package co.edu.unicesar.marketradar.dto;

/**
 * Formato uniforme de error de la API.
 *
 * @param codigo código del error, por ejemplo {@code VALIDACION}
 * @param mensajeUsuario frase amable y comprensible para el usuario
 * @param detalleTecnico detalle para desarrolladores; solo se incluye en modo desarrollo
 */
public record ErrorRespuestaDto(String codigo, String mensajeUsuario, String detalleTecnico) {
}
