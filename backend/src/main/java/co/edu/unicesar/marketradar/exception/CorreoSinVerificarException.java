package co.edu.unicesar.marketradar.exception;

/**
 * La persona quiere recibir avisos por correo, pero todavía no verificó su correo. La página le ofrece mandarle el
 * enlace de verificación.
 */
public class CorreoSinVerificarException extends RuntimeException {

    /**
     * @param mensajeUsuario frase que explica qué hacer
     */
    public CorreoSinVerificarException(String mensajeUsuario) {
        super(mensajeUsuario);
    }
}
