package co.edu.unicesar.marketradar.correo;

/**
 * El servicio de correo no aceptó el mensaje o no respondió. El mensaje no trae la clave, el usuario del servidor ni
 * el contenido del correo.
 */
public class CorreoNoEnviadoException extends RuntimeException {

    private final boolean transitorio;

    /**
     * Error que puede arreglarse solo (el servidor no respondió, límite de envíos): vale la pena reintentar.
     *
     * @param mensaje motivo, sin datos privados
     */
    public CorreoNoEnviadoException(String mensaje) {
        this(mensaje, true);
    }

    /**
     * @param mensaje motivo, sin datos privados
     * @param transitorio {@code true} si reintentar más tarde puede funcionar; {@code false} si el proveedor rechazó el
     *                    mensaje o la configuración (clave inválida, remitente sin verificar, dirección inválida)
     */
    public CorreoNoEnviadoException(String mensaje, boolean transitorio) {
        super(mensaje);
        this.transitorio = transitorio;
    }

    /** @return {@code true} si conviene reintentar */
    public boolean esTransitorio() {
        return transitorio;
    }
}
