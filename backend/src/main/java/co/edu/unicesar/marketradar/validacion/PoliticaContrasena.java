package co.edu.unicesar.marketradar.validacion;

import java.util.List;

/**
 * Reglas que debe cumplir una contraseña. Se define como interfaz para poder endurecer
 * la política sin tocar el registro ni el cambio de contraseña (OCP, DIP).
 */
public interface PoliticaContrasena {

    /**
     * Revisa la contraseña y devuelve las reglas que no cumple.
     *
     * @param clave contraseña propuesta
     * @param correo correo del usuario, para evitar contraseñas que lo contengan
     * @return mensajes de las reglas incumplidas; vacío si la contraseña es válida
     */
    List<String> reglasIncumplidas(String clave, String correo);

    /**
     * Lanza {@link co.edu.unicesar.marketradar.exception.ValidacionException} si la contraseña no es segura.
     *
     * @param clave contraseña propuesta
     * @param correo correo del usuario
     */
    void validar(String clave, String correo);
}
