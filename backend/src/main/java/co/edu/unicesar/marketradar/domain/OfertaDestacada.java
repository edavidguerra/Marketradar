package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;

/**
 * Oferta guardada que se puede recomendar en el inicio.
 *
 * @param oferta oferta guardada
 * @param codigoMarketplace tienda de la oferta
 * @param precioAnterior precio más alto registrado en el periodo, o {@code null} si no aplica
 */
public record OfertaDestacada(Oferta oferta, String codigoMarketplace, BigDecimal precioAnterior) {

    /**
     * @return cuánto bajó el precio, o cero si no bajó
     */
    public BigDecimal baja() {
        if (precioAnterior == null || precioAnterior.compareTo(oferta.getPrecioActual()) <= 0) {
            return BigDecimal.ZERO;
        }
        return precioAnterior.subtract(oferta.getPrecioActual());
    }
}
