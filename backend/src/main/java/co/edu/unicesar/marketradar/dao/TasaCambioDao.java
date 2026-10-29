package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.TasaCambio;

import java.util.Optional;

/**
 * Acceso a la tabla {@code tasa_cambio}, que guarda el histórico de tasas consultadas.
 */
public interface TasaCambioDao {

    /**
     * Guarda la tasa; si ya existe una para la misma moneda y fecha, la reemplaza.
     *
     * @param tasa tasa consultada
     */
    void guardar(TasaCambio tasa);

    /**
     * @param moneda código ISO 4217
     * @return la tasa más reciente guardada de esa moneda
     */
    Optional<TasaCambio> buscarVigente(String moneda);
}
