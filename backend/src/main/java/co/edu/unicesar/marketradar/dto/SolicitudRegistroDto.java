package co.edu.unicesar.marketradar.dto;

/**
 * Datos del formulario de creación de cuenta.
 *
 * @param nombres nombres de la persona
 * @param apellidos apellidos de la persona
 * @param correo correo, que será su usuario
 * @param clave contraseña
 * @param confirmacionClave contraseña repetida
 */
public record SolicitudRegistroDto(String nombres, String apellidos, String correo, String clave,
                                   String confirmacionClave) {

    @Override
    public String toString() {
        return "SolicitudRegistroDto[correo=" + correo + "]";
    }
}
