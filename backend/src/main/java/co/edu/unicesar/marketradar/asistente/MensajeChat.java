package co.edu.unicesar.marketradar.asistente;

import java.util.Objects;

/**
 * Un mensaje de la conversación con el asistente de IA.
 */
public final class MensajeChat {

    private final RolMensaje rol;
    private final String contenido;

    /**
     * @param rol autor del mensaje
     * @param contenido texto del mensaje
     */
    public MensajeChat(RolMensaje rol, String contenido) {
        this.rol = Objects.requireNonNull(rol, "rol");
        this.contenido = Objects.requireNonNull(contenido, "contenido");
    }

    public RolMensaje getRol() {
        return rol;
    }

    public String getContenido() {
        return contenido;
    }
}
