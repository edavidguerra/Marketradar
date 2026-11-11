package co.edu.unicesar.marketradar.dto;

/**
 * Datos del formulario de creación de cuenta.
 *
 * @param nombres nombres de la persona
 * @param apellidos apellidos de la persona
 * @param correo correo, que será su usuario
 * @param clave contraseña
 * @param confirmacionClave contraseña repetida
 * @param idioma idioma en que quiere leer las reseñas; si no viene, español
 */
public record SolicitudRegistroDto(String nombres, String apellidos, String correo, String clave,
                                   String confirmacionClave, String idioma) {

    @Override
    public String toString() {
        return "SolicitudRegistroDto[correo=" + correo + "]";
    }
}
