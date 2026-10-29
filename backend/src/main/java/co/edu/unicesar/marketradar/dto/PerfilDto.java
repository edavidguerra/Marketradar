package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.Usuario;
import co.edu.unicesar.marketradar.service.ServicioPerfil;

import java.time.LocalDate;

/**
 * Formulario de perfil. El correo se muestra pero no se puede cambiar.
 *
 * @param nombres nombres
 * @param apellidos apellidos
 * @param correo correo de la cuenta (solo lectura)
 * @param tipoDocumento CC, CE o PA
 * @param numeroDocumento número de documento
 * @param fechaNacimiento fecha de nacimiento (AAAA-MM-DD)
 * @param celular celular de 10 dígitos
 * @param ciudad ciudad de residencia
 */
public record PerfilDto(String nombres, String apellidos, String correo, String tipoDocumento,
                        String numeroDocumento, LocalDate fechaNacimiento, String celular, String ciudad) {

    /**
     * @param usuario cuenta
     * @return datos del perfil
     */
    public static PerfilDto desde(Usuario usuario) {
        return new PerfilDto(usuario.getNombres(), usuario.getApellidos(), usuario.getCorreo(),
                usuario.getTipoDocumento() == null ? null : usuario.getTipoDocumento().name(),
                usuario.getNumeroDocumento(), usuario.getFechaNacimiento(), usuario.getCelular(), usuario.getCiudad());
    }

    /**
     * @return datos editables para el servicio
     */
    public ServicioPerfil.DatosPerfil aDatos() {
        return new ServicioPerfil.DatosPerfil(nombres, apellidos, tipoDocumento, numeroDocumento, fechaNacimiento,
                celular, ciudad);
    }
}
