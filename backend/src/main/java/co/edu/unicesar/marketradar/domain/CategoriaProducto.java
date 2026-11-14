package co.edu.unicesar.marketradar.domain;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tipo de producto deducido del título, para no comparar como equivalentes productos que no lo son (un celular y su
 * funda, un televisor y un soporte). Solo se reconocen palabras claras; si no hay ninguna, el tipo es
 * {@link #DESCONOCIDA} y no se afirma nada.
 */
public enum CategoriaProducto {

    ACCESORIO("Accesorio"),
    CELULAR("Celular"),
    TELEVISOR("Televisor"),
    PORTATIL("Computador portátil"),
    TABLETA("Tableta"),
    CONSOLA("Consola de videojuegos"),
    AUDIFONOS("Audífonos"),
    RELOJ("Reloj inteligente"),
    NEVERA("Nevera"),
    LAVADORA("Lavadora"),
    LICUADORA("Licuadora"),
    FREIDORA("Freidora de aire"),
    TENSIOMETRO("Tensiómetro"),
    DESCONOCIDA("Sin identificar");

    private final String texto;

    CategoriaProducto(String texto) {
        this.texto = texto;
    }

    /** @return nombre para mostrar */
    public String texto() {
        return texto;
    }

    /** Primeras palabras que dicen que la publicación es algo para otro producto, no el producto. */
    private static final Set<String> PRIMERA_PALABRA_ACCESORIO = Set.of("funda", "forro", "estuche", "carcasa", "case",
            "cover", "protector", "mica", "vidrio", "lamina", "cable", "cargador", "adaptador", "soporte", "base",
            "control", "correa", "manilla", "repuesto", "skin", "sticker", "pelicula");

    /** "Funda para iPhone", "cargador compatible con Samsung". */
    private static final Pattern PARA_OTRO = Pattern.compile(
            "^(funda|forro|estuche|carcasa|protector|mica|vidrio|cable|cargador|adaptador|soporte|base|correa|"
                    + "manilla|repuesto|bateria|control|case|cover)\\b.*\\b(para|compatible|for)\\b");

    private record Regla(CategoriaProducto categoria, Pattern patron) {
    }

    /** El orden importa: "tablet" antes que "celular" porque "galaxy tab" también dice "galaxy". */
    private static final List<Regla> REGLAS = List.of(
            new Regla(TABLETA, Pattern.compile("\\b(tablet|tableta|ipad|galaxy tab|tab [a-z]?\\d)\\b")),
            new Regla(PORTATIL, Pattern.compile("\\b(portatil|laptop|notebook|macbook|chromebook|ultrabook)\\b")),
            new Regla(TELEVISOR, Pattern.compile("\\b(televisor|televisores|smart tv|tv|qled|oled tv|led tv)\\b")),
            new Regla(CONSOLA, Pattern.compile("\\b(consola|playstation|ps5|ps4|xbox|nintendo switch)\\b")),
            new Regla(RELOJ, Pattern.compile("\\b(smartwatch|reloj inteligente|apple watch|galaxy watch|smart band)\\b")),
            new Regla(AUDIFONOS, Pattern.compile("\\b(audifonos|airpods|auriculares|headphones|earbuds|diadema)\\b")),
            new Regla(CELULAR, Pattern.compile("\\b(celular|celulares|smartphone|telefono movil|iphone|galaxy [asmz]\\d|"
                    + "redmi|moto [egz]\\d|pixel \\d|poco [a-z]?\\d)")),
            new Regla(NEVERA, Pattern.compile("\\b(nevera|nevecon|refrigerador|refrigeradora|frigorifico)\\b")),
            new Regla(LAVADORA, Pattern.compile("\\b(lavadora|lavasecadora)\\b")),
            new Regla(FREIDORA, Pattern.compile("\\b(freidora|air fryer|airfryer)\\b")),
            new Regla(LICUADORA, Pattern.compile("\\b(licuadora|blender)\\b")),
            new Regla(TENSIOMETRO, Pattern.compile("\\b(tensiometro|monitor de presion|baumanometro)\\b")));

    /**
     * Deduce el tipo de producto del título.
     *
     * @param titulo título de la publicación
     * @return tipo, o {@link #DESCONOCIDA} si ninguna palabra lo dice con claridad
     */
    public static CategoriaProducto detectar(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            return DESCONOCIDA;
        }
        String texto = normalizar(titulo);
        String primera = texto.split(" ")[0];
        if (PRIMERA_PALABRA_ACCESORIO.contains(primera) || PARA_OTRO.matcher(texto).find()) {
            return ACCESORIO;
        }
        for (Regla regla : REGLAS) {
            if (regla.patron().matcher(texto).find()) {
                return regla.categoria();
            }
        }
        return DESCONOCIDA;
    }

    /**
     * Dos tipos se pueden comparar como el mismo producto si son iguales, o si uno de ellos no se pudo identificar y
     * ninguno es un accesorio.
     *
     * @param otra el otro tipo
     * @return {@code true} si no hay nada que diga que son productos distintos
     */
    public boolean compatibleCon(CategoriaProducto otra) {
        if (this == otra) {
            return true;
        }
        if (this == ACCESORIO || otra == ACCESORIO) {
            return false;
        }
        return this == DESCONOCIDA || otra == DESCONOCIDA;
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ñ]+", " ").strip();
    }
}
