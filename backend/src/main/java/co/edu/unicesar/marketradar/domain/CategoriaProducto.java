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
    ALMACENAMIENTO("Memoria o disco de almacenamiento"),
    VIDEOJUEGO("Videojuego o contenido digital"),
    EMPAQUE("Caja o empaque sin el producto"),
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
            "control", "controles", "mando", "mandos", "joystick", "gamepad", "correa", "manilla", "repuesto",
            "repuestos", "skin", "sticker", "pelicula");

    /**
     * Palabras que en cualquier parte del título dicen que es un accesorio, aunque empiece por la marca
     * ("Sony DualSense Wireless Controller PS5"). Solo cuentan si el título no nombró antes el producto
     * ("Consola PS5 con control DualSense" sigue siendo una consola).
     */
    private static final Pattern ACCESORIO_EN_EL_TITULO = Pattern.compile("\\b(dualsense|dualshock|joy ?con|joystick|"
            + "gamepad|controller|controles?|mandos?|repuestos?|refaccion|funda|carcasa|skin|protector|cargador|"
            + "estacion de carga|base de carga|soporte)\\b");

    private static final Pattern REPUESTO = Pattern.compile("(?<!\\bde )\\b(repuestos?|refaccion)\\b");

    /** Grupos de accesorios: quien busca un control acepta un mando o un DualSense, pero no una funda. */
    private static final List<String[]> TIPOS_ACCESORIO = List.of(
            new String[] {"control", "\\b(control|controles|mando|mandos|joystick|gamepad|controller|dualsense|"
                    + "dualshock|joy ?con)\\b"},
            new String[] {"funda", "\\b(funda|forro|estuche|carcasa|case|cover|skin)\\b"},
            new String[] {"protector", "\\b(protector|mica|vidrio|lamina|pelicula)\\b"},
            new String[] {"cargador", "\\b(cargador|base de carga|estacion de carga)\\b"},
            new String[] {"cable", "\\b(cable|adaptador)\\b"},
            new String[] {"soporte", "\\b(soporte|base)\\b"},
            new String[] {"correa", "\\b(correa|manilla)\\b"},
            new String[] {"repuesto", "\\b(repuestos?|refaccion|cuchilla|aspas|vaso|tapa|bateria)\\b"});

    /** Juegos, tarjetas de regalo y contenido digital: no son la consola ni un aparato. */
    private static final Pattern VIDEOJUEGO_AL_COMIENZO = Pattern.compile(
            "^(juegos?|videojuegos?)\\b(?! de\\b)|^(tarjeta|gift card|giftcard|membresia|suscripcion)\\b.*\\b"
                    + "(playstation|psn|xbox|nintendo|steam|regalo|plus|game pass)\\b");
    private static final Pattern CONTENIDO_DIGITAL = Pattern.compile("\\b(dlc|expansion|season pass|codigo digital)\\b");

    /** La publicación no trae el producto: solo la caja, el empaque o una réplica para exhibir. */
    static final List<String> SIN_PRODUCTO = List.of(
            "caja vacia", "cajas vacias", "solo caja", "solo la caja", "solo empaque", "solo el empaque",
            "sin equipo", "sin telefono", "sin celular", "sin consola", "no incluye equipo", "no incluye telefono",
            "empty box", "box only", "only box", "only the box", "solo para exhibicion", "dummy", "replica de exhibicion");

    /** "Funda para iPhone", "cargador compatible con Samsung". */
    private static final Pattern PARA_OTRO = Pattern.compile(
            "^(funda|forro|estuche|carcasa|protector|mica|vidrio|cable|cargador|adaptador|soporte|base|correa|"
                    + "manilla|repuesto|bateria|control|case|cover|cuchilla|aspas|vaso|tapa|jarra)\\b.*"
                    + "\\b(para|compatible|for)\\b");

    private record Regla(CategoriaProducto categoria, Pattern patron) {
    }

    /** Si dos tipos se nombran en el título, gana el que aparece primero; con empate, el de más arriba. */
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
     * Memorias y discos: el producto mismo es almacenamiento ("Memoria USB", "Tarjeta microSD", "Disco externo",
     * "SSD"). La palabra "USB" sola no cuenta: también aparece en los puertos de un celular o un portátil.
     */
    private static final Pattern ALMACENAMIENTO_PRODUCTO = Pattern.compile("\\b(memorias? usb|usb flash|flash drive|"
            + "pendrive|pen drive|memorias? flash|micro ?sd(hc|xc)?|tarjetas? (de memoria|sd|micro ?sd)|sd card|"
            + "memory card|discos?( duros?| externos?| solidos?| ssd| hdd)?|ssd|hdd|nvme)\\b");

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
        String conEspacios = " " + texto + " ";
        if (SIN_PRODUCTO.stream().anyMatch(frase -> conEspacios.contains(" " + frase + " "))) {
            return EMPAQUE;
        }
        String primera = texto.split(" ")[0];
        if (PRIMERA_PALABRA_ACCESORIO.contains(primera) || PARA_OTRO.matcher(texto).find()) {
            return ACCESORIO;
        }
        if (VIDEOJUEGO_AL_COMIENZO.matcher(texto).find() || CONTENIDO_DIGITAL.matcher(texto).find()) {
            return VIDEOJUEGO;
        }
        // Una memoria o un disco solo es el producto si se nombra antes que cualquier otro tipo: "Memoria microSD para
        // celular" es una memoria; "Celular 128 GB con ranura microSD" y "Portátil 512 GB SSD" no.
        java.util.regex.Matcher almacenamiento = ALMACENAMIENTO_PRODUCTO.matcher(texto);
        int posicionAlmacenamiento = almacenamiento.find() ? almacenamiento.start() : Integer.MAX_VALUE;
        // Gana el tipo que se nombra primero: "Consola portátil Q21" es una consola, no un computador portátil.
        CategoriaProducto elegida = DESCONOCIDA;
        int posicion = Integer.MAX_VALUE;
        for (Regla regla : REGLAS) {
            java.util.regex.Matcher otra = regla.patron().matcher(texto);
            if (otra.find() && otra.start() < posicion) {
                elegida = regla.categoria();
                posicion = otra.start();
            }
        }
        // "iPhone 17 Pro repuesto pantalla" es un repuesto, aunque nombre primero el celular; "con vaso de repuesto"
        // describe algo que viene incluido.
        if (REPUESTO.matcher(texto).find()) {
            return ACCESORIO;
        }
        java.util.regex.Matcher accesorio = ACCESORIO_EN_EL_TITULO.matcher(texto);
        if (accesorio.find() && accesorio.start() < posicion && !texto.startsWith("consola")) {
            return ACCESORIO;
        }
        if (elegida == DESCONOCIDA) {
            return posicionAlmacenamiento < Integer.MAX_VALUE ? ALMACENAMIENTO : DESCONOCIDA;
        }
        return posicion < posicionAlmacenamiento ? elegida : ALMACENAMIENTO;
    }

    /**
     * Qué tipo de accesorio es ("control", "funda", "protector"...), para que quien busca un control no reciba una
     * funda.
     *
     * @param titulo título o búsqueda
     * @return tipo de accesorio, o vacío si no se reconoce
     */
    public static String tipoAccesorio(String titulo) {
        String texto = normalizar(titulo == null ? "" : titulo);
        String elegido = "";
        int posicion = Integer.MAX_VALUE;
        for (String[] tipo : TIPOS_ACCESORIO) {
            java.util.regex.Matcher m = Pattern.compile(tipo[1]).matcher(texto);
            if (m.find() && m.start() < posicion) {
                elegido = tipo[0];
                posicion = m.start();
            }
        }
        return elegido;
    }

    /** @return si es algo que acompaña o reemplaza al producto (accesorio, juego, caja), no un producto en sí */
    public boolean esComplemento() {
        return this == ACCESORIO || this == VIDEOJUEGO || this == EMPAQUE;
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
        if (esComplemento() || otra.esComplemento()) {
            return false;
        }
        return this == DESCONOCIDA || otra == DESCONOCIDA;
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ñ]+", " ").strip();
    }
}
