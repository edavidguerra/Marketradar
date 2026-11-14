package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.correo.CorreoNoEnviadoException;
import co.edu.unicesar.marketradar.correo.PropiedadesCorreo;
import co.edu.unicesar.marketradar.correo.TransporteCorreo;
import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.dao.VerificacionCorreoDao;
import co.edu.unicesar.marketradar.domain.Usuario;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ServicioNoDisponibleException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Verifica que el correo de la cuenta es de la persona: le manda un enlace de un solo uso que vence en 24 horas. Los
 * avisos de precio solo se mandan a un correo verificado.
 */
@Service
public class ServicioVerificacionCorreo {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioVerificacionCorreo.class);

    /** Enlaces que se pueden pedir en una hora, para que nadie use la página para mandar correos sin control. */
    static final int ENLACES_POR_HORA = 3;
    static final int HORAS_VIGENCIA = 24;

    private final VerificacionCorreoDao verificacionDao;
    private final UsuarioDao usuarioDao;
    private final TransporteCorreo transporte;
    private final PropiedadesCorreo correo;

    public ServicioVerificacionCorreo(VerificacionCorreoDao verificacionDao, UsuarioDao usuarioDao,
                                      TransporteCorreo transporte, PropiedadesCorreo correo) {
        this.verificacionDao = verificacionDao;
        this.usuarioDao = usuarioDao;
        this.transporte = transporte;
        this.correo = correo;
    }

    /**
     * Estado del correo de la persona.
     *
     * @param correo correo de la cuenta
     * @param verificado si ya lo verificó
     * @param envioConfigurado si el servidor puede mandar correos
     */
    public record EstadoCorreo(String correo, boolean verificado, boolean envioConfigurado) {
    }

    /**
     * @param usuarioId persona
     * @return su correo y si está verificado
     */
    public EstadoCorreo estado(long usuarioId) {
        Usuario usuario = usuario(usuarioId);
        return new EstadoCorreo(usuario.getCorreo(), verificacionDao.verificadoEn(usuarioId).isPresent(),
                transporte.estaConfigurado());
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
     */
    public void solicitar(long usuarioId) {
        Usuario usuario = usuario(usuarioId);
        if (verificacionDao.verificadoEn(usuarioId).isPresent()) {
            throw new ValidacionException("Su correo ya está verificado. No tiene que hacer nada más.");
        }
        if (!transporte.estaConfigurado()) {
            throw new ServicioNoDisponibleException("Este servidor todavía no tiene configurado el envío de correos, "
                    + "así que no podemos mandarle el enlace. Avise al administrador.");
        }
        OffsetDateTime ahora = OffsetDateTime.now();
        if (verificacionDao.contarEnviados(usuarioId, ahora.minusHours(1)) >= ENLACES_POR_HORA) {
            throw new ValidacionException("Ya le mandamos " + ENLACES_POR_HORA + " enlaces en la última hora. Revise su "
                    + "correo, también la carpeta de spam, o pida otro más tarde.");
        }
        String token = Tokens.nuevo();
        verificacionDao.guardarToken(Tokens.sha256(token), usuarioId, usuario.getCorreo(),
                ahora.plusHours(HORAS_VIGENCIA));
        try {
            transporte.enviar(PlantillasCorreo.verificacion(usuario.getCorreo(), usuario.getNombres(),
                    correo.urlPublica() + "/verificar-correo.html#token=" + token));
        } catch (CorreoNoEnviadoException e) {
            LOG.warn("No se pudo mandar el enlace de verificación a la cuenta {}: {}", usuarioId, e.getMessage());
            throw new ServicioNoDisponibleException("No pudimos mandar el correo en este momento. Intente de nuevo en "
                    + "unos minutos.");
        }
    }

    /**
     * Usa el enlace de verificación.
     *
     * @param token clave del enlace
     */
    public void verificar(String token) {
        if (!Tokens.pareceToken(token) || verificacionDao.usarToken(Tokens.sha256(token)).isEmpty()) {
            throw new ValidacionException("El enlace no es válido, ya se usó o venció. Pida uno nuevo en "
                    + "\"Mis seguimientos\".");
        }
    }

    private Usuario usuario(long usuarioId) {
        return usuarioDao.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos su cuenta."));
    }
}
