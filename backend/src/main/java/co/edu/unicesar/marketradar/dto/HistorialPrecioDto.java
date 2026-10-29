package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.HistorialPrecio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Un punto del historial de precios de una oferta.
 *
 * @param precio precio registrado
 * @param moneda moneda
 * @param registradoEn fecha del registro
 */
public record HistorialPrecioDto(BigDecimal precio, String moneda, OffsetDateTime registradoEn) {

    /**
     * Convierte un registro del dominio en el DTO.
     *
     * @param registro registro de precio
     * @return DTO
     */
    public static HistorialPrecioDto desde(HistorialPrecio registro) {
        return new HistorialPrecioDto(registro.getPrecio(), registro.getMoneda(), registro.getRegistradoEn());
    }
}
