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
 * "Seguir producto", "Mis seguimientos" y la verificación del correo. Solo responde a cuentas con rol USUARIO, y cada
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
        return Map.of("correo", estado.correo(), "verificado", estado.verificado(),
                "envio_configurado", estado.envioConfigurado(), "frecuencia_minutos", servicio.frecuenciaMinutos());
    }

    /**
     * Manda el enlace para verificar el correo.
     *
     * @param usuario persona con sesión
     */
    @PostMapping("/correo/verificacion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void pedirVerificacion(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        verificacion.solicitar(usuario.getId());
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
            case ENVIADO -> "Listo. Le mandamos un correo de confirmación.";
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
