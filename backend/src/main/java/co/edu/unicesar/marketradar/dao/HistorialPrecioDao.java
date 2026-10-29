package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.HistorialPrecio;

import java.util.List;

/**
 * Acceso a la tabla {@code historial_precio}.
 */
public interface HistorialPrecioDao extends Dao<HistorialPrecio> {

    /**
     * Lista el historial de una oferta, del más antiguo al más reciente.
     *
     * @param ofertaId oferta consultada
     * @return registros de precio
     */
    List<HistorialPrecio> listarPorOferta(long ofertaId);
}
