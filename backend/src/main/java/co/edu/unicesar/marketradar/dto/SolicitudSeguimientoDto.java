package co.edu.unicesar.marketradar.dto;

/**
 * Formulario de "Seguir producto" y de los cambios en "Mis seguimientos".
 *
 * @param ofertaId oferta de los resultados (solo al seguir)
 * @param tipoAviso {@code baja_precio} o {@code precio_objetivo}
 * @param precioObjetivo precio en pesos, como lo escribió la persona ("500.000"); solo para {@code precio_objetivo}
 * @param termino lo que buscó la persona (solo al seguir)
 * @param estado {@code activo} o {@code pausado} (solo al cambiar)
 */
public record SolicitudSeguimientoDto(Long ofertaId, String tipoAviso, String precioObjetivo, String termino,
                                      String estado) {
}
