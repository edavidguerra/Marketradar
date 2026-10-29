package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.Busqueda;

import java.util.List;

/**
 * Acceso a la tabla {@code busqueda}.
 */
public interface BusquedaDao extends Dao<Busqueda> {

    /**
     * Lista las búsquedas de un usuario, de la más reciente a la más antigua.
     *
     * @param usuarioId usuario con sesión
     * @param limite cantidad máxima de registros
     * @return historial de búsquedas
     */
    List<Busqueda> listarPorUsuario(long usuarioId, int limite);

    /**
     * Borra el historial de búsquedas de un usuario.
     *
     * @param usuarioId usuario con sesión
     * @return cantidad de registros borrados
     */
    int eliminarPorUsuario(long usuarioId);
}
