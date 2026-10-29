package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.dto.PqrDto;
import co.edu.unicesar.marketradar.dto.RespuestaPqrDto;
import co.edu.unicesar.marketradar.dto.ResumenAdministradorDto;
import co.edu.unicesar.marketradar.seguridad.UsuarioAutenticado;
import co.edu.unicesar.marketradar.service.ServicioAdministracion;
import co.edu.unicesar.marketradar.service.ServicioPqr;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Opciones de la interfaz de administrador: resumen y gestión de PQR con el nombre de quien la radicó.
 * Solo responde a cuentas con rol ADMINISTRADOR.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class ControladorAdministrador {

    private final ServicioPqr servicioPqr;
    private final ServicioAdministracion servicioAdministracion;

    public ControladorAdministrador(ServicioPqr servicioPqr, ServicioAdministracion servicioAdministracion) {
        this.servicioPqr = servicioPqr;
        this.servicioAdministracion = servicioAdministracion;
    }

    @GetMapping("/resumen")
    public ResumenAdministradorDto resumen() {
        return servicioAdministracion.resumen();
    }

    /**
     * @param estado RADICADA, EN_PROCESO o RESPONDIDA; vacío para todas
     * @return PQR, primero las más antiguas sin responder
     */
    @GetMapping("/pqr")
    public List<PqrDto> listarPqr(@RequestParam(required = false) String estado) {
        return servicioPqr.listarParaAdministrador(estado).stream().map(PqrDto::desde).toList();
    }

    /**
     * Abre una PQR; si era nueva queda en proceso.
     *
     * @param id número de radicado
     * @return PQR con el nombre y correo de quien la radicó
     */
    @GetMapping("/pqr/{id}")
    public PqrDto abrirPqr(@PathVariable long id) {
        return PqrDto.desde(servicioPqr.abrir(id));
    }

    /**
     * Responde una PQR.
     *
     * @param id número de radicado
     * @param cuerpo texto de la respuesta
     * @param administrador administrador con sesión
     * @return PQR respondida
     */
    @PostMapping("/pqr/{id}/respuesta")
    public PqrDto responderPqr(@PathVariable long id, @RequestBody RespuestaPqrDto cuerpo,
                               @AuthenticationPrincipal UsuarioAutenticado administrador) {
        return PqrDto.desde(servicioPqr.responder(id, administrador.getId(), cuerpo.respuesta()));
    }
}
