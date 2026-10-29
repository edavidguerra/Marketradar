package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.EstadoPqr;
import co.edu.unicesar.marketradar.domain.Pqr;

import java.util.List;

/**
 * Acceso a la tabla {@code pqr}. Las consultas traen el nombre y el correo de quien la radicó.
 */
public interface PqrDao extends Dao<Pqr> {

    /**
     * Lista las PQR de un usuario, de la más reciente a la más antigua.
     *
     * @param usuarioId usuario que las radicó
     * @return PQR del usuario
     */
    List<Pqr> listarPorUsuario(long usuarioId);

    /**
     * Lista las PQR para el administrador, primero las más antiguas sin responder.
     *
     * @param estado filtro por estado, o {@code null} para todas
     * @return PQR con los datos de quien la radicó
     */
    List<Pqr> listarPorEstado(EstadoPqr estado);
}
