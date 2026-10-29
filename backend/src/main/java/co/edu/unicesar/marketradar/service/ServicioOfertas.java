package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.HistorialPrecioDao;
import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.domain.HistorialPrecio;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Consulta el detalle y el historial de precios de las ofertas guardadas.
 */
@Service
public class ServicioOfertas {

    private final OfertaDao ofertaDao;
    private final HistorialPrecioDao historialDao;

    public ServicioOfertas(OfertaDao ofertaDao, HistorialPrecioDao historialDao) {
        this.ofertaDao = ofertaDao;
        this.historialDao = historialDao;
    }

    /**
     * Obtiene una oferta por su identificador.
     *
     * @param id identificador de la oferta
     * @return la oferta
     * @throws RecursoNoEncontradoException si no existe
     */
    public Oferta obtener(long id) {
        return ofertaDao.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese producto. Vuelva a buscarlo."));
    }

    /**
     * Obtiene el historial de precios de una oferta.
     *
     * @param id identificador de la oferta
     * @return registros de precio del más antiguo al más reciente
     * @throws RecursoNoEncontradoException si la oferta no existe
     */
    public List<HistorialPrecio> historial(long id) {
        obtener(id);
        return historialDao.listarPorOferta(id);
    }
}
