package co.edu.unicesar.marketradar.exception;

/**
 * Datos de entrada inválidos. Su mensaje está escrito en español sencillo
 * porque se muestra directamente al usuario.
 */
public class ValidacionException extends RuntimeException {

    /**
     * Crea la excepción con un mensaje comprensible para el usuario.
     *
     * @param mensajeUsuario frase amable que explica qué corregir
     */
    public ValidacionException(String mensajeUsuario) {
        super(mensajeUsuario);
    }
}
