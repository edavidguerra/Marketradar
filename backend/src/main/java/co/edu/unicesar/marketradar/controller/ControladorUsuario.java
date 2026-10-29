package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.dto.BusquedaHistorialDto;
import co.edu.unicesar.marketradar.dto.CambioClaveDto;
import co.edu.unicesar.marketradar.dto.PerfilDto;
import co.edu.unicesar.marketradar.dto.PqrDto;
import co.edu.unicesar.marketradar.dto.SolicitudPqrDto;
import co.edu.unicesar.marketradar.seguridad.UsuarioAutenticado;
import co.edu.unicesar.marketradar.service.ServicioCuentas;
import co.edu.unicesar.marketradar.service.ServicioHistorial;
import co.edu.unicesar.marketradar.service.ServicioPerfil;
import co.edu.unicesar.marketradar.service.ServicioPqr;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Opciones de la interfaz de usuario: perfil, historial de búsquedas y PQR. Solo responde a cuentas
 * con rol USUARIO, y cada persona ve únicamente sus propios datos.
 */
@RestController
@RequestMapping("/api/v1/usuario")
public class ControladorUsuario {

    private final ServicioPerfil servicioPerfil;
    private final ServicioCuentas servicioCuentas;
    private final ServicioHistorial servicioHistorial;
    private final ServicioPqr servicioPqr;

    public ControladorUsuario(ServicioPerfil servicioPerfil, ServicioCuentas servicioCuentas,
                              ServicioHistorial servicioHistorial, ServicioPqr servicioPqr) {
        this.servicioPerfil = servicioPerfil;
        this.servicioCuentas = servicioCuentas;
        this.servicioHistorial = servicioHistorial;
        this.servicioPqr = servicioPqr;
    }

    @GetMapping("/perfil")
    public PerfilDto perfil(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PerfilDto.desde(servicioPerfil.obtener(usuario.getId()));
    }

    @PutMapping("/perfil")
    public PerfilDto actualizarPerfil(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                      @RequestBody PerfilDto perfil) {
        return PerfilDto.desde(servicioPerfil.actualizar(usuario.getId(), perfil.aDatos()));
    }

    @PutMapping("/perfil/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarClave(@AuthenticationPrincipal UsuarioAutenticado usuario, @RequestBody CambioClaveDto cambio) {
        servicioCuentas.cambiarClave(usuario, cambio.claveActual(), cambio.claveNueva(), cambio.confirmacionClave());
    }

    @GetMapping("/historial")
    public List<BusquedaHistorialDto> historial(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicioHistorial.listar(usuario.getId()).stream().map(BusquedaHistorialDto::desde).toList();
    }

    @DeleteMapping("/historial")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrarHistorial(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        servicioHistorial.borrar(usuario.getId());
    }

    @GetMapping("/pqr")
    public List<PqrDto> misPqr(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicioPqr.listarDelUsuario(usuario.getId()).stream().map(PqrDto::desde).toList();
    }

    @PostMapping("/pqr")
    @ResponseStatus(HttpStatus.CREATED)
    public PqrDto radicarPqr(@AuthenticationPrincipal UsuarioAutenticado usuario,
                             @RequestBody SolicitudPqrDto solicitud) {
        return PqrDto.desde(servicioPqr.radicar(usuario.getId(), solicitud.tipo(), solicitud.asunto(),
                solicitud.descripcion()));
    }
}
