package co.edu.unicesar.marketradar.domain;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Nombre del vendedor tal como se puede mostrar. Los proveedores de datos (SerpApi) a veces mandan en el nombre de la
 * tienda marcadores técnicos: identificadores internos ("mc_9f3a77c1e2b44d0a8"), pares clave=valor
 * ("merchant_id=8841377"), plantillas sin llenar ("{{seller_name}}"), valores vacíos ("null", "undefined"), enlaces o
 * nombres de prueba de sus sistemas ("eBay - item_title_google_cloud_translation_test_for_us_es").
 *
 * <p>Se quita solo lo que es técnico. Si queda un nombre comercial con letras ("Alkosto" en
 * "Alkosto · merchant_id=12345"), se conserva; si no queda nada fiable, no hay nombre, y la página dice
 * "Vendedor no identificado". Nombres legítimos con cifras o signos ("3M Colombia", "Black+Decker Store", "D1",
 * "alkosto.com") no se tocan.</p>
 */
public final class NombreVendedor {

    /** Separadores entre el nombre y lo que el proveedor le pegó. */
    private static final Pattern SEPARADOR = Pattern.compile("\\s*(?:[·|•»]|\\s-\\s|\\(|\\)|\\[|\\]|,|;)\\s*");

    /** Partes que son técnicas aunque vengan solas. */
    private static final List<Pattern> TECNICOS = List.of(
            // clave=valor o clave: número ("merchant_id=8841377", "seller_id: 99812731", "srsltid=AfmBO...").
            Pattern.compile("(?i)^[a-z_]*(id|key|token|srsltid|gclid|utm_[a-z]+|ref|sku|merchant|seller)[a-z_]*\\s*[=:].*"),
            Pattern.compile("(?i).*[a-z_]+=[^\\s]*.*"),
            // Plantillas sin llenar: {{...}}, ${...}, %s, <...>.
            Pattern.compile(".*(\\{\\{|\\}\\}|\\$\\{|%s|<[^>]*>).*"),
            // Valores vacíos escritos como texto.
            Pattern.compile("(?i)^(null|undefined|nan|none|n/?a|\\[?object object\\]?|sin nombre|unknown|-+)$"),
            // Enlaces.
            Pattern.compile("(?i)^(https?://|www\\.google\\.).*"),
            // Identificador con guion bajo y cifras ("mc_9f3a77c1", "seller_12345").
            Pattern.compile("(?i)^[a-z]{1,10}_(?=[a-z0-9]*\\d)[a-z0-9]{5,}$"),
            // Identificadores en snake_case con palabras técnicas ("item_title_google_cloud_translation_test_for_us_es"),
            // o tan largos que no son un nombre. Un nombre corto con guion bajo ("tienda_la_14", "Mi_Tienda") se conserva.
            Pattern.compile("(?i)^(?=[a-z0-9]+(?:_[a-z0-9]+){2,}$).*(test|debug|sample|placeholder|translation|google|"
                    + "cloud|item_title|seller_name|merchant|lorem|dummy|tmp|undefined|null|example).*"),
            Pattern.compile("(?i)^[a-z0-9]+(?:_[a-z0-9]+){4,}$"),
            // Solo cifras largas (un número de comercio).
            Pattern.compile("^\\d{6,}$"),
            // Cadena larga sin espacios que mezcla letras y cifras (un hash o un token).
            Pattern.compile("^(?=[A-Za-z0-9_\\-]{16,}$)(?=.*\\d)(?=.*[A-Za-z])[A-Za-z0-9_\\-]+$"));

    private NombreVendedor() {
    }

    /**
     * @param recibido nombre que mandó el proveedor, o {@code null}
     * @return nombre comercial para mostrar, o vacío si no hay uno fiable
     */
    public static Optional<String> limpiar(String recibido) {
        if (recibido == null || recibido.isBlank()) {
            return Optional.empty();
        }
        String texto = recibido.strip();
        if (!esTecnico(texto)) {
            return Optional.of(texto);
        }
        // Se conserva la primera parte que sí es un nombre comercial.
        for (String parte : SEPARADOR.split(texto)) {
            String limpia = parte.strip();
            if (!limpia.isEmpty() && !esTecnico(limpia) && tieneLetras(limpia)) {
                return Optional.of(limpia);
            }
        }
        return Optional.empty();
    }

    /** Lo que se muestra cuando no queda un nombre fiable. */
    public static final String SIN_NOMBRE = "Vendedor no identificado";

    /**
     * @param recibido nombre que mandó el proveedor, o {@code null}
     * @return nombre limpio, o "Vendedor no identificado"
     */
    public static String paraMostrar(String recibido) {
        return limpiar(recibido).orElse(SIN_NOMBRE);
    }

    /**
     * @param recibido nombre que mandó el proveedor
     * @return si trae algún marcador técnico
     */
    public static boolean tieneMarcador(String recibido) {
        return recibido != null && !recibido.isBlank() && esTecnico(recibido.strip());
    }

    private static boolean esTecnico(String texto) {
        if (TECNICOS.stream().anyMatch(patron -> patron.matcher(texto).matches())) {
            return true;
        }
        // Un nombre con varias partes es técnico si alguna parte lo es ("Alkosto · merchant_id=12345").
        String[] partes = SEPARADOR.split(texto);
        return partes.length > 1 && java.util.Arrays.stream(partes).map(String::strip).filter(p -> !p.isEmpty())
                .anyMatch(p -> TECNICOS.stream().anyMatch(patron -> patron.matcher(p).matches()));
    }

    private static boolean tieneLetras(String texto) {
        return texto.codePoints().filter(Character::isLetter).count() >= 2
                || texto.matches("(?i)^[a-z]\\d{1,3}$");
    }

    /**
     * Versión del nombre recibido apta para los registros de diagnóstico: conserva la forma, pero oculta valores de
     * parámetros que podrían ser claves o tokens.
     *
     * @param recibido nombre que mandó el proveedor
     * @return texto para el registro
     */
    public static String paraDiagnostico(String recibido) {
        if (recibido == null) {
            return "null";
        }
        String recortado = recibido.length() > 120 ? recibido.substring(0, 120) + "…" : recibido;
        return recortado.replaceAll("(?i)((?:api_?key|key|token|secret|clave|password|auth)[a-z_]*\\s*[=:]\\s*)[^\\s&·|]+",
                "$1***");
    }
}
