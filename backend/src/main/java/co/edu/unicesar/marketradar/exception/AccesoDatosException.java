package co.edu.unicesar.marketradar.exception;

/**
 * Error de la capa de acceso a datos. Envuelve las {@link java.sql.SQLException}
 * para que las capas superiores no dependan de JDBC.
 */
public class AccesoDatosException extends RuntimeException {

    /**
     * Crea la excepción con un mensaje y su causa original.
     *
     * @param mensaje descripción técnica del error
     * @param causa excepción original
     */
    public AccesoDatosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
