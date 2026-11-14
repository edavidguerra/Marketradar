package co.edu.unicesar.marketradar.correo;

/**
 * Manda correos. En producción es {@link TransporteCorreoSmtp}; en las pruebas se usa uno en memoria que guarda lo que
 * se mandó, así ninguna prueba manda correos de verdad.
 */
public interface TransporteCorreo {

    /**
     * @return {@code true} si hay servidor de correo y remitente configurados
     */
    boolean estaConfigurado();

    /**
     * Manda un correo. Solo termina sin error si el servidor de correo lo aceptó.
     *
     * @param mensaje correo
     * @throws CorreoNoEnviadoException si no se pudo mandar o no está configurado
     */
    void enviar(MensajeCorreo mensaje);
}
