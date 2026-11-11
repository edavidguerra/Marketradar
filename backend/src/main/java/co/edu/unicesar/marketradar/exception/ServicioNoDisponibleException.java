package co.edu.unicesar.marketradar.exception;

/**
 * Una función que depende de un servicio externo (por ejemplo, la traducción con IA) no se puede usar ahora: no
 * está configurada, se acabó la cuota o no respondió. La página muestra el mensaje y lo demás sigue funcionando.
 */
public class ServicioNoDisponibleException extends RuntimeException {

    /**
     * Crea la excepción con un mensaje comprensible para el usuario.
     *
     * @param mensajeUsuario frase que explica qué no se pudo hacer
     */
    public ServicioNoDisponibleException(String mensajeUsuario) {
        super(mensajeUsuario);
    }
}
