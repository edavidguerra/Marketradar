package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saca las características de un producto de su título y de las características cortas que informa la tienda, y las
 * deja con nombre y unidad comunes para poder alinearlas en una tabla: "256GB", "256 gb" y "256 Gigas" son
 * "Almacenamiento: 256 GB"; {@code 6.1"} y "6,1 pulgadas" son "Pantalla: 6,1 pulgadas".
 *
 * <p>Reglas: solo se reconoce lo que está escrito de forma clara. Si un valor es ambiguo (dos capacidades distintas
 * sin decir cuál es la RAM) no se adivina. Solo se convierten unidades con equivalencia fija (1 TB = 1024 GB, como en
 * el resto del sistema; 1000 ml = 1 L; 1000 g = 1 kg). Lo que informó la tienda tiene prioridad sobre el título.</p>
 */
public final class NormalizadorEspecificaciones {

    /** Orden de las filas en la comparación. */
    public static final List<String> ORDEN = List.of("marca", "modelo", "version", "almacenamiento", "ram", "pantalla",
            "resolucion", "capacidad", "potencia", "peso", "color", "conectividad");

    private static final Map<String, String> ETIQUETAS = Map.ofEntries(
            Map.entry("marca", "Marca"), Map.entry("modelo", "Modelo o referencia"),
            Map.entry("version", "Versión"), Map.entry("almacenamiento", "Almacenamiento"),
            Map.entry("ram", "Memoria RAM"), Map.entry("pantalla", "Pantalla"),
            Map.entry("resolucion", "Resolución"), Map.entry("capacidad", "Capacidad"),
            Map.entry("potencia", "Potencia"), Map.entry("peso", "Peso"), Map.entry("color", "Color"),
            Map.entry("conectividad", "Conectividad"));

    /** Características que identifican la variante: si cambian, ya no es el mismo producto. */
    static final List<String> DE_VARIANTE = List.of("modelo", "version", "almacenamiento", "ram", "pantalla",
            "capacidad", "color");

    private static final String NUM = "(\\d{1,4}(?:[.,]\\d{1,2})?)";

    private static final Pattern RAM_Y_ALMACENAMIENTO =
            Pattern.compile("\\b(\\d{1,2})\\s?(?:gb)?\\s?[+/]\\s?(\\d{2,4})\\s?(gb|tb)\\b");
    private static final Pattern RAM_ANTES = Pattern.compile("\\b(\\d{1,2})\\s?gb\\s?(?:de\\s)?(?:memoria\\s)?ram\\b");
    private static final Pattern RAM_DESPUES = Pattern.compile("\\bram\\s?(?:de\\s)?(\\d{1,2})\\s?gb\\b");
    private static final Pattern ALMACENAMIENTO_DESPUES = Pattern.compile(
            "\\b(\\d{1,4})\\s?(gb|tb)\\s?(?:de\\s)?(?:almacenamiento|rom|internos?|internas?|ssd|hdd)\\b");
    private static final Pattern ALMACENAMIENTO_ANTES = Pattern.compile(
            "\\b(?:almacenamiento|rom|ssd|hdd)\\s?(?:interno\\s|interna\\s)?(?:de\\s)?(\\d{1,4})\\s?(gb|tb)\\b");
    private static final Pattern CAPACIDAD_DIGITAL = Pattern.compile("\\b(\\d{1,4})\\s?(gb|tb|gigas?|teras?)\\b");
    private static final Pattern PANTALLA = Pattern.compile(
            "(?<![\\d.,])" + NUM + "\\s?(\"|pulgadas|pulgada|pulg\\b\\.?|inch(?:es)?\\b)");
    private static final Pattern LITROS = Pattern.compile("\\b" + NUM + "\\s?(litros?|lts?|l)\\b");
    private static final Pattern MILILITROS = Pattern.compile("\\b(\\d{2,5})\\s?ml\\b");
    private static final Pattern POTENCIA = Pattern.compile("\\b(\\d{2,5})\\s?(w|watts?|vatios)\\b");
    private static final Pattern KILOS = Pattern.compile("\\b" + NUM + "\\s?(kg|kgs|kilos?|kilogramos?)\\b");
    private static final Pattern GRAMOS = Pattern.compile("\\b(\\d{2,5})\\s?(gr|gramos)\\b");
    private static final Pattern REFERENCIA = Pattern.compile(
            "\\b(?=[a-z0-9-]*[a-z])(?=[a-z0-9-]*\\d)[a-z0-9]{2,}(?:-[a-z0-9]+)*\\b");
    private static final Pattern UNIDAD_PEGADA = Pattern.compile(
            "^\\d+(gb|tb|w|l|lt|lts|ml|kg|gr|mah|hz|mp|p|k|g|v|mm|cm|in)$");

    /** Resolución: de la más específica a la menos, con el nombre común. */
    private static final List<String[]> RESOLUCIONES = List.of(
            new String[] {"\\b8k\\b", "8K"},
            new String[] {"\\b(4k|uhd|ultra hd)\\b", "4K UHD"},
            new String[] {"\\b(qhd|2k)\\b", "QHD"},
            new String[] {"\\b(full hd|fhd|1080p)\\b", "Full HD (1080p)"},
            new String[] {"\\b(hd|720p)\\b", "HD (720p)"});

    /** Colores en español e inglés, con el nombre en español. Los de dos palabras van primero. */
    private static final List<String[]> COLORES = List.of(
            new String[] {"titanio natural|natural titanium", "Titanio natural"},
            new String[] {"titanio negro|black titanium", "Titanio negro"},
            new String[] {"titanio azul|blue titanium", "Titanio azul"},
            new String[] {"titanio blanco|white titanium", "Titanio blanco"},
            new String[] {"azul medianoche|midnight", "Medianoche"},
            new String[] {"negro|negra|black", "Negro"}, new String[] {"blanco|blanca|white", "Blanco"},
            new String[] {"azul|blue", "Azul"}, new String[] {"rojo|roja|red", "Rojo"},
            new String[] {"verde|green", "Verde"}, new String[] {"gris|gray|grey|grafito|graphite", "Gris"},
            new String[] {"plateado|plateada|plata|silver", "Plateado"},
            new String[] {"dorado|dorada|gold", "Dorado"}, new String[] {"rosado|rosada|rosa|pink", "Rosado"},
            new String[] {"morado|morada|lila|purple|violeta", "Morado"},
            new String[] {"amarillo|amarilla|yellow", "Amarillo"});

    private static final List<String[]> CONEXIONES = List.of(
            new String[] {"\\b5g\\b", "5G"}, new String[] {"\\b(4g|lte)\\b", "4G"},
            new String[] {"\\bwi ?-?fi\\b", "Wi-Fi"}, new String[] {"\\bbluetooth\\b", "Bluetooth"},
            new String[] {"\\bnfc\\b", "NFC"});

    /** Marcas comunes, con el nombre bien escrito. iPhone, iPad y MacBook son de Apple. */
    private static final Map<String, String> MARCAS = marcas();

    private static Map<String, String> marcas() {
        Map<String, String> marcas = new LinkedHashMap<>();
        for (String marca : List.of("Samsung", "Apple", "Xiaomi", "Motorola", "LG", "Sony", "Lenovo", "HP", "Asus",
                "Acer", "Huawei", "Oster", "Kalley", "Hyundai", "Philips", "Panasonic", "Nintendo", "Microsoft", "Dell",
                "Realme", "Oppo", "Honor", "Tecno", "Infinix", "Haceb", "Mabe", "Whirlpool", "Challenger", "Imusa",
                "Omron", "Google", "TCL", "Hisense", "JBL", "Bose", "Garmin", "Amazfit", "Electrolux", "Black+Decker",
                "Ninja", "Nokia", "Vivo", "ZTE", "Alcatel", "Redmi", "Poco")) {
            marcas.put(marca.toLowerCase(Locale.ROOT), marca);
        }
        marcas.put("iphone", "Apple");
        marcas.put("ipad", "Apple");
        marcas.put("macbook", "Apple");
        marcas.put("airpods", "Apple");
        marcas.put("galaxy", "Samsung");
        marcas.put("playstation", "Sony");
        marcas.put("xbox", "Microsoft");
        marcas.put("pixel", "Google");
        return marcas;
    }

    private NormalizadorEspecificaciones() {
    }

    /**
     * Características de un producto.
     *
     * @param titulo título de la publicación
     * @param detalles características que informó la tienda (pueden faltar)
     * @return características en el orden de {@link #ORDEN}; solo las que se pudieron leer con claridad
     */
    public static List<Especificacion> extraer(String titulo, List<String> detalles) {
        Map<String, Especificacion> encontradas = new LinkedHashMap<>();
        if (detalles != null) {
            for (String detalle : detalles) {
                leer(preparar(detalle), Especificacion.FUENTE_TIENDA, encontradas);
            }
        }
        String texto = preparar(titulo);
        leer(texto, Especificacion.FUENTE_TITULO, encontradas);
        marca(texto).ifPresent(m -> encontradas.putIfAbsent("marca", nueva("marca", m, null, Especificacion.FUENTE_TITULO)));
        version(texto).ifPresent(v -> encontradas.putIfAbsent("version",
                nueva("version", v, null, Especificacion.FUENTE_TITULO)));
        modelo(texto, encontradas).ifPresent(m -> encontradas.putIfAbsent("modelo",
                nueva("modelo", m, null, Especificacion.FUENTE_TITULO)));
        List<Especificacion> ordenadas = new ArrayList<>();
        for (String clave : ORDEN) {
            Especificacion especificacion = encontradas.get(clave);
            if (especificacion != null) {
                ordenadas.add(especificacion);
            }
        }
        return ordenadas;
    }

    /**
     * Nombre para mostrar de una característica.
     *
     * @param clave clave de {@link #ORDEN}
     * @return etiqueta
     */
    public static String etiqueta(String clave) {
        return ETIQUETAS.getOrDefault(clave, clave);
    }

    /**
     * Qué identifica la variante: modelo, versión, capacidades, pantalla y color, en minúsculas.
     *
     * @param especificaciones características del producto
     * @return valores por clave; solo las que se conocen
     */
    public static Map<String, String> variante(List<Especificacion> especificaciones) {
        Map<String, String> variante = new LinkedHashMap<>();
        for (Especificacion especificacion : especificaciones) {
            if (DE_VARIANTE.contains(especificacion.clave())) {
                variante.put(especificacion.clave(), especificacion.valor().toLowerCase(Locale.ROOT));
            }
        }
        return variante;
    }

    /**
     * Texto corto de la variante para guardarla, por ejemplo {@code modelo=iphone 15;version=pro;almacenamiento=256 gb}.
     *
     * @param titulo título
     * @param detalles características que informó la tienda
     * @return texto de máximo 200 caracteres
     */
    public static String claveVariante(String titulo, List<String> detalles) {
        StringBuilder clave = new StringBuilder();
        variante(extraer(titulo, detalles)).forEach((nombre, valor) -> {
            if (!clave.isEmpty()) {
                clave.append(';');
            }
            clave.append(nombre).append('=').append(valor);
        });
        return clave.length() <= 200 ? clave.toString() : clave.substring(0, 200);
    }

    /**
     * Compara dos variantes guardadas con {@link #claveVariante}. Son distintas si alguna característica que se conoce en
     * las dos tiene otro valor, o si una dice una versión ("Pro") y la otra no.
     *
     * @param antes clave de la variante anterior (puede ser {@code null} si no se conocía)
     * @param ahora clave de la variante actual
     * @return {@code true} si no hay nada que diga que cambió
     */
    public static boolean mismaVariante(String antes, String ahora) {
        if (antes == null || ahora == null) {
            return true;
        }
        Map<String, String> a = leerClave(antes);
        Map<String, String> b = leerClave(ahora);
        for (String clave : DE_VARIANTE) {
            String valorA = a.get(clave);
            String valorB = b.get(clave);
            if (valorA != null && valorB != null && !valorA.equals(valorB)) {
                return false;
            }
            if ("version".equals(clave) && (valorA == null) != (valorB == null)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> leerClave(String clave) {
        Map<String, String> valores = new LinkedHashMap<>();
        for (String parte : clave.split(";")) {
            int igual = parte.indexOf('=');
            if (igual > 0) {
                valores.put(parte.substring(0, igual), parte.substring(igual + 1));
            }
        }
        return valores;
    }

    private static void leer(String texto, String fuente, Map<String, Especificacion> encontradas) {
        if (texto.isEmpty()) {
            return;
        }
        Capacidades capacidades = leerCapacidades(texto);
        if (!capacidades.ram().isEmpty()) {
            int gb = capacidades.ram().iterator().next();
            poner(encontradas, nueva("ram", gb + " GB", BigDecimal.valueOf(gb), fuente));
        }
        if (!capacidades.almacenamiento().isEmpty()) {
            int gb = capacidades.almacenamiento().iterator().next();
            poner(encontradas, nueva("almacenamiento", textoGigas(gb), BigDecimal.valueOf(gb), fuente));
        } else {
            // Una capacidad suelta solo es el almacenamiento si es de 32 GB o más (las menores suelen ser la RAM) y si
            // es la única: con dos distintas no se sabe cuál es cuál.
            Set<Integer> candidatas = new TreeSet<>();
            capacidades.sueltas().stream().filter(gb -> gb >= 32).forEach(candidatas::add);
            if (candidatas.size() == 1) {
                int gb = candidatas.iterator().next();
                poner(encontradas, nueva("almacenamiento", textoGigas(gb), BigDecimal.valueOf(gb), fuente));
            }
        }
        Matcher pantalla = PANTALLA.matcher(texto);
        if (pantalla.find()) {
            BigDecimal pulgadas = numero(pantalla.group(1));
            if (pulgadas.compareTo(BigDecimal.ONE) >= 0 && pulgadas.compareTo(new BigDecimal("120")) <= 0) {
                poner(encontradas, nueva("pantalla", textoNumero(pulgadas) + " pulgadas", pulgadas, fuente));
            }
        }
        for (String[] resolucion : RESOLUCIONES) {
            if (Pattern.compile(resolucion[0]).matcher(texto).find()) {
                poner(encontradas, nueva("resolucion", resolucion[1], null, fuente));
                break;
            }
        }
        Matcher litros = LITROS.matcher(texto);
        Matcher mililitros = MILILITROS.matcher(texto);
        if (litros.find()) {
            BigDecimal valor = numero(litros.group(1));
            poner(encontradas, nueva("capacidad", textoNumero(valor) + " L", valor, fuente));
        } else if (mililitros.find()) {
            BigDecimal valor = new BigDecimal(mililitros.group(1)).divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            poner(encontradas, nueva("capacidad", textoNumero(valor) + " L", valor, fuente));
        }
        Matcher potencia = POTENCIA.matcher(texto);
        if (potencia.find()) {
            BigDecimal vatios = new BigDecimal(potencia.group(1));
            poner(encontradas, nueva("potencia", vatios.toPlainString() + " W", vatios, fuente));
        }
        Matcher kilos = KILOS.matcher(texto);
        Matcher gramos = GRAMOS.matcher(texto);
        if (kilos.find()) {
            BigDecimal valor = numero(kilos.group(1));
            poner(encontradas, nueva("peso", textoNumero(valor) + " kg", valor, fuente));
        } else if (gramos.find()) {
            BigDecimal valor = new BigDecimal(gramos.group(1)).divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            poner(encontradas, nueva("peso", textoNumero(valor) + " kg", valor, fuente));
        }
        for (String[] color : COLORES) {
            if (Pattern.compile("\\b(" + color[0] + ")\\b").matcher(texto).find()) {
                poner(encontradas, nueva("color", color[1], null, fuente));
                break;
            }
        }
        List<String> conexiones = new ArrayList<>();
        for (String[] conexion : CONEXIONES) {
            if (Pattern.compile(conexion[0]).matcher(texto).find()) {
                conexiones.add(conexion[1]);
            }
        }
        if (!conexiones.isEmpty()) {
            poner(encontradas, nueva("conectividad", String.join(", ", conexiones), null, fuente));
        }
    }

    private static Optional<String> marca(String texto) {
        for (String palabra : texto.split("[^a-z0-9+]+")) {
            String marca = MARCAS.get(palabra);
            if (marca != null) {
                return Optional.of(marca);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> version(String texto) {
        List<String> versiones = new ArrayList<>();
        for (String palabra : texto.split("[^a-z0-9]+")) {
            if (RequisitosBusqueda.PALABRAS_VERSION.contains(palabra) && !versiones.contains(palabra)) {
                versiones.add(palabra);
            }
        }
        if (versiones.isEmpty()) {
            return Optional.empty();
        }
        List<String> bonitas = versiones.stream()
                .map(v -> v.length() <= 2 ? v.toUpperCase(Locale.ROOT) : Character.toUpperCase(v.charAt(0)) + v.substring(1))
                .toList();
        return Optional.of(String.join(" ", bonitas));
    }

    /**
     * Una referencia del fabricante ("UN55CU7000", "SM-A546") o, si no hay, la serie y el número ("iPhone 15").
     * Los números que ya son otra característica (55 pulgadas, 600 W) no cuentan como modelo.
     */
    private static Optional<String> modelo(String texto, Map<String, Especificacion> encontradas) {
        Set<String> numerosUsados = new LinkedHashSet<>();
        encontradas.values().stream().map(Especificacion::numero).filter(Objects::nonNull)
                .forEach(n -> numerosUsados.add(n.stripTrailingZeros().toPlainString()));
        Matcher referencia = REFERENCIA.matcher(texto);
        while (referencia.find()) {
            String candidata = referencia.group();
            String sinGuion = candidata.replace("-", "");
            if (sinGuion.length() >= 5 && !UNIDAD_PEGADA.matcher(sinGuion).matches()
                    && !candidata.matches("\\d+[a-z]{1,3}") && !candidata.contains("pulgada")) {
                return Optional.of(candidata.toUpperCase(Locale.ROOT));
            }
        }
        for (RequisitosBusqueda.Modelo modelo : RequisitosBusqueda.de(texto).modelos()) {
            boolean soloNumero = modelo.letras().isEmpty() && modelo.pegado().isEmpty();
            if (soloNumero && (modelo.anterior().isEmpty() || numerosUsados.contains(modelo.numero())
                    || !MARCAS.containsKey(modelo.anterior()) && !SERIES.contains(modelo.anterior()))) {
                continue;
            }
            if (!soloNumero && UNIDAD_PEGADA.matcher(modelo.toString().replace(" ", "")).matches()) {
                continue;
            }
            return Optional.of(bonito(modelo.toString()));
        }
        return Optional.empty();
    }

    /** Palabras que van antes del número del modelo y no son marcas. */
    private static final Set<String> SERIES = Set.of("iphone", "ipad", "galaxy", "redmi", "note", "moto", "pixel",
            "playstation", "xbox", "watch", "airpods", "macbook", "switch", "series", "serie", "tab", "poco", "mi");

    private static String bonito(String modelo) {
        List<String> partes = new ArrayList<>();
        for (String parte : modelo.split(" ")) {
            if (parte.startsWith("iphone") || parte.startsWith("ipad")) {
                partes.add("i" + Character.toUpperCase(parte.charAt(1)) + parte.substring(2));
            } else if (parte.matches("[a-z]\\d+[a-z]*") || parte.length() <= 2) {
                partes.add(parte.toUpperCase(Locale.ROOT));
            } else {
                partes.add(Character.toUpperCase(parte.charAt(0)) + parte.substring(1));
            }
        }
        return String.join(" ", partes);
    }

    private static Especificacion capacidadDigital(String clave, String numero, String unidad, String fuente) {
        int gb = gigas(numero, unidad);
        return nueva(clave, textoGigas(gb), BigDecimal.valueOf(gb), fuente);
    }

    private static int gigas(String numero, String unidad) {
        int valor = Integer.parseInt(numero);
        return unidad.startsWith("t") ? valor * 1024 : valor;
    }

    private static String textoGigas(int gb) {
        return gb >= 1024 && gb % 1024 == 0 ? (gb / 1024) + " TB" : gb + " GB";
    }

    private static BigDecimal numero(String texto) {
        return new BigDecimal(texto.replace(',', '.')).stripTrailingZeros();
    }

    private static String textoNumero(BigDecimal numero) {
        return numero.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static void poner(Map<String, Especificacion> encontradas, Especificacion especificacion) {
        encontradas.putIfAbsent(especificacion.clave(), especificacion);
    }

    private static Especificacion nueva(String clave, String valor, BigDecimal numero, String fuente) {
        return new Especificacion(clave, etiqueta(clave), valor, numero, fuente);
    }

    /**
     * Capacidades de un texto separadas por lo que dicen ser. La búsqueda ({@link RequisitosBusqueda}) y la tabla de
     * comparación leen el mismo título con esta misma regla.
     *
     * @param ram capacidades unidas a la palabra RAM ("4 GB RAM", "RAM de 6 GB") o primeras de "4+128 GB", en GB
     * @param almacenamiento capacidades que el texto dice que son almacenamiento ("128 GB de almacenamiento",
     *                       "256 GB ROM", segunda de "4+128 GB"), en GB
     * @param sueltas las demás ("128 GB" sola): no se sabe si son RAM o almacenamiento
     */
    public record Capacidades(Set<Integer> ram, Set<Integer> almacenamiento, Set<Integer> sueltas) {
    }

    /**
     * Lee las capacidades de un título o de una búsqueda.
     *
     * @param texto texto tal como llega
     * @return capacidades separadas en RAM, almacenamiento y sueltas (1 TB = 1024 GB)
     */
    public static Capacidades capacidades(String texto) {
        return leerCapacidades(preparar(texto));
    }

    private static Capacidades leerCapacidades(String texto) {
        Set<Integer> ram = new LinkedHashSet<>();
        Set<Integer> almacenamiento = new LinkedHashSet<>();
        Set<Integer> sueltas = new LinkedHashSet<>();
        String resto = texto;
        Matcher combo = RAM_Y_ALMACENAMIENTO.matcher(resto);
        if (combo.find() && Integer.parseInt(combo.group(1)) <= 32) {
            ram.add(Integer.parseInt(combo.group(1)));
            almacenamiento.add(gigas(combo.group(2), combo.group(3)));
            resto = resto.replace(combo.group(), " ");
        }
        for (Pattern patron : List.of(RAM_ANTES, RAM_DESPUES)) {
            Matcher leida = patron.matcher(resto);
            List<String> usados = new ArrayList<>();
            while (leida.find()) {
                ram.add(Integer.parseInt(leida.group(1)));
                usados.add(leida.group());
            }
            for (String usado : usados) {
                resto = resto.replace(usado, " ");
            }
        }
        for (Pattern patron : List.of(ALMACENAMIENTO_DESPUES, ALMACENAMIENTO_ANTES)) {
            Matcher leida = patron.matcher(resto);
            List<String> usados = new ArrayList<>();
            while (leida.find()) {
                almacenamiento.add(gigas(leida.group(1), leida.group(2)));
                usados.add(leida.group());
            }
            for (String usado : usados) {
                resto = resto.replace(usado, " ");
            }
        }
        Matcher suelta = CAPACIDAD_DIGITAL.matcher(resto);
        while (suelta.find()) {
            sueltas.add(gigas(suelta.group(1), suelta.group(2)));
        }
        return new Capacidades(Collections.unmodifiableSet(ram), Collections.unmodifiableSet(almacenamiento),
                Collections.unmodifiableSet(sueltas));
    }

    /** Minúsculas y sin tildes, pero conserva comillas, puntos, comas, "+" y "/" para leer medidas. */
    static String preparar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\u201C\u201D\u2033]|''", "\"")
                .replaceAll("[^a-z0-9ñ\"'.,+/\\- ]+", " ")
                .replaceAll("\\s+", " ").strip();
    }
}
