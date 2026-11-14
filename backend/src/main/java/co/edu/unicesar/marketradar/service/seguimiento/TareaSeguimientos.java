package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.config.PropiedadesSeguimientos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Tarea del servidor que revisa los precios de las ofertas seguidas cada pocos minutos. Mientras el servidor esté
 * encendido, funciona aunque nadie tenga la página abierta. En el plan gratis de Render el servidor se duerme tras 15
 * minutos sin visitas: para que la tarea siga corriendo, un servicio externo debe llamar a
 * {@code POST /api/v1/tareas/seguimientos} (ver docs/seguimientos.md).
 */
@Component
public class TareaSeguimientos {

    private static final Logger LOG = LoggerFactory.getLogger(TareaSeguimientos.class);

    private final ServicioComprobacionPrecios comprobacion;
    private final PropiedadesSeguimientos propiedades;

    public TareaSeguimientos(ServicioComprobacionPrecios comprobacion, PropiedadesSeguimientos propiedades) {
        this.comprobacion = comprobacion;
        this.propiedades = propiedades;
    }

    /** Despierta cada {@code revisar-cada-minutos} y revisa lo que ya toca. */
    @Scheduled(fixedDelayString = "${marketradar.seguimientos.revisar-cada-minutos:15}",
            initialDelayString = "${marketradar.seguimientos.espera-inicial-minutos:3}", timeUnit = TimeUnit.MINUTES)
    public void ejecutar() {
        if (!propiedades.habilitado()) {
            return;
        }
        try {
            comprobacion.ejecutarRonda();
        } catch (RuntimeException e) {
            LOG.error("Falló la ronda de seguimientos; se intenta otra vez en la próxima", e);
        }
    }
}
