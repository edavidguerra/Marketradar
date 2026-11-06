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

    /**
     * Términos que buscaron varias personas y dieron resultados, del más buscado al menos buscado.
     * Solo cuenta los que buscaron al menos dos personas distintas, para no mostrar a nadie
     * la búsqueda privada de otra persona.
     *
     * @param limite cantidad máxima de términos
     * @return términos en minúsculas con la cantidad de veces que se buscaron
     */
    List<TerminoPopular> terminosPopulares(int limite);

    /**
     * Término buscado y cuántas veces se buscó.
     *
     * @param termino texto buscado, en minúsculas
     * @param veces cantidad de búsquedas
     */
    record TerminoPopular(String termino, int veces) {
    }
}
