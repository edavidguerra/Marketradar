package co.edu.unicesar.marketradar.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Idiomas en que una persona puede pedir que se le muestren las reseñas. Se elige al crear la cuenta y se cambia en
 * Mi perfil; la página sigue en español.
 */
public enum Idioma {

    ES("es", "español"),
    EN("en", "inglés"),
    PT("pt", "portugués"),
    FR("fr", "francés"),
    IT("it", "italiano"),
    DE("de", "alemán");

    private final String codigo;
    private final String nombre;

    Idioma(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    /**
     * Código de dos letras (ISO 639-1), como se guarda en la base de datos.
     *
     * @return por ejemplo {@code es}
     */
    public String codigo() {
        return codigo;
    }

    /**
     * Nombre en español, en minúscula.
     *
     * @return por ejemplo {@code inglés}
     */
    public String nombre() {
        return nombre;
    }

    /**
     * Busca el idioma por su código, sin importar mayúsculas.
     *
     * @param codigo por ejemplo {@code "en"}
     * @return el idioma, o vacío si el código no es uno de los aceptados
     */
    public static Optional<Idioma> desdeCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        String limpio = codigo.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(idioma -> idioma.codigo.equals(limpio)).findFirst();
    }
}
