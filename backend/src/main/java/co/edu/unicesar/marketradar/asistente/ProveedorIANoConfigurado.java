package co.edu.unicesar.marketradar.asistente;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Proveedor usado mientras no se elija un modelo de IA. Responde con un mensaje amable
 * para que la interfaz funcione de punta a punta.
 */
@Component
@ConditionalOnProperty(prefix = "marketradar.asistente", name = "proveedor", havingValue = "ninguno",
        matchIfMissing = true)
public class ProveedorIANoConfigurado implements ProveedorIA {

    @Override
    public String nombre() {
        return "ninguno";
    }

    @Override
    public boolean estaConfigurado() {
        return false;
    }

    @Override
    public String responder(List<MensajeChat> conversacion) {
        return "El asistente todavía no está disponible. Mientras tanto, escriba el nombre del producto "
                + "en el buscador y le mostraremos dónde está más barato.";
    }
}
