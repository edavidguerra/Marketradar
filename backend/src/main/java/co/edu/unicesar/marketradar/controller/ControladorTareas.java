package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.config.PropiedadesSeguimientos;
import co.edu.unicesar.marketradar.service.seguimiento.ServicioComprobacionPrecios;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Permite que un servicio externo de tareas programadas (por ejemplo cron-job.org o GitHub Actions) despierte el
 * servidor y ejecute una ronda de comprobación de precios. Exige la clave {@code TAREAS_TOKEN} en la cabecera
 * {@code X-Tarea-Token}; sin esa variable configurada, la ruta no existe.
 */
@RestController
@RequestMapping("/api/v1/tareas")
public class ControladorTareas {

    private final ServicioComprobacionPrecios comprobacion;
    private final PropiedadesSeguimientos propiedades;

    public ControladorTareas(ServicioComprobacionPrecios comprobacion, PropiedadesSeguimientos propiedades) {
        this.comprobacion = comprobacion;
        this.propiedades = propiedades;
    }

    @PostMapping("/seguimientos")
    public ResponseEntity<Object> seguimientos(@RequestHeader(name = "X-Tarea-Token", required = false) String token) {
        String esperado = propiedades.tokenTareas();
        if (esperado == null || esperado.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                esperado.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!propiedades.habilitado()) {
            return ResponseEntity.ok(Map.of("ejecutada", false, "motivo", "La comprobación de seguimientos está apagada"));
        }
        return ResponseEntity.ok(comprobacion.ejecutarRonda());
    }
}
