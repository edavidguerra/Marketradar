package co.edu.unicesar.marketradar.correo;

/**
 * Un correo para mandar, con texto plano y HTML (el lector de correo elige cuál mostrar).
 *
 * @param para dirección del destinatario
 * @param asunto asunto
 * @param texto contenido en texto plano
 * @param html contenido en HTML, ya escapado
 * @param claveIdempotencia clave que identifica este correo; si el proveedor la admite, un reintento con la misma clave
 *                          no manda un segundo correo. Puede ser {@code null}
 */
public record MensajeCorreo(String para, String asunto, String texto, String html, String claveIdempotencia) {

    /**
     * Correo sin clave de idempotencia.
     *
     * @param para destinatario
     * @param asunto asunto
     * @param texto texto plano
     * @param html HTML
     */
    public MensajeCorreo(String para, String asunto, String texto, String html) {
        this(para, asunto, texto, html, null);
    }

    /**
     * @param clave clave de idempotencia
     * @return el mismo correo con esa clave
     */
    public MensajeCorreo conClave(String clave) {
        return new MensajeCorreo(para, asunto, texto, html, clave);
    }

    /** @return el destinatario a medias ({@code a***@example.com}), para los registros */
    public String paraEnRegistro() {
        return ocultar(para);
    }

    /**
     * @param correo dirección
     * @return la dirección con el usuario oculto, para los registros
     */
    public static String ocultar(String correo) {
        if (correo == null || correo.indexOf('@') < 1) {
            return "(sin correo)";
        }
        int arroba = correo.indexOf('@');
        return correo.charAt(0) + "***" + correo.substring(arroba);
    }
}
