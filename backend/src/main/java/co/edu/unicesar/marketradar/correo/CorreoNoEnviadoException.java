package co.edu.unicesar.marketradar.correo;

/**
 * El servidor de correo no aceptó el mensaje o no respondió. El mensaje no trae la clave ni el contenido del correo.
 */
public class CorreoNoEnviadoException extends RuntimeException {

    /**
     * @param mensaje motivo, sin datos privados
     */
    public CorreoNoEnviadoException(String mensaje) {
        super(mensaje);
    }
}
