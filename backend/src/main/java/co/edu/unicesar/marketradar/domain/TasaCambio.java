package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Valor de una moneda en pesos colombianos para una fecha, con la fuente de donde se obtuvo.
 *
 * @param monedaOrigen código ISO 4217 de la moneda, por ejemplo {@code USD}
 * @param valor pesos colombianos por una unidad de la moneda
 * @param vigenteDesde fecha desde la que rige el valor
 * @param fuente origen del dato, por ejemplo la TRM del Banco de la República
 */
public record TasaCambio(String monedaOrigen, BigDecimal valor, LocalDate vigenteDesde, String fuente) {

    /**
     * Valida que la tasa tenga moneda, valor positivo y fecha.
     */
    public TasaCambio {
        Objects.requireNonNull(monedaOrigen, "monedaOrigen");
        Objects.requireNonNull(vigenteDesde, "vigenteDesde");
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("La tasa de cambio debe ser mayor que cero");
        }
    }
}
