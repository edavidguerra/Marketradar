package co.edu.unicesar.marketradar.adapter;

import co.edu.unicesar.marketradar.domain.TasaCambio;

import java.util.Optional;

/**
 * Entrega la tasa de cambio vigente de una moneda a pesos colombianos. El conversor depende de esta
 * interfaz y no de la fuente concreta (DIP).
 */
@FunctionalInterface
public interface ProveedorTasas {

    /**
     * @param moneda código ISO 4217, por ejemplo {@code USD}
     * @return pesos por unidad de la moneda, o vacío si no hay tasa conocida
     */
    Optional<TasaCambio> tasaVigente(String moneda);
}
