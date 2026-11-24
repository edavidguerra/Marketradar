package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Una sola forma de leer búsquedas y títulos, para que la búsqueda, los filtros y la comparación entiendan lo mismo.
 *
 * <ul>
 *   <li><b>Conserva los decimales</b>: "3.5 pulgadas" y "3,5\"" quedan como {@code 3.5 pulgadas}, no como "3 5".
 *       Así el 5 de una pantalla de 3,5 pulgadas no se confunde con el modelo "PlayStation 5".</li>
 *   <li><b>Alias en los dos sentidos</b>: "PS5", "PS 5", "Play Station 5" y "PlayStation®5" se leen como
 *       {@code playstation 5}, tanto en lo que escribe la persona como en el título de la tienda. No depende de la IA.</li>
 *   <li><b>Separa números de modelo de las especificaciones</b>: un número con unidad ("13 pulgadas", "600 W",
 *       "128 GB"), con decimales ("5.0"), de conectividad ("Bluetooth 5.0", "5G", "Wi-Fi 6") o de cantidad
 *       ("10000 juegos", "2 unidades") es una especificación, nunca un modelo.</li>
 * </ul>
 */
public final class TextoProducto {

    /** Unidades que, escritas justo después de un número, lo vuelven una medida o una cantidad. */
    private static final Set<String> UNIDADES = Set.of("gb", "tb", "mb", "giga", "gigas", "tera", "teras", "w", "watts",
            "watt", "vatios", "l", "lt", "lts", "litro", "litros", "ml", "kg", "kgs", "kilo", "kilos", "gr", "gramos", "g",
            "mah", "hz", "ghz", "mhz", "mp", "megapixeles", "p", "k", "v", "voltios", "mm", "cm", "m", "metros",
            "pulgadas", "pulgada", "pulg", "inch", "inches", "velocidades", "puertas", "puestos", "piezas", "pzs",
            "unidades", "und", "uds", "pares", "anos", "meses", "horas", "minutos", "juegos", "canales", "nucleos",
            "bits", "rpm", "psi", "btu", "libras", "lb", "lbs", "onzas", "oz", "quemadores", "personas", "tazas",
            "niveles", "funciones", "programas", "hojas");

    /** Palabras que, justo antes de un número, dicen que es una versión de conectividad o una cantidad. */
    private static final Set<String> ANTES_DE_ESPECIFICACION = Set.of("bluetooth", "bt", "wifi", "fi", "usb", "version",
            "v", "gen", "generacion", "android", "ios", "hdmi", "cat", "ip", "x", "pack", "por", "ram", "rom", "dlss",
            "ddr", "lpddr", "pcie", "tipo", "type", "serie", "ref");

    /** Un número con la unidad pegada: "600w", "5g", "4k", "128gb", "13pulgadas". */
    private static final Pattern UNIDAD_PEGADA = Pattern.compile(
            "^\\d+(?:\\.\\d+)?(gb|tb|mb|w|l|lt|lts|ml|kg|gr|g|mah|hz|ghz|mhz|mp|p|k|v|mm|cm|m|pulgadas|pulg|in)$");

    /** Alias de nombres de producto. Se aplican igual a la búsqueda y al título. */
    private static final Map<Pattern, String> ALIAS = alias();

    /** Abreviaturas comunes en los títulos, para comparar palabras ("TV" es "televisor"). */
    private static final Map<String, String> ABREVIATURAS = abreviaturas();

    /** Palabras que no dicen qué producto es. */
    static final Set<String> VACIAS = Set.of("de", "del", "la", "el", "los", "las", "un", "una", "unos", "unas", "para",
            "con", "sin", "y", "o", "en", "por", "a", "al", "e", "mi", "su", "+", "&");

    private TextoProducto() {
    }

    private static Map<Pattern, String> alias() {
        Map<Pattern, String> mapa = new LinkedHashMap<>();
        mapa.put(Pattern.compile("\\bplay ?station ?([1-5])\\b"), "playstation $1");
        mapa.put(Pattern.compile("\\bplay station\\b"), "playstation");
        mapa.put(Pattern.compile("\\bps ?([1-5])\\b"), "playstation $1");
        mapa.put(Pattern.compile("\\b(iphone|ipad|galaxy|pixel|xbox|redmi)(\\d)"), "$1 $2");
        return Collections.unmodifiableMap(mapa);
    }

    /** En orden: "smart tv" se cambia antes que "tv". */
    private static Map<String, String> abreviaturas() {
        Map<String, String> mapa = new LinkedHashMap<>();
        mapa.put("smart tv", "televisor");
        mapa.put("tv", "televisor");
        mapa.put("tele", "televisor");
        mapa.put("pc", "computador");
        mapa.put("laptop", "portatil");
        mapa.put("notebook", "portatil");
        mapa.put("celular", "telefono");
        return Collections.unmodifiableMap(mapa);
    }

    /**
     * Texto en minúsculas, sin tildes ni signos, con los decimales y los alias resueltos.
     *
     * @param texto búsqueda o título
     * @return texto leído, palabras separadas por un espacio
     */
    public static String leer(String texto) {
        String limpio = NormalizadorEspecificaciones.preparar(texto)
                // 3,5" o 13 in: pulgadas.
                .replaceAll("(\\d)\\s?(\"|''|pulg\\b\\.?|inch(?:es)?\\b)", "$1 pulgadas ")
                // Coma decimal: 1,25 litros es 1.25 litros. Una coma de miles (1,500,000) también queda unida.
                .replaceAll("(\\d),(\\d)", "$1.$2")
                // "S24+" es la versión Plus; "4+128 GB" es RAM y almacenamiento y no se toca.
                .replaceAll("([a-z0-9])\\+(?=\\s|$|[a-z])", "$1 plus ")
                .replaceAll("\\+", " + ")
                .replaceAll("[/'\"\\-,]", " ")
                // Un punto solo se conserva entre dos cifras.
                .replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ")
                .replaceAll("\\s+", " ").strip();
        for (Map.Entry<Pattern, String> alias : ALIAS.entrySet()) {
            limpio = alias.getKey().matcher(limpio).replaceAll(alias.getValue());
        }
        return limpio.replaceAll("\\s+", " ").strip();
    }

    /**
     * @param texto búsqueda o título
     * @return palabras leídas con {@link #leer(String)}
     */
    public static List<String> palabras(String texto) {
        String leido = leer(texto);
        return leido.isEmpty() ? List.of() : List.of(leido.split(" "));
    }

    /**
     * Si la palabra en la posición dada es un número que describe una característica (medida, cantidad,
     * conectividad, decimal, año) y no un modelo.
     *
     * @param palabras palabras leídas
     * @param i posición
     * @return {@code true} si es una especificación
     */
    static boolean esEspecificacion(List<String> palabras, int i) {
        String palabra = palabras.get(i);
        if (palabra.chars().noneMatch(Character::isDigit)) {
            return false;
        }
        if (palabra.contains(".") || UNIDAD_PEGADA.matcher(palabra).matches()) {
            return true;
        }
        if (palabra.matches("(19|20)\\d{2}")) {
            return true;
        }
        boolean soloNumero = palabra.matches("\\d+");
        if (soloNumero && i + 1 < palabras.size() && UNIDADES.contains(palabras.get(i + 1))) {
            return true;
        }
        return soloNumero && i > 0 && ANTES_DE_ESPECIFICACION.contains(palabras.get(i - 1));
    }

    /**
     * Una medida pedida o leída: "55 pulgadas", "600 W", "1.5 litros".
     *
     * @param valor número
     * @param unidad unidad común ({@code pulgadas}, {@code W}, {@code litros}, {@code ml}, {@code kg}, {@code mAh})
     */
    public record Medida(BigDecimal valor, String unidad) {
        @Override
        public String toString() {
            return valor.stripTrailingZeros().toPlainString().replace('.', ',') + " " + unidad;
        }

        boolean mismaQue(Medida otra) {
            return unidad.equals(otra.unidad) && valor.compareTo(otra.valor) == 0;
        }
    }

    private static final Map<String, String> UNIDAD_MEDIDA = Map.ofEntries(
            Map.entry("pulgadas", "pulgadas"), Map.entry("pulgada", "pulgadas"), Map.entry("pulg", "pulgadas"),
            Map.entry("in", "pulgadas"), Map.entry("inch", "pulgadas"), Map.entry("inches", "pulgadas"),
            Map.entry("w", "W"), Map.entry("watts", "W"), Map.entry("watt", "W"), Map.entry("vatios", "W"),
            Map.entry("l", "litros"), Map.entry("lt", "litros"), Map.entry("lts", "litros"), Map.entry("litro", "litros"),
            Map.entry("litros", "litros"), Map.entry("ml", "ml"), Map.entry("kg", "kg"), Map.entry("kgs", "kg"),
            Map.entry("kilos", "kg"), Map.entry("kilo", "kg"), Map.entry("mah", "mAh"));

    private static final Pattern MEDIDA_PEGADA = Pattern.compile("^(\\d+(?:\\.\\d+)?)(pulgadas|pulg|in|w|l|lt|lts|ml|kg|mah)$");

    /**
     * Medidas del texto (pantalla, potencia, capacidad en litros, peso, batería). El almacenamiento en GB no está
     * aquí: lo lee {@link NormalizadorEspecificaciones#capacidades(String)}.
     *
     * @param palabras palabras leídas
     * @return medidas en el orden del texto
     */
    static List<Medida> medidas(List<String> palabras) {
        List<Medida> medidas = new ArrayList<>();
        for (int i = 0; i < palabras.size(); i++) {
            String palabra = palabras.get(i);
            Matcher pegada = MEDIDA_PEGADA.matcher(palabra);
            if (pegada.matches()) {
                medidas.add(new Medida(new BigDecimal(pegada.group(1)), UNIDAD_MEDIDA.get(pegada.group(2))));
            } else if (palabra.matches("\\d+(?:\\.\\d+)?") && i + 1 < palabras.size()
                    && UNIDAD_MEDIDA.containsKey(palabras.get(i + 1))) {
                medidas.add(new Medida(new BigDecimal(palabra), UNIDAD_MEDIDA.get(palabras.get(i + 1))));
            }
        }
        return medidas;
    }

    /**
     * Marcas que nombra el texto, con su nombre común en minúsculas. Una serie dice su marca ("iPhone" es Apple,
     * "PlayStation" es Sony, "Redmi" es Xiaomi).
     *
     * @param palabras palabras leídas
     * @param incluirSeries si cuentan las series ("playstation") o solo las marcas escritas ("sony")
     * @return marcas, sin repetir
     */
    static Set<String> marcas(List<String> palabras, boolean incluirSeries) {
        Set<String> marcas = new java.util.LinkedHashSet<>();
        for (String palabra : palabras) {
            NormalizadorEspecificaciones.marcaDe(palabra, incluirSeries).ifPresent(marcas::add);
        }
        return marcas;
    }

    /**
     * @param palabra palabra leída
     * @return si es una serie ("iphone", "playstation", "galaxy") o una marca ("oster", "sony")
     */
    static boolean esSerieOMarca(String palabra) {
        return NormalizadorEspecificaciones.esSerie(palabra) || NormalizadorEspecificaciones.marcaDe(palabra, true)
                .isPresent();
    }

    /**
     * Palabras de la búsqueda que dicen qué producto es, para comparar con los títulos.
     *
     * @param termino lo que se buscó
     * @return palabras sin repetir, con las abreviaturas escritas completas
     */
    public static List<String> palabrasClave(String termino) {
        Map<String, Boolean> unicas = new LinkedHashMap<>();
        for (String palabra : expandir(leer(termino)).split(" ")) {
            if (!palabra.isEmpty() && !VACIAS.contains(palabra) && CondicionProducto.detectar(palabra).isEmpty()) {
                unicas.put(palabra, true);
            }
        }
        return List.copyOf(unicas.keySet());
    }

    /**
     * Cuántas de las palabras buscadas aparecen en el título, con plurales y abreviaturas. Los números cuentan
     * exactos y un decimal no es un número entero: el "5" no aparece en "3.5".
     *
     * @param buscadas palabras de {@link #palabrasClave(String)}
     * @param titulo título de la oferta
     * @return cuántas aparecen
     */
    public static int coincidencias(List<String> buscadas, String titulo) {
        List<String> delTitulo = List.of(expandir(leer(titulo)).split(" "));
        int total = 0;
        for (String buscada : buscadas) {
            if (delTitulo.stream().anyMatch(palabra -> coincide(buscada, palabra))) {
                total++;
            }
        }
        return total;
    }

    /**
     * @param buscada palabra buscada
     * @param titulo título leído
     * @return si la palabra aparece en el título
     */
    static boolean aparece(String buscada, List<String> titulo) {
        String expandida = expandir(buscada);
        List<String> partes = List.of(expandida.split(" "));
        List<String> delTitulo = List.of(expandir(String.join(" ", titulo)).split(" "));
        return partes.stream().allMatch(parte -> delTitulo.stream().anyMatch(palabra -> coincide(parte, palabra)));
    }

    private static boolean coincide(String buscada, String delTitulo) {
        if (buscada.equals(delTitulo)) {
            return true;
        }
        if (buscada.chars().anyMatch(Character::isDigit) || delTitulo.chars().anyMatch(Character::isDigit)) {
            return false;
        }
        String raizBuscada = raiz(buscada);
        return raizBuscada.length() >= 3 && raizBuscada.equals(raiz(delTitulo));
    }

    /** Quita el plural: "ollas" y "olla", "televisores" y "televisor". */
    private static String raiz(String palabra) {
        if (palabra.length() > 5 && palabra.endsWith("es")) {
            return palabra.substring(0, palabra.length() - 2);
        }
        if (palabra.length() > 3 && palabra.endsWith("s")) {
            return palabra.substring(0, palabra.length() - 1);
        }
        return palabra;
    }

    /** Escribe completas las abreviaturas: "smart tv samsung" queda "televisor samsung". */
    private static String expandir(String leido) {
        String expandido = " " + leido + " ";
        for (Map.Entry<String, String> abreviatura : ABREVIATURAS.entrySet()) {
            expandido = expandido.replace(" " + abreviatura.getKey() + " ", " " + abreviatura.getValue() + " ");
        }
        return expandido.strip();
    }

    /**
     * Un valor que solo sirve para comparar: la palabra con su abreviatura expandida.
     *
     * @param palabra palabra leída
     * @return palabra para comparar
     */
    static Optional<String> expandida(String palabra) {
        String expandida = expandir(palabra);
        return expandida.equals(palabra) ? Optional.empty() : Optional.of(expandida);
    }
}
