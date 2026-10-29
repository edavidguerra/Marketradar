package co.edu.unicesar.marketradar.dto;

/**
 * Formulario para cambiar la contraseña.
 *
 * @param claveActual contraseña actual
 * @param claveNueva contraseña nueva
 * @param confirmacionClave contraseña nueva repetida
 */
public record CambioClaveDto(String claveActual, String claveNueva, String confirmacionClave) {

    @Override
    public String toString() {
        return "CambioClaveDto[]";
    }
}
