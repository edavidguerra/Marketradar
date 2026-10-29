package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.dto.SesionDto;
import co.edu.unicesar.marketradar.dto.SolicitudIngresoDto;
import co.edu.unicesar.marketradar.dto.SolicitudRegistroDto;
import co.edu.unicesar.marketradar.seguridad.UsuarioAutenticado;
import co.edu.unicesar.marketradar.service.ServicioCuentas;
import co.edu.unicesar.marketradar.validacion.PoliticaContrasena;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Registro, ingreso y consulta de la sesión. La salida la atiende Spring Security en
 * {@code POST /api/v1/auth/salida}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class ControladorAutenticacion {

    private final ServicioCuentas servicioCuentas;
    private final SecurityContextRepository repositorioContexto;
    private final PoliticaContrasena politicaContrasena;

    public ControladorAutenticacion(ServicioCuentas servicioCuentas, SecurityContextRepository repositorioContexto,
                                    PoliticaContrasena politicaContrasena) {
        this.servicioCuentas = servicioCuentas;
        this.repositorioContexto = repositorioContexto;
        this.politicaContrasena = politicaContrasena;
    }

    /**
     * Crea una cuenta de usuario e inicia su sesión.
     *
     * @param solicitud datos del formulario
     * @param peticion petición HTTP
     * @param respuesta respuesta HTTP
     * @return sesión iniciada
     */
    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public SesionDto registrar(@RequestBody SolicitudRegistroDto solicitud, HttpServletRequest peticion,
                               HttpServletResponse respuesta) {
        servicioCuentas.registrar(solicitud.nombres(), solicitud.apellidos(), solicitud.correo(), solicitud.clave(),
                solicitud.confirmacionClave());
        return iniciarSesion(servicioCuentas.ingresar(solicitud.correo(), solicitud.clave()), peticion, respuesta);
    }

    /**
     * Verifica correo y contraseña e inicia la sesión.
     *
     * @param solicitud correo y contraseña
     * @param peticion petición HTTP
     * @param respuesta respuesta HTTP
     * @return sesión iniciada con la interfaz que corresponde al rol
     */
    @PostMapping("/ingreso")
    public SesionDto ingresar(@RequestBody SolicitudIngresoDto solicitud, HttpServletRequest peticion,
                              HttpServletResponse respuesta) {
        return iniciarSesion(servicioCuentas.ingresar(solicitud.correo(), solicitud.clave()), peticion, respuesta);
    }

    /**
     * Devuelve la persona con sesión iniciada, o 204 si no hay sesión.
     *
     * @param usuario persona autenticada
     * @return sesión actual
     */
    @GetMapping("/sesion")
    public ResponseEntity<SesionDto> sesion(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return usuario == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(SesionDto.desde(usuario));
    }

    /**
     * Revisa una contraseña mientras la persona la escribe, sin guardarla.
     *
     * @param cuerpo {@code clave} y {@code correo}
     * @return reglas que aún no cumple
     */
    @PostMapping("/reglas-clave")
    public Map<String, List<String>> revisarClave(@RequestBody Map<String, String> cuerpo) {
        return Map.of("pendientes", politicaContrasena.reglasIncumplidas(cuerpo.get("clave"), cuerpo.get("correo")));
    }

    private SesionDto iniciarSesion(Authentication autenticacion, HttpServletRequest peticion,
                                    HttpServletResponse respuesta) {
        if (peticion.getSession(false) != null) {
            peticion.changeSessionId();
        }
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        repositorioContexto.saveContext(contexto, peticion, respuesta);
        return SesionDto.desde((UsuarioAutenticado) autenticacion.getPrincipal());
    }
}
