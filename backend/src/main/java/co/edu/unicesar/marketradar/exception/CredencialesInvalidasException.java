package co.edu.unicesar.marketradar.exception;

/**
 * El correo o la contraseña no coinciden, o la cuenta está inactiva o bloqueada temporalmente.
 */
public class CredencialesInvalidasException extends RuntimeException {

    private final boolean bloqueada;

    /**
     * @param mensajeUsuario frase amable que se muestra en pantalla
     * @param bloqueada {@code true} si se superó el máximo de intentos
     */
    public CredencialesInvalidasException(String mensajeUsuario, boolean bloqueada) {
        super(mensajeUsuario);
        this.bloqueada = bloqueada;
    }

    public boolean isBloqueada() {
        return bloqueada;
    }
}
