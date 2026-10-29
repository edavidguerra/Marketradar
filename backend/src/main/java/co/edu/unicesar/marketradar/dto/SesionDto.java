package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.Rol;
import co.edu.unicesar.marketradar.seguridad.UsuarioAutenticado;

/**
 * Persona con sesión iniciada y la interfaz que le corresponde.
 *
 * @param id identificador de la cuenta
 * @param nombre nombres, para el saludo
 * @param correo correo de la cuenta
 * @param rol USUARIO o ADMINISTRADOR
 * @param interfaz página a la que debe ir el navegador
 */
public record SesionDto(long id, String nombre, String correo, Rol rol, String interfaz) {

    /**
     * @param usuario persona autenticada
     * @return datos de la sesión
     */
    public static SesionDto desde(UsuarioAutenticado usuario) {
        String interfaz = usuario.getRol() == Rol.ADMINISTRADOR ? "/admin.html" : "/usuario.html";
        return new SesionDto(usuario.getId(), usuario.getNombre(), usuario.getUsername(), usuario.getRol(), interfaz);
    }
}
