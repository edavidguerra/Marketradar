package co.edu.unicesar.marketradar.correo;

/**
 * Manda correos. Hay dos formas: {@link TransporteCorreoSmtp} (servidor SMTP) y {@link TransporteCorreoHttp} (API HTTPS
 * de Brevo o Resend, para alojamientos que bloquean los puertos SMTP, como el plan gratis de Render). Cuál se usa lo
 * decide {@link ConfiguracionCorreo}. En las pruebas se usa uno en memoria, así ninguna prueba manda correos de verdad.
 */
public interface TransporteCorreo {

    /**
     * @return {@code true} si hay servicio de correo y remitente configurados
     */
    boolean estaConfigurado();

    /** @return nombre del proveedor, para los registros y la base de datos ({@code smtp}, {@code brevo}...) */
    default String nombre() {
        return "smtp";
    }

    /**
     * Manda un correo. Solo termina sin error si el servicio de correo lo <b>aceptó</b>; eso no confirma que haya
     * llegado a la bandeja de la persona.
     *
     * @param mensaje correo
     * @return quién lo aceptó y con qué identificador
     * @throws CorreoNoEnviadoException si no se pudo mandar o no está configurado
     */
    ResultadoEnvio enviar(MensajeCorreo mensaje);
}
