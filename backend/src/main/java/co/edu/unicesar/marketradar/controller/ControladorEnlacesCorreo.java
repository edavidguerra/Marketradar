package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.dto.TokenDto;
import co.edu.unicesar.marketradar.service.seguimiento.ServicioSeguimientos;
import co.edu.unicesar.marketradar.service.seguimiento.ServicioVerificacionCorreo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Los enlaces de los correos: verificar el correo y dejar de seguir un producto. No exigen sesión, porque la persona
 * puede abrirlos desde el celular; la clave del enlace es la que autoriza, y solo sirve para esa acción.
 */
@RestController
@RequestMapping("/api/v1/enlaces")
public class ControladorEnlacesCorreo {

    private final ServicioVerificacionCorreo verificacion;
    private final ServicioSeguimientos seguimientos;

    public ControladorEnlacesCorreo(ServicioVerificacionCorreo verificacion, ServicioSeguimientos seguimientos) {
        this.verificacion = verificacion;
        this.seguimientos = seguimientos;
    }

    @PostMapping("/verificacion-correo")
    public Map<String, String> verificar(@RequestBody TokenDto cuerpo) {
        verificacion.verificar(cuerpo == null ? null : cuerpo.token());
        return Map.of("mensaje", "Listo, su correo quedó verificado. Ya puede seguir productos y recibir avisos.");
    }

    @PostMapping("/cancelacion-seguimiento")
    public Map<String, String> cancelar(@RequestBody TokenDto cuerpo) {
        String producto = seguimientos.cancelarPorEnlace(cuerpo == null ? null : cuerpo.token());
        return Map.of("mensaje", "Listo, ya no le avisaremos sobre " + producto + ".");
    }
}
