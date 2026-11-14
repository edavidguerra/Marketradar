package co.edu.unicesar.marketradar.domain.mayorista;

import java.math.BigDecimal;

/**
 * Un precio por cantidad: desde {@code minimo} hasta {@code maximo} unidades, cada una cuesta {@code precio}.
 *
 * @param minimo cantidad mínima de la escala
 * @param maximo cantidad máxima, o {@code null} si no tiene tope ("1000 o más")
 * @param precio precio por unidad
 */
public record EscalaPrecio(long minimo, Long maximo, BigDecimal precio) {

    /** Valida la escala. */
    public EscalaPrecio {
        if (minimo < 1 || precio == null || precio.signum() <= 0 || maximo != null && maximo < minimo) {
            throw new IllegalArgumentException("Escala de precio inválida");
        }
    }

    /**
     * @param cantidad unidades
     * @return {@code true} si la cantidad cae en esta escala
     */
    public boolean incluye(long cantidad) {
        return cantidad >= minimo && (maximo == null || cantidad <= maximo);
    }
}
