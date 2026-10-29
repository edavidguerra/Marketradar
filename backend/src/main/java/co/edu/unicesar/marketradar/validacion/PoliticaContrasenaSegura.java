package co.edu.unicesar.marketradar.validacion;

import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Contraseña fuerte: de 8 a 64 caracteres, con minúscula, mayúscula, número y carácter especial,
 * sin espacios y sin contener el correo.
 */
@Component
public class PoliticaContrasenaSegura implements PoliticaContrasena {

    public static final int LONGITUD_MINIMA = 8;
    public static final int LONGITUD_MAXIMA = 64;

    @Override
    public List<String> reglasIncumplidas(String clave, String correo) {
        String valor = clave == null ? "" : clave;
        List<String> incumplidas = new ArrayList<>();
        if (valor.length() < LONGITUD_MINIMA || valor.length() > LONGITUD_MAXIMA) {
            incumplidas.add("Tener entre 8 y 64 caracteres");
        }
        if (valor.chars().noneMatch(Character::isLowerCase)) {
            incumplidas.add("Tener al menos una letra minúscula");
        }
        if (valor.chars().noneMatch(Character::isUpperCase)) {
            incumplidas.add("Tener al menos una letra mayúscula");
        }
        if (valor.chars().noneMatch(Character::isDigit)) {
            incumplidas.add("Tener al menos un número");
        }
        if (valor.chars().noneMatch(PoliticaContrasenaSegura::esEspecial)) {
            incumplidas.add("Tener al menos un carácter especial, por ejemplo ! @ # $ %");
        }
        if (valor.chars().anyMatch(Character::isWhitespace)) {
            incumplidas.add("No tener espacios");
        }
        if (contieneCorreo(valor, correo)) {
            incumplidas.add("No contener su correo");
        }
        return incumplidas;
    }

    @Override
    public void validar(String clave, String correo) {
        List<String> incumplidas = reglasIncumplidas(clave, correo);
        if (!incumplidas.isEmpty()) {
            throw new ValidacionException("La contraseña debe: " + String.join("; ", incumplidas).toLowerCase(Locale.ROOT)
                    + ".");
        }
    }

    private static boolean esEspecial(int caracter) {
        return !Character.isLetterOrDigit(caracter) && !Character.isWhitespace(caracter);
    }

    private static boolean contieneCorreo(String clave, String correo) {
        if (correo == null || !correo.contains("@")) {
            return false;
        }
        String usuario = correo.substring(0, correo.indexOf('@')).toLowerCase(Locale.ROOT);
        return usuario.length() >= 4 && clave.toLowerCase(Locale.ROOT).contains(usuario);
    }
}
