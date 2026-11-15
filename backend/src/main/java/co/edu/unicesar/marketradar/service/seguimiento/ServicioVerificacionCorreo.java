package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.correo.CorreoNoEnviadoException;
import co.edu.unicesar.marketradar.correo.MensajeCorreo;
import co.edu.unicesar.marketradar.correo.PropiedadesCorreo;
import co.edu.unicesar.marketradar.correo.ResultadoEnvio;
import co.edu.unicesar.marketradar.correo.TransporteCorreo;
import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.dao.VerificacionCorreoDao;
import co.edu.unicesar.marketradar.domain.Usuario;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ServicioNoDisponibleException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Verifica que el correo de la cuenta es de la persona: le manda un enlace de un solo uso que vence en 24 horas. Los
 * avisos de Seguimientos solo se mandan a un correo verificado.
 *
 * <p>Cada enlace guarda en qué quedó su envío: pendiente, aceptado por el servicio de correo o fallido. "Aceptado" no
 * quiere decir que llegó: el correo puede tardar o caer en spam, y así se le dice a la persona. Si el servicio falla de
 * forma pasajera se reintenta una vez enseguida; un fallo no cuenta para el límite de enlaces por hora, pero sí para el
 * tope de solicitudes, así nadie puede usar la página para mandar correos sin control.</p>
 */
@Service
public class ServicioVerificacionCorreo {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioVerificacionCorreo.class);

    /** Enlaces que se pueden recibir en una hora. */
    static final int ENLACES_POR_HORA = 3;
    /** Solicitudes por hora contando las que fallaron. */
    static final int SOLICITUDES_POR_HORA = 6;
    static final int HORAS_VIGENCIA = 24;
    /** Intentos de mandar el mismo enlace (el segundo solo si el primer fallo fue pasajero). */
    static final int INTENTOS = 2;
    /** Tras un enlace aceptado, cuánto esperar antes de mandar otro (evita repetidos por clics seguidos). */
    static final Duration ESPERA_ENTRE_ENLACES = Duration.ofMinutes(1);
    /** Un envío PENDIENTE más viejo que esto quedó colgado (por ejemplo, un reinicio). */
    static final Duration ENVIO_COLGADO = Duration.ofMinutes(2);

    private final VerificacionCorreoDao verificacionDao;
    private final UsuarioDao usuarioDao;
    private final TransporteCorreo transporte;
    private final PropiedadesCorreo correo;
    private final Clock reloj;
    private final Pausa pausa;

    /** Espera entre intentos; en las pruebas no espera. */
    @FunctionalInterface
    interface Pausa {
        void esperar(Duration duracion);
    }

    @Autowired
    public ServicioVerificacionCorreo(VerificacionCorreoDao verificacionDao, UsuarioDao usuarioDao,
                                      TransporteCorreo transporte, PropiedadesCorreo correo) {
        this(verificacionDao, usuarioDao, transporte, correo, Clock.systemUTC(), ServicioVerificacionCorreo::dormir);
    }

    ServicioVerificacionCorreo(VerificacionCorreoDao verificacionDao, UsuarioDao usuarioDao,
                               TransporteCorreo transporte, PropiedadesCorreo correo, Clock reloj, Pausa pausa) {
        this.verificacionDao = verificacionDao;
        this.usuarioDao = usuarioDao;
        this.transporte = transporte;
        this.correo = correo;
        this.reloj = reloj;
        this.pausa = pausa;
    }

    /**
     * Estado del correo de la persona.
     *
     * @param correo correo de la cuenta
     * @param verificado si ya lo verificó
     * @param envioConfigurado si el servidor puede mandar correos
     * @param ultimoEnvio el último enlace pedido, o {@code null}
     */
    public record EstadoCorreo(String correo, boolean verificado, boolean envioConfigurado,
                               VerificacionCorreoDao.Envio ultimoEnvio) {

        /** Sin datos del último envío. */
        public EstadoCorreo(String correo, boolean verificado, boolean envioConfigurado) {
            this(correo, verificado, envioConfigurado, null);
        }
    }

    /**
     * Resultado de pedir un enlace: el servicio de correo lo aceptó.
     *
     * @param proveedor quién lo aceptó
     * @param aceptadoEn cuándo
     * @param venceEn hasta cuándo sirve el enlace
     * @param mensaje qué decirle a la persona
     */
    public record Solicitud(String proveedor, OffsetDateTime aceptadoEn, OffsetDateTime venceEn, String mensaje) {
    }

    /**
     * @param usuarioId persona
     * @return su correo, si está verificado y en qué quedó el último enlace
     */
    public EstadoCorreo estado(long usuarioId) {
        Usuario usuario = usuario(usuarioId);
        return new EstadoCorreo(usuario.getCorreo(), verificacionDao.verificadoEn(usuarioId).isPresent(),
                transporte.estaConfigurado(), verificacionDao.ultimoEnvio(usuarioId).orElse(null));
    }

    /**
     * @param usuarioId persona
     * @return {@code true} si verificó su correo
     */
    public boolean estaVerificado(long usuarioId) {
        return verificacionDao.verificadoEn(usuarioId).isPresent();
    }

    /**
     * Manda el enlace de verificación al correo de la cuenta.
     *
     * @param usuarioId persona
     * @return el servicio de correo lo aceptó
     * @throws ServicioNoDisponibleException si no hay correo configurado o el servicio no lo aceptó
     * @throws ValidacionException si ya está verificado, se pasó del límite o hay un envío en curso
     */
    public Solicitud solicitar(long usuarioId) {
        Usuario usuario = usuario(usuarioId);
        if (verificacionDao.verificadoEn(usuarioId).isPresent()) {
            throw new ValidacionException("Su correo ya está verificado. No tiene que hacer nada más.");
        }
        if (!transporte.estaConfigurado()) {
            throw new ServicioNoDisponibleException("Este servidor todavía no tiene configurado el envío de correos, "
                    + "así que no podemos mandarle el enlace. Avise al administrador.");
        }
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        verificacionDao.liberarEnviosColgados(usuarioId, ahora.minus(ENVIO_COLGADO));
        if (verificacionDao.contarEnviados(usuarioId, ahora.minusHours(1)) >= ENLACES_POR_HORA
                || verificacionDao.contarSolicitudes(usuarioId, ahora.minusHours(1)) >= SOLICITUDES_POR_HORA) {
            throw new ValidacionException("Ya pidió varios enlaces en la última hora. Revise su correo, también la "
                    + "carpeta de spam, o pida otro más tarde.");
        }
        VerificacionCorreoDao.Envio ultimo = verificacionDao.ultimoEnvio(usuarioId).orElse(null);
        if (ultimo != null && ultimo.estado() == VerificacionCorreoDao.EstadoEnvio.ACEPTADO && !ultimo.usado()
                && ultimo.aceptadoEn() != null && ultimo.aceptadoEn().isAfter(ahora.minus(ESPERA_ENTRE_ENLACES))) {
            throw new ValidacionException("Le mandamos un enlace hace menos de un minuto. Espere a que llegue (revise "
                    + "también la carpeta de spam) antes de pedir otro.");
        }
        String token = Tokens.nuevo();
        String hash = Tokens.sha256(token);
        OffsetDateTime vence = ahora.plusHours(HORAS_VIGENCIA);
        if (!verificacionDao.guardarToken(hash, usuarioId, usuario.getCorreo(), vence)) {
            throw new ValidacionException("Ya estamos mandando un enlace a su correo. Espere un momento.");
        }
        MensajeCorreo mensaje = PlantillasCorreo.verificacion(usuario.getCorreo(), usuario.getNombres(),
                correo.urlPublica() + "/verificar-correo.html#token=" + token).conClave("verificacion-" + hash);
        CorreoNoEnviadoException ultimoError = null;
        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                ResultadoEnvio resultado = transporte.enviar(mensaje);
                verificacionDao.marcarAceptado(hash, intento, resultado.proveedor(), resultado.idMensaje());
                LOG.info("Enlace de verificación de la cuenta {} aceptado por {} (intento {}, id {})", usuarioId,
                        resultado.proveedor(), intento, resultado.idMensaje());
                return new Solicitud(resultado.proveedor(), OffsetDateTime.now(reloj), vence,
                        "El servicio de correo aceptó el enlace para " + usuario.getCorreo() + ". Puede tardar unos "
                                + "minutos en llegar; si no lo ve, revise la carpeta de spam. Vence en "
                                + HORAS_VIGENCIA + " horas.");
            } catch (CorreoNoEnviadoException e) {
                ultimoError = e;
                LOG.warn("No se pudo mandar el enlace de verificación de la cuenta {} por {} (intento {} de {}, {}): {}",
                        usuarioId, transporte.nombre(), intento, INTENTOS, e.esTransitorio() ? "pasajero" : "definitivo",
                        e.getMessage());
                if (!e.esTransitorio() || intento == INTENTOS) {
                    verificacionDao.marcarFallido(hash, intento, e.getMessage());
                    break;
                }
                pausa.esperar(Duration.ofSeconds(2));
            }
        }
        throw new ServicioNoDisponibleException(ultimoError != null && !ultimoError.esTransitorio()
                ? "El servicio de correo rechazó el envío. El administrador debe revisar la configuración del correo."
                : "No pudimos mandar el correo en este momento. Intente de nuevo en unos minutos.");
    }

    /**
     * Usa el enlace de verificación.
     *
     * @param token clave del enlace
     */
    public void verificar(String token) {
        if (!Tokens.pareceToken(token) || verificacionDao.usarToken(Tokens.sha256(token)).isEmpty()) {
            throw new ValidacionException("El enlace no es válido, ya se usó o venció. Pida uno nuevo en "
                    + "\"Seguimientos\".");
        }
    }

    private Usuario usuario(long usuarioId) {
        return usuarioDao.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos su cuenta."));
    }

    private static void dormir(Duration duracion) {
        try {
            Thread.sleep(duracion.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
