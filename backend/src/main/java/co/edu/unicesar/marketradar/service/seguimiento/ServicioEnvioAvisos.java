package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.config.PropiedadesSeguimientos;
import co.edu.unicesar.marketradar.correo.CorreoNoEnviadoException;
import co.edu.unicesar.marketradar.correo.MensajeCorreo;
import co.edu.unicesar.marketradar.correo.PropiedadesCorreo;
import co.edu.unicesar.marketradar.correo.ResultadoEnvio;
import co.edu.unicesar.marketradar.correo.TransporteCorreo;
import co.edu.unicesar.marketradar.dao.AvisoSeguimientoDao;
import co.edu.unicesar.marketradar.dao.SeguimientoDao;
import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
import co.edu.unicesar.marketradar.domain.Seguimiento;
import co.edu.unicesar.marketradar.service.ServicioBusqueda;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Manda los correos pendientes de Seguimientos y guarda qué pasó con cada uno. Un correo solo queda como
 * {@code ENVIADO} si el servicio de correo lo aceptó (eso no confirma que haya llegado a la bandeja). Sin servidor configurado, los avisos quedan pendientes (y vencen a
 * las 48 horas); si el servidor falla, se reintenta con espera creciente hasta el máximo de intentos.
 */
@Service
public class ServicioEnvioAvisos {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioEnvioAvisos.class);

    /** Minutos que un aviso queda apartado mientras se manda, para que otra ronda no lo mande a la vez. */
    static final int MINUTOS_APARTADO = 10;

    private final AvisoSeguimientoDao avisoDao;
    private final SeguimientoDao seguimientoDao;
    private final TransporteCorreo transporte;
    private final PropiedadesCorreo correo;
    private final PropiedadesSeguimientos propiedades;
    private final Clock reloj;

    @Autowired
    public ServicioEnvioAvisos(AvisoSeguimientoDao avisoDao, SeguimientoDao seguimientoDao,
                               TransporteCorreo transporte, PropiedadesCorreo correo,
                               PropiedadesSeguimientos propiedades) {
        this(avisoDao, seguimientoDao, transporte, correo, propiedades, Clock.systemUTC());
    }

    ServicioEnvioAvisos(AvisoSeguimientoDao avisoDao, SeguimientoDao seguimientoDao, TransporteCorreo transporte,
                        PropiedadesCorreo correo, PropiedadesSeguimientos propiedades, Clock reloj) {
        this.avisoDao = avisoDao;
        this.seguimientoDao = seguimientoDao;
        this.transporte = transporte;
        this.correo = correo;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    /** @return {@code true} si el servidor puede mandar correos */
    public boolean envioConfigurado() {
        return transporte.estaConfigurado();
    }

    /**
     * Manda enseguida un aviso (por ejemplo, la confirmación de un seguimiento nuevo).
     *
     * @param id aviso
     * @return en qué estado quedó
     */
    public AvisoSeguimiento.Estado enviarAhora(long id) {
        return avisoDao.tomar(id, MINUTOS_APARTADO).map(this::procesar).orElse(AvisoSeguimiento.Estado.DESCARTADO);
    }

    /**
     * Manda los avisos pendientes cuyo intento ya toca.
     *
     * @param limite cuántos como máximo
     * @return cuántos se mandaron
     */
    public int enviarPendientes(int limite) {
        return enviarLote(limite).aceptados();
    }

    /**
     * Lo que pasó con los correos de una tanda.
     *
     * @param aceptados correos que el proveedor aceptó (no confirma que lleguen a la bandeja)
     * @param rechazados intentos que el proveedor rechazó (pasajeros o definitivos)
     */
    public record Lote(int aceptados, int rechazados) {
    }

    /**
     * Manda los avisos pendientes cuyo intento ya toca y cuenta aceptados y rechazados por separado.
     *
     * @param limite cuántos como máximo
     * @return aceptados y rechazados
     */
    public Lote enviarLote(int limite) {
        List<AvisoSeguimientoDao.Pendiente> pendientes = avisoDao.tomarPendientes(limite, MINUTOS_APARTADO);
        int aceptados = 0;
        int rechazados = 0;
        for (AvisoSeguimientoDao.Pendiente pendiente : pendientes) {
            Intento intento = intentar(pendiente);
            if (intento.estado() == AvisoSeguimiento.Estado.ENVIADO) {
                aceptados++;
            }
            if (intento.rechazado()) {
                rechazados++;
            }
        }
        return new Lote(aceptados, rechazados);
    }

    /** En qué quedó un aviso y si el proveedor de correo rechazó el envío. */
    private record Intento(AvisoSeguimiento.Estado estado, boolean rechazado) {
    }

    AvisoSeguimiento.Estado procesar(AvisoSeguimientoDao.Pendiente pendiente) {
        return intentar(pendiente).estado();
    }

    private Intento intentar(AvisoSeguimientoDao.Pendiente pendiente) {
        AvisoSeguimiento aviso = pendiente.aviso();
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        if (aviso.getCreadoEn() != null
                && aviso.getCreadoEn().isBefore(ahora.minusHours(propiedades.horasVigenciaAviso()))) {
            avisoDao.descartar(aviso.getId(), "Venció sin poder mandarse; el precio pudo haber cambiado");
            return new Intento(AvisoSeguimiento.Estado.DESCARTADO, false);
        }
        boolean confirmacion = aviso.getTipo() == AvisoSeguimiento.Tipo.CONFIRMACION;
        if (confirmacion ? "CANCELADO".equals(pendiente.estadoSeguimiento())
                : !"ACTIVO".equals(pendiente.estadoSeguimiento())) {
            avisoDao.descartar(aviso.getId(), "El seguimiento está pausado o cancelado");
            return new Intento(AvisoSeguimiento.Estado.DESCARTADO, false);
        }
        if (!pendiente.correoVerificado() || pendiente.correoActual() == null
                || !pendiente.correoActual().equalsIgnoreCase(aviso.getCorreoDestino())) {
            avisoDao.descartar(aviso.getId(), "El correo de la cuenta no está verificado");
            return new Intento(AvisoSeguimiento.Estado.DESCARTADO, false);
        }
        if (!transporte.estaConfigurado()) {
            // No cuenta como intento: cuando se configure el correo, se manda (si no venció).
            avisoDao.registrarFallo(aviso.getId(), "El servidor no tiene configurado el envío de correos", false,
                    ahora.plusHours(1));
            return new Intento(AvisoSeguimiento.Estado.PENDIENTE, false);
        }
        // Justo antes de mandar se mira otra vez el estado: si la persona pausó o canceló mientras esta ronda ya tenía
        // el aviso apartado, no se manda.
        Optional<Seguimiento.Estado> estadoAhora = seguimientoDao.buscar(aviso.getUsuarioId(), aviso.getOfertaId())
                .map(Seguimiento::getEstado);
        if (estadoAhora.isPresent() && (estadoAhora.get() == Seguimiento.Estado.CANCELADO
                || !confirmacion && estadoAhora.get() != Seguimiento.Estado.ACTIVO)) {
            avisoDao.descartar(aviso.getId(), "El seguimiento está pausado o cancelado");
            return new Intento(AvisoSeguimiento.Estado.DESCARTADO, false);
        }
        String token = seguimientoDao.asegurarToken(aviso.getUsuarioId(), aviso.getOfertaId(), Tokens.nuevo());
        MensajeCorreo mensaje = mensaje(pendiente, token).conClave("aviso-" + aviso.getClaveEvento().hashCode() + "-"
                + aviso.getId());
        try {
            ResultadoEnvio resultado = transporte.enviar(mensaje);
            avisoDao.marcarAceptado(aviso.getId(), resultado);
            LOG.info("Aviso {} ({}) aceptado por {} para {} (id {})", aviso.getId(), aviso.getTipo(),
                    resultado.proveedor(), mensaje.paraEnRegistro(), resultado.idMensaje());
            return new Intento(AvisoSeguimiento.Estado.ENVIADO, false);
        } catch (CorreoNoEnviadoException e) {
            int intentos = aviso.getIntentos() + 1;
            // Un rechazo definitivo (clave inválida, remitente sin verificar, dirección inválida) no mejora reintentando.
            boolean ultimo = !e.esTransitorio() || intentos >= propiedades.maxIntentosCorreo();
            LOG.warn("No se pudo mandar el aviso {} por {} (intento {} de {}, {}): {}", aviso.getId(),
                    transporte.nombre(), intentos, propiedades.maxIntentosCorreo(),
                    e.esTransitorio() ? "pasajero" : "definitivo", e.getMessage());
            avisoDao.registrarFallo(aviso.getId(), e.getMessage(), true,
                    ultimo ? null : ahora.plus(Duration.ofMinutes(5L << Math.min(intentos, 6))));
            return new Intento(ultimo ? AvisoSeguimiento.Estado.FALLIDO : AvisoSeguimiento.Estado.PENDIENTE, true);
        }
    }

    private MensajeCorreo mensaje(AvisoSeguimientoDao.Pendiente pendiente, String token) {
        AvisoSeguimiento aviso = pendiente.aviso();
        String tienda = "googleshopping".equals(pendiente.codigoMarketplace()) && pendiente.vendedor() != null
                ? pendiente.vendedor() : ServicioBusqueda.nombreVisible(pendiente.codigoMarketplace());
        PlantillasCorreo.DatosOferta oferta = new PlantillasCorreo.DatosOferta(pendiente.tituloOferta(), tienda,
                pendiente.urlOferta());
        String seguimientos = correo.urlPublica() + "/usuario.html#seguimientos";
        String cancelar = correo.urlPublica() + "/cancelar-seguimiento.html#token=" + token;
        String nombre = pendiente.nombre() == null ? "" : pendiente.nombre();
        String moneda = aviso.getMoneda() == null ? "COP" : aviso.getMoneda();
        return switch (aviso.getTipo()) {
            case CONFIRMACION -> PlantillasCorreo.confirmacion(aviso.getCorreoDestino(), nombre, oferta,
                    aviso.getPrecioActual(), moneda, queAvisamos(pendiente.tipoAviso(), pendiente.precioObjetivo(), moneda),
                    propiedades.frecuenciaMinutos(), seguimientos, cancelar);
            case BAJA_PRECIO, PRECIO_OBJETIVO -> PlantillasCorreo.avisoPrecio(aviso.getCorreoDestino(), nombre,
                    aviso.getTipo() == AvisoSeguimiento.Tipo.PRECIO_OBJETIVO, pendiente.precioObjetivo(), oferta,
                    aviso.getPrecioAnterior(), aviso.getPrecioActual(), moneda,
                    Optional.ofNullable(aviso.getComprobadoEn()).orElse(aviso.getCreadoEn()), seguimientos, cancelar);
        };
    }

    private String queAvisamos(String tipoAviso, BigDecimal objetivo, String moneda) {
        if ("PRECIO_OBJETIVO".equals(tipoAviso) && objetivo != null) {
            return "cuando cueste " + PlantillasCorreo.precio(objetivo, moneda) + " o menos";
        }
        return "cuando baje de precio (al menos " + propiedades.bajaMinimaPorcentaje().stripTrailingZeros()
                .toPlainString().replace('.', ',') + " %)";
    }
}
