package co.edu.unicesar.marketradar.dto;

/**
 * Datos del formulario de ingreso.
 *
 * @param correo correo de la cuenta
 * @param clave contraseña
 */
public record SolicitudIngresoDto(String correo, String clave) {

    @Override
    public String toString() {
        return "SolicitudIngresoDto[correo=" + correo + "]";
    }
}
