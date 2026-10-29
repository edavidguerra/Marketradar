package co.edu.unicesar.marketradar.dao;

import java.util.List;
import java.util.Optional;

/**
 * Operaciones CRUD genéricas de la capa de acceso a datos (patrón DAO).
 *
 * @param <T> clase de dominio que se persiste
 */
public interface Dao<T> {

    /**
     * Inserta una entidad y le asigna el identificador generado.
     *
     * @param entidad entidad a guardar
     * @return la misma entidad con su identificador
     */
    T insertar(T entidad);

    /**
     * Busca una entidad por su identificador.
     *
     * @param id identificador
     * @return la entidad, o vacío si no existe
     */
    Optional<T> buscarPorId(long id);

    /**
     * Lista todas las entidades.
     *
     * @return lista de entidades
     */
    List<T> listarTodos();

    /**
     * Actualiza una entidad existente.
     *
     * @param entidad entidad con los datos nuevos
     * @return {@code true} si se actualizó algún registro
     */
    boolean actualizar(T entidad);

    /**
     * Elimina una entidad por su identificador.
     *
     * @param id identificador
     * @return {@code true} si se eliminó algún registro
     */
    boolean eliminar(long id);
}
