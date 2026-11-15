package co.edu.unicesar.marketradar.correo;

/**
 * El servicio de correo <b>aceptó</b> el mensaje. Aceptar no es entregar: el proveedor todavía puede rebotarlo o el
 * correo de la persona puede mandarlo a spam. Por eso en la página se dice "aceptado por el servicio de correo" y no
 * "entregado".
 *
 * @param proveedor quién lo aceptó ({@code smtp}, {@code brevo}, {@code resend})
 * @param idMensaje identificador que devolvió el proveedor, o {@code null} si no devolvió ninguno
 */
public record ResultadoEnvio(String proveedor, String idMensaje) {

    /** Recorta el identificador para que quepa en la base de datos. */
    public ResultadoEnvio {
        if (idMensaje != null) {
            idMensaje = idMensaje.strip();
            idMensaje = idMensaje.isEmpty() ? null : idMensaje.length() > 200 ? idMensaje.substring(0, 200) : idMensaje;
        }
    }
}
