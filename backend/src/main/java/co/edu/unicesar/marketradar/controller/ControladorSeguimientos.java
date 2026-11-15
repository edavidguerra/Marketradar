package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.Seguimiento;
import co.edu.unicesar.marketradar.dto.SeguimientoDto;
import co.edu.unicesar.marketradar.dto.SolicitudSeguimientoDto;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import co.edu.unicesar.marketradar.seguridad.UsuarioAutenticado;
import co.edu.unicesar.marketradar.service.seguimiento.ServicioSeguimientos;
import co.edu.unicesar.marketradar.service.seguimiento.ServicioVerificacionCorreo;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Seguir producto", "Seguimientos" y la verificación del correo. Solo responde a cuentas con rol USUARIO, y cada
 * persona ve y cambia únicamente sus propios seguimientos.
 */
@RestController
@RequestMapping("/api/v1/usuario")
public class ControladorSeguimientos {

    private final ServicioSeguimientos servicio;
    private final ServicioVerificacionCorreo verificacion;

    public ControladorSeguimientos(ServicioSeguimientos servicio, ServicioVerificacionCorreo verificacion) {
        this.servicio = servicio;
        this.verificacion = verificacion;
    }

    /**
     * Estado del correo para los avisos.
     *
     * @param usuario persona con sesión
     * @return correo, si está verificado, si el servidor puede mandar correos y cada cuánto se revisan los precios
     */
    @GetMapping("/correo")
    public Map<String, Object> correo(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        ServicioVerificacionCorreo.EstadoCorreo estado = verificacion.estado(usuario.getId());
        Map<String, Object> respuesta = new java.util.LinkedHashMap<>();
        respuesta.put("correo", estado.correo());
        respuesta.put("verificado", estado.verificado());
        respuesta.put("envio_configurado", estado.envioConfigurado());
        respuesta.put("frecuencia_minutos", servicio.frecuenciaMinutos());
        respuesta.put("ultimo_envio", ultimoEnvio(estado.ultimoEnvio()));
        return respuesta;
    }

    /**
     * Manda el enlace para verificar el correo.
     *
     * @param usuario persona con sesión
     */
    @PostMapping("/correo/verificacion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> pedirVerificacion(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        ServicioVerificacionCorreo.Solicitud solicitud = verificacion.solicitar(usuario.getId());
        return Map.of("estado", "aceptado", "aceptado_en", solicitud.aceptadoEn(), "vence_en", solicitud.venceEn(),
                "mensaje", solicitud.mensaje());
    }

    /** El último enlace pedido, en palabras; nunca dice "entregado" porque no se sabe. */
    static Map<String, Object> ultimoEnvio(co.edu.unicesar.marketradar.dao.VerificacionCorreoDao.Envio envio) {
        if (envio == null) {
            return null;
        }
        String texto = switch (envio.estado()) {
            case PENDIENTE -> "Se está mandando el enlace.";
            case ACEPTADO -> envio.usado() ? "Ya usó el enlace."
                    : "El servicio de correo aceptó el último enlace. Puede tardar unos minutos en llegar; revise "
                    + "también la carpeta de spam.";
            case FALLIDO -> "No se pudo mandar el último enlace. Pida otro.";
            case SIN_REGISTRO -> "No sabemos si el último enlace salió. Si no le llegó, pida otro.";
        };
        Map<String, Object> datos = new java.util.LinkedHashMap<>();
        datos.put("estado", envio.estado().name().toLowerCase(Locale.ROOT));
        datos.put("texto", texto);
        datos.put("pedido_en", envio.creadoEn());
        datos.put("aceptado_en", envio.aceptadoEn());
        datos.put("vence_en", envio.venceEn());
        return datos;
    }

    @GetMapping("/seguimientos")
    public List<SeguimientoDto> seguimientos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicio.listar(usuario.getId()).stream().map(SeguimientoDto::desde).toList();
    }

    /**
     * Empieza a seguir una oferta de los resultados.
     *
     * @param usuario persona con sesión
     * @param solicitud oferta, qué avisar y lo que buscó
     * @return el seguimiento y si se mandó la confirmación
     */
    @PostMapping("/seguimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> seguir(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                      @RequestBody SolicitudSeguimientoDto solicitud) {
        if (solicitud == null || solicitud.ofertaId() == null) {
            throw new ValidacionException("Elija el producto que quiere seguir.");
        }
        ServicioSeguimientos.Activacion activacion = servicio.seguir(usuario.getId(), solicitud.ofertaId(),
                tipo(solicitud.tipoAviso()), ConsultaBusqueda.leerPrecio(solicitud.precioObjetivo()),
                solicitud.termino());
        return Map.of("seguimiento", SeguimientoDto.desde(activacion.vista()),
                "confirmacion", activacion.confirmacion().name().toLowerCase(Locale.ROOT),
                "mensaje", mensajeConfirmacion(activacion.confirmacion()));
    }

    /**
     * Cambia qué se avisa, o pausa o reanuda.
     *
     * @param usuario persona con sesión
     * @param ofertaId oferta seguida
     * @param solicitud cambios
     * @return el seguimiento actualizado
     */
    @PutMapping("/seguimientos/{ofertaId}")
    public SeguimientoDto cambiar(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable long ofertaId,
                                  @RequestBody SolicitudSeguimientoDto solicitud) {
        return SeguimientoDto.desde(servicio.cambiar(usuario.getId(), ofertaId, tipo(solicitud.tipoAviso()),
                ConsultaBusqueda.leerPrecio(solicitud.precioObjetivo()), estado(solicitud.estado())));
    }

    @DeleteMapping("/seguimientos/{ofertaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dejarDeSeguir(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable long ofertaId) {
        servicio.cancelar(usuario.getId(), ofertaId);
    }

    static String mensajeConfirmacion(AvisoSeguimiento.Estado estado) {
        return switch (estado) {
            case ENVIADO -> "Listo, el seguimiento quedó activo. El servicio de correo aceptó la confirmación; puede "
                    + "tardar unos minutos en llegar.";
            case PENDIENTE -> "Quedó activado, pero todavía no pudimos mandar el correo de confirmación. Lo "
                    + "intentaremos de nuevo. Si este servidor no tiene configurado el envío de correos, no le llegarán "
                    + "avisos hasta que el administrador lo configure.";
            case FALLIDO -> "Quedó activado, pero el servidor de correo rechazó la confirmación.";
            case DESCARTADO -> "Ya seguía este producto: guardamos lo que quiere que le avisemos. No le mandamos otra "
                    + "confirmación.";
        };
    }

    private static Seguimiento.TipoAviso tipo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Seguimiento.TipoAviso.valueOf(texto.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("Elija cuándo quiere que le avisemos.");
        }
    }

    private static Seguimiento.Estado estado(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Seguimiento.Estado.valueOf(texto.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("El estado del seguimiento no es válido.");
        }
    }
}
