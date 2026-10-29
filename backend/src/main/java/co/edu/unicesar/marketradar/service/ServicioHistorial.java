package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.BusquedaDao;
import co.edu.unicesar.marketradar.domain.Busqueda;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Historial de búsquedas de cada persona.
 */
@Service
public class ServicioHistorial {

    /** Cantidad de búsquedas que se muestran en el historial. */
    public static final int LIMITE = 50;

    private final BusquedaDao busquedaDao;

    public ServicioHistorial(BusquedaDao busquedaDao) {
        this.busquedaDao = busquedaDao;
    }

    /**
     * @param usuarioId persona con sesión
     * @return sus últimas búsquedas, de la más reciente a la más antigua
     */
    public List<Busqueda> listar(long usuarioId) {
        return busquedaDao.listarPorUsuario(usuarioId, LIMITE);
    }

    /**
     * Borra todo el historial de la persona.
     *
     * @param usuarioId persona con sesión
     * @return cantidad de búsquedas borradas
     */
    public int borrar(long usuarioId) {
        return busquedaDao.eliminarPorUsuario(usuarioId);
    }
}
