package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.TasaCambio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tasa de cambio que se muestra junto a los precios convertidos.
 *
 * @param moneda código ISO 4217
 * @param valor pesos colombianos por una unidad
 * @param vigenteDesde fecha de la tasa
 * @param fuente de dónde se obtuvo
 */
public record TasaCambioDto(String moneda, BigDecimal valor, LocalDate vigenteDesde, String fuente) {

    /**
     * @param tasa tasa vigente
     * @return DTO
     */
    public static TasaCambioDto desde(TasaCambio tasa) {
        return new TasaCambioDto(tasa.monedaOrigen(), tasa.valor(), tasa.vigenteDesde(), tasa.fuente());
    }
}
