package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.PqrDao;
import co.edu.unicesar.marketradar.domain.EstadoPqr;
import co.edu.unicesar.marketradar.domain.Pqr;
import co.edu.unicesar.marketradar.domain.TipoPqr;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import co.edu.unicesar.marketradar.validacion.ValidadorCampos;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Peticiones, quejas, reclamos y sugerencias: el usuario las radica y el administrador las responde.
 */
@Service
public class ServicioPqr {

    /** Máximo de PQR abiertas (sin respuesta) por persona, para evitar duplicados. */
    public static final int MAXIMO_ABIERTAS = 5;

    private final PqrDao pqrDao;

    public ServicioPqr(PqrDao pqrDao) {
        this.pqrDao = pqrDao;
    }

    /**
     * Radica una PQR nueva.
     *
     * @param usuarioId persona con sesión
     * @param tipo PETICION, QUEJA, RECLAMO o SUGERENCIA
     * @param asunto resumen de 5 a 120 caracteres
     * @param descripcion detalle de 20 a 2000 caracteres
     * @return PQR radicada
     */
    public Pqr radicar(long usuarioId, String tipo, String asunto, String descripcion) {
        Pqr pqr = new Pqr(usuarioId,
                ValidadorCampos.opcion(tipo, TipoPqr.class, "el tipo de solicitud"),
                ValidadorCampos.texto(asunto, "el asunto", 5, 120),
                ValidadorCampos.texto(descripcion, "la descripción", 20, 2000));
        long abiertas = pqrDao.listarPorUsuario(usuarioId).stream()
                .filter(existente -> existente.getEstado() != EstadoPqr.RESPONDIDA)
                .count();
        if (abiertas >= MAXIMO_ABIERTAS) {
            throw new ValidacionException("Ya tiene " + MAXIMO_ABIERTAS + " solicitudes sin respuesta. Espere la "
                    + "respuesta antes de enviar otra.");
        }
        return pqrDao.insertar(pqr);
    }

    /**
     * @param usuarioId persona con sesión
     * @return sus PQR, de la más reciente a la más antigua
     */
    public List<Pqr> listarDelUsuario(long usuarioId) {
        return pqrDao.listarPorUsuario(usuarioId);
    }

    /**
     * Lista las PQR para el administrador.
     *
     * @param estado RADICADA, EN_PROCESO o RESPONDIDA; vacío para todas
     * @return PQR con el nombre de quien la radicó
     */
    public List<Pqr> listarParaAdministrador(String estado) {
        EstadoPqr filtro = estado == null || estado.isBlank() ? null
                : ValidadorCampos.opcion(estado, EstadoPqr.class, "el estado");
        return pqrDao.listarPorEstado(filtro);
    }

    /**
     * Abre una PQR en la vista del administrador y la marca como en proceso si era nueva.
     *
     * @param pqrId PQR
     * @return PQR con los datos de quien la radicó
     */
    public Pqr abrir(long pqrId) {
        Pqr pqr = buscar(pqrId);
        if (pqr.getEstado() == EstadoPqr.RADICADA) {
            pqr.marcarEnProceso();
            pqrDao.actualizar(pqr);
        }
        return pqr;
    }

    /**
     * Registra la respuesta del administrador. Una PQR respondida no se vuelve a responder.
     *
     * @param pqrId PQR
     * @param administradorId administrador con sesión
     * @param respuesta texto de 10 a 2000 caracteres
     * @return PQR respondida
     */
    public Pqr responder(long pqrId, long administradorId, String respuesta) {
        String texto = ValidadorCampos.texto(respuesta, "la respuesta", 10, 2000);
        Pqr pqr = buscar(pqrId);
        if (pqr.getEstado() == EstadoPqr.RESPONDIDA) {
            throw new ValidacionException("Esta solicitud ya fue respondida.");
        }
        pqr.responder(texto, administradorId);
        pqrDao.actualizar(pqr);
        return pqr;
    }

    private Pqr buscar(long pqrId) {
        return pqrDao.buscarPorId(pqrId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos esa solicitud."));
    }
}
