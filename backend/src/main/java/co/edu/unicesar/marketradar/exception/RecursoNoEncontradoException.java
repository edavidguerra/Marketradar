package co.edu.unicesar.marketradar.exception;

/**
 * El recurso solicitado (oferta, producto, marketplace) no existe.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    /**
     * Crea la excepción con un mensaje comprensible para el usuario.
     *
     * @param mensajeUsuario frase que explica qué no se encontró
     */
    public RecursoNoEncontradoException(String mensajeUsuario) {
        super(mensajeUsuario);
    }
}
