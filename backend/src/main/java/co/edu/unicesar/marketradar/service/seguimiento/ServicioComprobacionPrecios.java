package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.config.PropiedadesSeguimientos;
import co.edu.unicesar.marketradar.dao.AvisoSeguimientoDao;
import co.edu.unicesar.marketradar.dao.EjecucionTareaDao;
import co.edu.unicesar.marketradar.dao.SeguimientoDao;
import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
import co.edu.unicesar.marketradar.domain.EvaluadorPrecio;
import co.edu.unicesar.marketradar.domain.NormalizadorEspecificaciones;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.Seguimiento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Una ronda de comprobación: toma los seguimientos que ya toca revisar, vuelve a consultar cada tienda una sola vez por
 * búsqueda, decide con {@link EvaluadorPrecio} si hubo una bajada de verdad y deja los avisos listos para mandar. Corre
 * en el servidor, sin depender de que la persona tenga la página abierta, y nunca dos rondas a la vez.
 */
@Service
public class ServicioComprobacionPrecios {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioComprobacionPrecios.class);

    /** Minutos que un seguimiento queda apartado mientras se comprueba. */
    static final int MINUTOS_APARTADO = 10;
    /** Si se acabó el cupo de consultas de la ronda, lo que faltó se revisa en este tiempo. */
    static final Duration ESPERA_SIN_CUPO = Duration.ofMinutes(30);

    private final SeguimientoDao seguimientoDao;
    private final AvisoSeguimientoDao avisoDao;
    private final ComprobadorOfertas comprobador;
    private final ServicioEnvioAvisos envio;
    private final PropiedadesSeguimientos propiedades;
    private final Clock reloj;
    private final EjecucionTareaDao ejecuciones;
    private final AtomicBoolean enCurso = new AtomicBoolean();

    /** Nombre de la tarea en {@code ejecucion_tarea} y en el bloqueo. */
    public static final String TAREA = "seguimientos";
    /**
     * Tiempo máximo de consultas a las tiendas en una ronda. Lo que no alcanza se pospone: así una tienda lenta no deja
     * colgada la llamada del cron (cron-job.org corta a los 30 segundos).
     */
    static final Duration PRESUPUESTO_RONDA = Duration.ofSeconds(20);

    @Autowired
    public ServicioComprobacionPrecios(SeguimientoDao seguimientoDao, AvisoSeguimientoDao avisoDao,
                                       ComprobadorOfertas comprobador, ServicioEnvioAvisos envio,
                                       PropiedadesSeguimientos propiedades, EjecucionTareaDao ejecuciones) {
        this(seguimientoDao, avisoDao, comprobador, envio, propiedades, Clock.systemUTC(), ejecuciones);
    }

    ServicioComprobacionPrecios(SeguimientoDao seguimientoDao, AvisoSeguimientoDao avisoDao,
                                ComprobadorOfertas comprobador, ServicioEnvioAvisos envio,
                                PropiedadesSeguimientos propiedades, Clock reloj) {
        this(seguimientoDao, avisoDao, comprobador, envio, propiedades, reloj, EjecucionTareaDao.SIN_REGISTRO);
    }

    ServicioComprobacionPrecios(SeguimientoDao seguimientoDao, AvisoSeguimientoDao avisoDao,
                                ComprobadorOfertas comprobador, ServicioEnvioAvisos envio,
                                PropiedadesSeguimientos propiedades, Clock reloj, EjecucionTareaDao ejecuciones) {
        this.ejecuciones = ejecuciones;
        this.seguimientoDao = seguimientoDao;
        this.avisoDao = avisoDao;
        this.comprobador = comprobador;
        this.envio = envio;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /**
     * Qué hizo una ronda.
     *
     * @param ejecutada {@code false} si ya había otra ronda en curso
     * @param revisados seguimientos comprobados
     * @param consultas consultas hechas a las tiendas
     * @param pospuestos seguimientos que quedaron para después por el cupo de consultas
     * @param avisosNuevos avisos de precio creados
     * @param correosEnviados correos que el servidor de correo aceptó
     * @param envioConfigurado si hay servidor de correo
     */
    public record Resumen(boolean ejecutada, int revisados, int consultas, int pospuestos, int avisosNuevos,
                          int correosEnviados, boolean envioConfigurado) {
    }

    /**
     * Ejecuta una ronda disparada por la tarea interna del servidor.
     *
     * @return resumen
     */
    public Resumen ejecutarRonda() {
        return ejecutarRonda(EjecucionTareaDao.Origen.INTERNA);
    }

    /**
     * Ejecuta una ronda, salvo que ya haya otra en curso (en este servidor o en otra copia). Cada ronda, también la que
     * se omite, queda registrada en {@code ejecucion_tarea}.
     *
     * @param origen quién la disparó
     * @return resumen
     */
    public Resumen ejecutarRonda(EjecucionTareaDao.Origen origen) {
        if (!enCurso.compareAndSet(false, true)) {
            registrarOmitida(origen);
            return new Resumen(false, 0, 0, 0, 0, 0, envio.envioConfigurado());
        }
        try {
            Optional<Resumen> hecha = ejecuciones.conBloqueo(TAREA, () -> rondaRegistrada(origen));
            if (hecha.isEmpty()) {
                registrarOmitida(origen);
                return new Resumen(false, 0, 0, 0, 0, 0, envio.envioConfigurado());
            }
            return hecha.get();
        } finally {
            enCurso.set(false);
        }
    }

    private void registrarOmitida(EjecucionTareaDao.Origen origen) {
        try {
            ejecuciones.omitida(TAREA, origen, "Ya había otra ronda en curso");
        } catch (RuntimeException e) {
            LOG.warn("No se pudo registrar la ronda omitida: {}", e.getMessage());
        }
    }

    private Resumen rondaRegistrada(EjecucionTareaDao.Origen origen) {
        long id = ejecuciones.iniciar(TAREA, origen);
        try {
            Resumen resumen = ronda();
            ejecuciones.terminar(id, EjecucionTareaDao.Estado.TERMINADA, resumen.revisados(), resumen.consultas(),
                    resumen.pospuestos(), resumen.avisosNuevos(), resumen.correosEnviados(),
                    resumen.envioConfigurado() ? null : "Sin servicio de correo configurado");
            return resumen;
        } catch (RuntimeException e) {
            ejecuciones.terminar(id, EjecucionTareaDao.Estado.FALLIDA, 0, 0, 0, 0, 0, e.getClass().getSimpleName());
            throw e;
        }
    }

    private Resumen ronda() {
        {
            List<SeguimientoDao.Pendiente> pendientes = seguimientoDao.tomarPendientes(
                    propiedades.maxSeguimientosPorRonda(), MINUTOS_APARTADO);
            Map<String, List<SeguimientoDao.Pendiente>> porBusqueda = new LinkedHashMap<>();
            for (SeguimientoDao.Pendiente pendiente : pendientes) {
                porBusqueda.computeIfAbsent(pendiente.codigoMarketplace() + "|" + termino(pendiente),
                        clave -> new ArrayList<>()).add(pendiente);
            }
            int consultas = 0;
            int pospuestos = 0;
            int revisados = 0;
            int avisos = 0;
            OffsetDateTime ahora = OffsetDateTime.now(reloj);
            java.time.Instant limite = reloj.instant().plus(PRESUPUESTO_RONDA);
            for (List<SeguimientoDao.Pendiente> grupo : porBusqueda.values()) {
                if (consultas >= propiedades.maxConsultasPorRonda() || reloj.instant().isAfter(limite)) {
                    for (SeguimientoDao.Pendiente pendiente : grupo) {
                        seguimientoDao.posponer(pendiente.seguimiento().getUsuarioId(),
                                pendiente.seguimiento().getOfertaId(), ahora.plus(ESPERA_SIN_CUPO));
                        pospuestos++;
                    }
                    continue;
                }
                consultas++;
                SeguimientoDao.Pendiente primero = grupo.get(0);
                List<OfertaNormalizada> encontradas = null;
                try {
                    encontradas = comprobador.consultar(primero.codigoMarketplace(), termino(primero));
                } catch (RuntimeException e) {
                    LOG.warn("No se pudo comprobar {} \"{}\": {}", primero.codigoMarketplace(), termino(primero),
                            e.getMessage());
                }
                for (SeguimientoDao.Pendiente pendiente : grupo) {
                    revisados++;
                    if (procesar(pendiente, encontradas, ahora)) {
                        avisos++;
                    }
                }
            }
            int enviados = envio.enviarPendientes(50);
            if (!pendientes.isEmpty()) {
                LOG.info("Ronda de seguimientos: {} revisados, {} consultas, {} pospuestos, {} avisos nuevos, {} correos "
                        + "enviados", revisados, consultas, pospuestos, avisos, enviados);
            }
            return new Resumen(true, revisados, consultas, pospuestos, avisos, enviados, envio.envioConfigurado());
        }
    }

    /**
     * Comprueba un seguimiento con lo que devolvió la tienda.
     *
     * @return {@code true} si se creó un aviso nuevo
     */
    boolean procesar(SeguimientoDao.Pendiente pendiente, List<OfertaNormalizada> encontradas, OffsetDateTime ahora) {
        Seguimiento s = pendiente.seguimiento();
        s.setUltimaComprobacion(ahora);
        if (encontradas == null) {
            s.setResultado(Seguimiento.Resultado.ERROR_TIENDA);
            s.setFallosConsecutivos(s.getFallosConsecutivos() + 1);
            // Si la tienda sigue fallando, se espera cada vez más, sin pasar de la frecuencia normal.
            long espera = Math.min(propiedades.frecuenciaMinutos(), 30L << Math.min(s.getFallosConsecutivos() - 1, 5));
            s.setProximaComprobacion(ahora.plusMinutes(espera));
            seguimientoDao.guardarComprobacion(s);
            return false;
        }
        Optional<OfertaNormalizada> misma = encontradas.stream()
                .filter(o -> o.getIdExterno().equals(pendiente.oferta().getIdExterno())).findFirst();
        String variante = misma.map(o -> NormalizadorEspecificaciones.claveVariante(o.getTitulo(), o.getDetalles()))
                .orElse(null);
        EvaluadorPrecio.Decision decision = EvaluadorPrecio.evaluar(s, misma, variante,
                propiedades.bajaMinimaPorcentaje());

        boolean avisoNuevo = false;
        if (decision.evento().isPresent()) {
            avisoNuevo = crearAviso(pendiente, decision.evento().get(), ahora);
        }
        s.setResultado(decision.resultado());
        s.setObjetivoAvisado(decision.objetivoAvisado());
        s.setFallosConsecutivos(decision.resultado() == Seguimiento.Resultado.NO_ENCONTRADA
                ? s.getFallosConsecutivos() + 1 : 0);
        if (decision.actualizarReferencia()) {
            OfertaNormalizada oferta = misma.orElseThrow();
            s.setPrecioReferencia(oferta.getPrecio());
            s.setMonedaReferencia(oferta.getMoneda());
            s.setPrecioOriginalReferencia(oferta.getPrecioOriginal());
            s.setMonedaOriginalReferencia(oferta.getMonedaOriginal());
            s.setVendedorReferencia(oferta.getVendedor());
            s.setCondicionReferencia(oferta.getCondicion());
            s.setVarianteReferencia(variante);
            s.setReferenciaDesde(ahora);
            s.setUltimoPrecio(oferta.getPrecio());
        }
        s.setProximaComprobacion(ahora.plusMinutes(propiedades.frecuenciaMinutos()));
        if (!seguimientoDao.guardarComprobacion(s)) {
            LOG.info("El seguimiento {}/{} cambió mientras se comprobaba; se revisa en la próxima ronda",
                    s.getUsuarioId(), s.getOfertaId());
        }
        return avisoNuevo;
    }

    /**
     * Guarda el aviso con una clave única por evento: la referencia de la que se partió y el precio nuevo. Si la ronda
     * se repite (por un reinicio o porque el guardado falló), la clave es la misma y no se crea otro aviso.
     */
    private boolean crearAviso(SeguimientoDao.Pendiente pendiente, EvaluadorPrecio.Evento evento, OffsetDateTime ahora) {
        Seguimiento s = pendiente.seguimiento();
        if (!pendiente.correoVerificado()) {
            LOG.info("El seguimiento {}/{} tuvo un evento de precio, pero la persona no ha verificado su correo",
                    s.getUsuarioId(), s.getOfertaId());
            return false;
        }
        AvisoSeguimiento aviso = new AvisoSeguimiento();
        aviso.setUsuarioId(s.getUsuarioId());
        aviso.setOfertaId(s.getOfertaId());
        aviso.setTipo(evento.tipo() == Seguimiento.TipoAviso.PRECIO_OBJETIVO
                ? AvisoSeguimiento.Tipo.PRECIO_OBJETIVO : AvisoSeguimiento.Tipo.BAJA_PRECIO);
        String desde = s.getReferenciaDesde() == null ? "0" : String.valueOf(s.getReferenciaDesde().toEpochSecond());
        String objetivo = s.getPrecioObjetivo() == null ? "" : ":" + s.getPrecioObjetivo().stripTrailingZeros().toPlainString();
        aviso.setClaveEvento(aviso.getTipo().name().toLowerCase(java.util.Locale.ROOT) + ":" + s.getUsuarioId() + ":"
                + s.getOfertaId() + ":" + desde + ":" + evento.precioActual().stripTrailingZeros().toPlainString()
                + objetivo);
        aviso.setCorreoDestino(pendiente.correo());
        aviso.setPrecioAnterior(evento.precioAnterior());
        aviso.setPrecioActual(evento.precioActual());
        aviso.setMoneda(evento.moneda());
        aviso.setComprobadoEn(ahora);
        return avisoDao.insertarSiEsNuevo(aviso).isPresent();
    }

    /** Lo que se busca para encontrar la oferta: lo que buscó la persona o, si no se sabe, el título. */
    static String termino(SeguimientoDao.Pendiente pendiente) {
        String termino = pendiente.seguimiento().getTerminoBusqueda();
        if (termino != null && termino.strip().length() >= 2) {
            return termino.strip();
        }
        String titulo = pendiente.oferta().getTitulo().strip().replaceAll("\\s+", " ");
        if (titulo.length() <= 100) {
            return titulo;
        }
        int corte = titulo.lastIndexOf(' ', 100);
        return titulo.substring(0, corte > 10 ? corte : 100);
    }
}
