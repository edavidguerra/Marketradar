package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.Oferta;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Detalle de una oferta guardada, para {@code GET /api/v1/ofertas/{id}}.
 *
 * @param id identificador
 * @param titulo nombre del producto
 * @param precio precio sin envío
 * @param costoEnvio costo de envío
 * @param precioTotal precio más envío
 * @param moneda moneda
 * @param condicion condición del producto
 * @param vendedor vendedor
 * @param urlDestino enlace a la tienda
 * @param urlImagen enlace a la foto
 * @param calificacion estrellas de 0 a 5, {@code null} si no se conocen
 * @param totalResenas cantidad de reseñas
 * @param consultadoEn momento de la última consulta
 */
public record OfertaDetalleDto(Long id, String titulo, BigDecimal precio, BigDecimal costoEnvio,
                               BigDecimal precioTotal, String moneda, String condicion, String vendedor,
                               String urlDestino, String urlImagen, BigDecimal calificacion,
                               Integer totalResenas, OffsetDateTime consultadoEn) {

    /**
     * Convierte una oferta del dominio en el DTO.
     *
     * @param oferta oferta guardada
     * @return DTO
     */
    public static OfertaDetalleDto desde(Oferta oferta) {
        return new OfertaDetalleDto(oferta.getId(), oferta.getTitulo(), oferta.getPrecioActual(),
                oferta.getCostoEnvio(), oferta.calcularPrecioTotal(), oferta.getMoneda(), oferta.getCondicion(),
                oferta.getVendedor(), oferta.getUrlDestino(), oferta.getUrlImagen(), oferta.getCalificacion(),
                oferta.getTotalResenas(), oferta.getConsultadoEn());
    }
}
