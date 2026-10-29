package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.OfertaNormalizada;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Oferta tal como la recibe el frontend en los resultados de búsqueda.
 *
 * @param id identificador para consultar detalle e historial; {@code null} si no se guardó
 * @param tienda código del marketplace
 * @param titulo nombre del producto
 * @param precio precio sin envío
 * @param costoEnvio costo de envío, {@code null} si no se conoce
 * @param precioTotal precio más envío
 * @param moneda moneda de los precios (COP)
 * @param condicion nuevo, usado, etc.
 * @param vendedor nombre del vendedor
 * @param urlDestino enlace a la tienda
 * @param urlImagen enlace a la foto
 * @param calificacion estrellas de 0 a 5 según las reseñas, {@code null} si la tienda no las informa
 * @param totalResenas cantidad de reseñas
 * @param consultadoEn momento de la consulta, para mostrar qué tan reciente es el precio
 * @param mejorPrecio si es la oferta más barata de la búsqueda
 */
public record OfertaDto(Long id, String tienda, String titulo, BigDecimal precio, BigDecimal costoEnvio,
                        BigDecimal precioTotal, String moneda, String condicion, String vendedor, String urlDestino,
                        String urlImagen, BigDecimal calificacion, Integer totalResenas,
                        OffsetDateTime consultadoEn, boolean mejorPrecio) {

    /**
     * Convierte una oferta normalizada en el DTO de respuesta.
     *
     * @param oferta oferta del resultado
     * @param id identificador guardado
     * @param mejorPrecio si es la más barata
     * @return DTO
     */
    public static OfertaDto desde(OfertaNormalizada oferta, Long id, boolean mejorPrecio) {
        return new OfertaDto(id, oferta.getCodigoMarketplace(), oferta.getTitulo(), oferta.getPrecio(),
                oferta.getCostoEnvio(), oferta.precioTotal(), oferta.getMoneda(), oferta.getCondicion(),
                oferta.getVendedor(), oferta.getUrlDestino(), oferta.getUrlImagen(), oferta.getCalificacion(),
                oferta.getTotalResenas(), oferta.getConsultadoEn(), mejorPrecio);
    }
}
