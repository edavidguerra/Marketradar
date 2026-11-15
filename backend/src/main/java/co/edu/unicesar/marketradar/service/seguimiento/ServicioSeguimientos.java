package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.config.PropiedadesSeguimientos;
import co.edu.unicesar.marketradar.dao.AvisoSeguimientoDao;
import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.dao.SeguimientoDao;
import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
import co.edu.unicesar.marketradar.domain.NormalizadorEspecificaciones;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.Seguimiento;
import co.edu.unicesar.marketradar.domain.Usuario;
import co.edu.unicesar.marketradar.exception.CorreoSinVerificarException;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * "Seguir producto" y "Seguimientos": cada seguimiento es de la persona con sesión y de una oferta concreta. Al
 * activarlo se guarda el precio, el vendedor, la condición y la variante de esa oferta, y se manda la confirmación al
 * correo verificado.
 */
@Service
public class ServicioSeguimientos {

    static final BigDecimal PRECIO_MAXIMO = new BigDecimal("100000000000");

    private final SeguimientoDao seguimientoDao;
    private final AvisoSeguimientoDao avisoDao;
    private final OfertaDao ofertaDao;
    private final UsuarioDao usuarioDao;
    private final ServicioVerificacionCorreo verificacion;
    private final ServicioEnvioAvisos envio;
    private final PropiedadesSeguimientos propiedades;

    public ServicioSeguimientos(SeguimientoDao seguimientoDao, AvisoSeguimientoDao avisoDao, OfertaDao ofertaDao,
                                UsuarioDao usuarioDao, ServicioVerificacionCorreo verificacion,
                                ServicioEnvioAvisos envio, PropiedadesSeguimientos propiedades) {
        this.seguimientoDao = seguimientoDao;
        this.avisoDao = avisoDao;
        this.ofertaDao = ofertaDao;
        this.usuarioDao = usuarioDao;
        this.verificacion = verificacion;
        this.envio = envio;
        this.propiedades = propiedades;
    }

    /**
     * Un seguimiento con su oferta y sus últimos correos.
     *
     * @param detalle seguimiento y oferta
     * @param avisos últimos correos, del más reciente al más antiguo
     */
    public record Vista(SeguimientoDao.Detalle detalle, List<AvisoSeguimiento> avisos) {
    }

    /**
     * Resultado de activar un seguimiento.
     *
     * @param vista el seguimiento
     * @param confirmacion en qué quedó el correo de confirmación
     */
    public record Activacion(Vista vista, AvisoSeguimiento.Estado confirmacion) {
    }

    /**
     * @param usuarioId persona
     * @return sus seguimientos activos y pausados
     */
    public List<Vista> listar(long usuarioId) {
        return seguimientoDao.listarDelUsuario(usuarioId).stream()
                .map(detalle -> new Vista(detalle, avisoDao.recientes(usuarioId, detalle.oferta().getId(), 3)))
                .toList();
    }

    /** @return cada cuánto se comprueba cada oferta, en minutos */
    public int frecuenciaMinutos() {
        return propiedades.frecuenciaMinutos();
    }

    /**
     * Empieza a seguir una oferta, o vuelve a activar un seguimiento que se había cancelado.
     *
     * @param usuarioId persona con sesión
     * @param ofertaId oferta de los resultados
     * @param tipo qué avisar
     * @param precioObjetivo precio objetivo en la moneda de la oferta, solo para {@code PRECIO_OBJETIVO}
     * @param termino lo que buscó la persona, para encontrar la oferta otra vez; opcional
     * @return el seguimiento y en qué quedó el correo de confirmación
     */
    public Activacion seguir(long usuarioId, long ofertaId, Seguimiento.TipoAviso tipo, BigDecimal precioObjetivo,
                             String termino) {
        Usuario usuario = usuarioDao.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos su cuenta."));
        if (!verificacion.estaVerificado(usuarioId)) {
            throw new CorreoSinVerificarException("Para avisarle por correo, primero confirme que " + usuario.getCorreo()
                    + " es suyo. Le mandamos un enlace a ese correo.");
        }
        Oferta oferta = ofertaDao.buscarPorId(ofertaId).orElseThrow(() -> new RecursoNoEncontradoException(
                "Esa oferta ya no está disponible. Búsquela otra vez."));
        Seguimiento.TipoAviso tipoElegido = tipo == null ? Seguimiento.TipoAviso.BAJA_PRECIO : tipo;
        BigDecimal objetivo = validarObjetivo(tipoElegido, precioObjetivo, oferta.getPrecioActual());
        boolean yaLoSigue = seguimientoDao.buscar(usuarioId, ofertaId)
                .filter(s -> s.getEstado() != Seguimiento.Estado.CANCELADO).isPresent();
        if (yaLoSigue) {
            // Ya lo seguía: se guardan las preferencias nuevas sin reiniciar el precio de referencia (eso podría
            // esconder una bajada) y sin mandar otra confirmación.
            return new Activacion(cambiar(usuarioId, ofertaId, tipoElegido, objetivo, Seguimiento.Estado.ACTIVO),
                    AvisoSeguimiento.Estado.DESCARTADO);
        }
        if (seguimientoDao.listarDelUsuario(usuarioId).size() >= propiedades.maxPorUsuario()) {
            throw new ValidacionException("Ya sigue " + propiedades.maxPorUsuario() + " productos, que es el máximo. "
                    + "Deje de seguir alguno para agregar otro.");
        }

        Seguimiento nuevo = new Seguimiento();
        nuevo.setUsuarioId(usuarioId);
        nuevo.setOfertaId(ofertaId);
        nuevo.setTipoAviso(tipoElegido);
        nuevo.setPrecioObjetivo(objetivo);
        nuevo.setPrecioReferencia(oferta.getPrecioActual());
        nuevo.setMonedaReferencia(oferta.getMoneda());
        nuevo.setVendedorReferencia(oferta.getVendedor());
        nuevo.setCondicionReferencia(oferta.getCondicion());
        nuevo.setVarianteReferencia(NormalizadorEspecificaciones.claveVariante(oferta.getTitulo(),
                oferta.listaDetalles()));
        nuevo.setTerminoBusqueda(limpiarTermino(termino));
        nuevo.setProximaComprobacion(OffsetDateTime.now().plusMinutes(propiedades.frecuenciaMinutos()));
        nuevo.setTokenCancelacion(Tokens.nuevo());
        Seguimiento guardado = seguimientoDao.activar(nuevo);

        AvisoSeguimiento confirmacion = new AvisoSeguimiento();
        confirmacion.setUsuarioId(usuarioId);
        confirmacion.setOfertaId(ofertaId);
        confirmacion.setTipo(AvisoSeguimiento.Tipo.CONFIRMACION);
        confirmacion.setClaveEvento("confirmacion:" + usuarioId + ":" + ofertaId + ":v" + guardado.getVersion());
        confirmacion.setCorreoDestino(usuario.getCorreo());
        confirmacion.setPrecioActual(oferta.getPrecioActual());
        confirmacion.setMoneda(oferta.getMoneda());
        confirmacion.setComprobadoEn(oferta.getConsultadoEn());
        AvisoSeguimiento.Estado estado = avisoDao.insertarSiEsNuevo(confirmacion)
                .map(envio::enviarAhora).orElse(AvisoSeguimiento.Estado.DESCARTADO);
        return new Activacion(vista(usuarioId, ofertaId), estado);
    }

    /**
     * Cambia qué se avisa, o pausa o reanuda el seguimiento. Al pausar, los avisos pendientes ya no se mandan.
     *
     * @param usuarioId persona con sesión
     * @param ofertaId oferta
     * @param tipo qué avisar
     * @param precioObjetivo precio objetivo, solo para {@code PRECIO_OBJETIVO}
     * @param estado {@code ACTIVO} o {@code PAUSADO}
     * @return el seguimiento actualizado
     */
    public Vista cambiar(long usuarioId, long ofertaId, Seguimiento.TipoAviso tipo, BigDecimal precioObjetivo,
                         Seguimiento.Estado estado) {
        Seguimiento actual = seguimientoDao.buscar(usuarioId, ofertaId)
                .filter(s -> s.getEstado() != Seguimiento.Estado.CANCELADO)
                .orElseThrow(() -> new RecursoNoEncontradoException("No sigue ese producto."));
        if (estado == Seguimiento.Estado.CANCELADO) {
            throw new ValidacionException("Para dejar de seguir el producto use \"Dejar de seguir\".");
        }
        Seguimiento.TipoAviso tipoElegido = tipo == null ? actual.getTipoAviso() : tipo;
        Seguimiento.Estado estadoElegido = estado == null ? actual.getEstado() : estado;
        BigDecimal objetivo = tipoElegido == Seguimiento.TipoAviso.PRECIO_OBJETIVO
                ? validarObjetivo(tipoElegido, precioObjetivo == null ? actual.getPrecioObjetivo() : precioObjetivo,
                ofertaDao.buscarPorId(ofertaId).map(Oferta::getPrecioActual).orElse(null))
                : null;
        seguimientoDao.actualizarPreferencias(usuarioId, ofertaId, tipoElegido, objetivo, estadoElegido);
        if (estadoElegido == Seguimiento.Estado.PAUSADO) {
            avisoDao.descartarPendientes(usuarioId, ofertaId, "El seguimiento se pausó");
        }
        return vista(usuarioId, ofertaId);
    }

    /**
     * Deja de seguir un producto. Los avisos pendientes ya no se mandan.
     *
     * @param usuarioId persona con sesión
     * @param ofertaId oferta
     */
    public void cancelar(long usuarioId, long ofertaId) {
        if (!seguimientoDao.cancelar(usuarioId, ofertaId)) {
            throw new RecursoNoEncontradoException("No sigue ese producto.");
        }
        avisoDao.descartarPendientes(usuarioId, ofertaId, "El seguimiento se canceló");
    }

    /**
     * Deja de seguir un producto desde el enlace de un correo, sin iniciar sesión.
     *
     * @param token clave del enlace
     * @return nombre del producto, para confirmarlo en la página
     */
    public String cancelarPorEnlace(String token) {
        if (!Tokens.pareceToken(token)) {
            throw new ValidacionException("El enlace para dejar de seguir no es válido.");
        }
        Seguimiento cancelado = seguimientoDao.cancelarPorToken(token).orElseThrow(() -> new ValidacionException(
                "Este enlace no es válido o el seguimiento ya estaba cancelado."));
        avisoDao.descartarPendientes(cancelado.getUsuarioId(), cancelado.getOfertaId(), "El seguimiento se canceló");
        return ofertaDao.buscarPorId(cancelado.getOfertaId()).map(Oferta::getTitulo).orElse("el producto");
    }

    private Vista vista(long usuarioId, long ofertaId) {
        return seguimientoDao.listarDelUsuario(usuarioId).stream()
                .filter(detalle -> detalle.oferta().getId() == ofertaId).findFirst()
                .map(detalle -> new Vista(detalle, avisoDao.recientes(usuarioId, ofertaId, 3)))
                .orElseThrow(() -> new RecursoNoEncontradoException("No sigue ese producto."));
    }

    static BigDecimal validarObjetivo(Seguimiento.TipoAviso tipo, BigDecimal objetivo, BigDecimal precioActual) {
        if (tipo != Seguimiento.TipoAviso.PRECIO_OBJETIVO) {
            return null;
        }
        if (objetivo == null || objetivo.signum() <= 0) {
            throw new ValidacionException("Escriba el precio al que quiere que le avisemos, por ejemplo 500.000.");
        }
        if (objetivo.compareTo(PRECIO_MAXIMO) > 0) {
            throw new ValidacionException("El precio objetivo es demasiado alto. Revise la cifra.");
        }
        if (precioActual != null && objetivo.compareTo(precioActual) >= 0) {
            throw new ValidacionException("El precio objetivo debe ser menor que el precio de hoy. Si quiere saber "
                    + "cuándo baje, elija \"Cuando baje de precio\".");
        }
        return objetivo.setScale(2, RoundingMode.HALF_UP);
    }

    static String limpiarTermino(String termino) {
        if (termino == null || termino.isBlank()) {
            return null;
        }
        String limpio = termino.strip().replaceAll("\\s+", " ");
        return limpio.length() < 2 ? null : limpio.length() > 100 ? limpio.substring(0, 100).strip() : limpio;
    }
}
