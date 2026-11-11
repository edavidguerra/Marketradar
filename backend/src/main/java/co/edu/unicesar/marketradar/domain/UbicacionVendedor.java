package co.edu.unicesar.marketradar.domain;

import java.net.URI;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Desde dónde vende una tienda: Colombia, otro país o no se sabe. Google Shopping no lo dice, así que se deduce del
 * nombre de la tienda y de su dirección web ({@code .com.co}, "Colombia" en el nombre, tiendas conocidas como
 * eBay o Amazon). Sirve para avisarle a la persona que un producto del exterior puede tardar más y cobrar impuestos.
 *
 * @param alcance si vende desde Colombia, desde otro país o no se sabe
 * @param pais nombre del país en español, o {@code null} si no se sabe cuál es
 */
public record UbicacionVendedor(Alcance alcance, String pais) {

    /** Si la tienda vende desde Colombia, desde el exterior o no se pudo saber. */
    public enum Alcance { COLOMBIA, EXTERIOR, DESCONOCIDA }

    private static final UbicacionVendedor COLOMBIA = new UbicacionVendedor(Alcance.COLOMBIA, "Colombia");
    private static final UbicacionVendedor DESCONOCIDA = new UbicacionVendedor(Alcance.DESCONOCIDA, null);

    /** Tiendas colombianas que Google Shopping muestra sin "Colombia" en el nombre. */
    private static final List<String> TIENDAS_COLOMBIANAS = List.of(
            "exito", "alkosto", "ktronix", "falabella", "homecenter", "olimpica", "jumbo", "pepeganga",
            "panamericana", "linio", "addi", "luegopago", "mercadolibre", "mercado libre", "rappi", "colsubsidio",
            "flamingo", "electrojaponesa", "mac center", "ishop", "dafiti", "comfandi", "la 14", "tiendas d1",
            "alkomprar", "corbeta", "sodimac", "constructor", "easy colombia", "tecnoplaza", "puntos colombia");

    /** Tiendas grandes del exterior y el país desde donde suelen enviar; {@code null} si cambia según el vendedor. */
    private static final Map<String, String> TIENDAS_EXTERIOR = new LinkedHashMap<>();

    /** Países que a veces vienen en el nombre de la tienda, como "Mercado Libre México". */
    private static final Map<String, String> PAISES = new LinkedHashMap<>();

    /** Terminación de la dirección web y su país. Van primero las de dos partes, como {@code .co.uk}. */
    private static final Map<String, String> DOMINIOS = new LinkedHashMap<>();

    static {
        TIENDAS_EXTERIOR.put("ebay", null);
        TIENDAS_EXTERIOR.put("amazon", "Estados Unidos");
        TIENDAS_EXTERIOR.put("walmart", "Estados Unidos");
        TIENDAS_EXTERIOR.put("best buy", "Estados Unidos");
        TIENDAS_EXTERIOR.put("bestbuy", "Estados Unidos");
        TIENDAS_EXTERIOR.put("newegg", "Estados Unidos");
        TIENDAS_EXTERIOR.put("target", "Estados Unidos");
        TIENDAS_EXTERIOR.put("b&h", "Estados Unidos");
        TIENDAS_EXTERIOR.put("aliexpress", "China");
        TIENDAS_EXTERIOR.put("temu", "China");
        TIENDAS_EXTERIOR.put("shein", "China");
        TIENDAS_EXTERIOR.put("banggood", "China");
        TIENDAS_EXTERIOR.put("dhgate", "China");
        TIENDAS_EXTERIOR.put("noon", "Emiratos Árabes Unidos");

        PAISES.put("mexico", "México");
        PAISES.put("peru", "Perú");
        PAISES.put("chile", "Chile");
        PAISES.put("argentina", "Argentina");
        PAISES.put("ecuador", "Ecuador");
        PAISES.put("venezuela", "Venezuela");
        PAISES.put("panama", "Panamá");
        PAISES.put("espana", "España");
        PAISES.put("estados unidos", "Estados Unidos");
        PAISES.put("usa", "Estados Unidos");
        PAISES.put("china", "China");

        DOMINIOS.put(".com.co", "Colombia");
        DOMINIOS.put(".co.uk", "Reino Unido");
        DOMINIOS.put(".com.mx", "México");
        DOMINIOS.put(".com.br", "Brasil");
        DOMINIOS.put(".com.ar", "Argentina");
        DOMINIOS.put(".com.pe", "Perú");
        DOMINIOS.put(".com.ec", "Ecuador");
        DOMINIOS.put(".co", "Colombia");
        DOMINIOS.put(".mx", "México");
        DOMINIOS.put(".br", "Brasil");
        DOMINIOS.put(".ar", "Argentina");
        DOMINIOS.put(".pe", "Perú");
        DOMINIOS.put(".ec", "Ecuador");
        DOMINIOS.put(".cl", "Chile");
        DOMINIOS.put(".ve", "Venezuela");
        DOMINIOS.put(".pa", "Panamá");
        DOMINIOS.put(".es", "España");
        DOMINIOS.put(".it", "Italia");
        DOMINIOS.put(".de", "Alemania");
        DOMINIOS.put(".fr", "Francia");
        DOMINIOS.put(".uk", "Reino Unido");
        DOMINIOS.put(".us", "Estados Unidos");
        DOMINIOS.put(".ca", "Canadá");
        DOMINIOS.put(".cn", "China");
        DOMINIOS.put(".ae", "Emiratos Árabes Unidos");
    }

    /**
     * Deduce desde dónde vende la tienda.
     *
     * @param vendedor nombre de la tienda como lo trae la API, por ejemplo {@code "eBay - rossgames"}
     * @param url dirección de la oferta; las búsquedas de Google no cuentan
     * @return Colombia, otro país o desconocida
     */
    public static UbicacionVendedor de(String vendedor, String url) {
        String nombre = normalizar(vendedor);
        String servidor = servidor(url);
        for (String texto : new String[] {nombre, servidor}) {
            if (texto.isEmpty()) {
                continue;
            }
            if (texto.contains("colombia")) {
                return COLOMBIA;
            }
            for (Map.Entry<String, String> pais : PAISES.entrySet()) {
                if (contienePalabra(texto, pais.getKey())) {
                    return new UbicacionVendedor(Alcance.EXTERIOR, pais.getValue());
                }
            }
            for (Map.Entry<String, String> tienda : TIENDAS_EXTERIOR.entrySet()) {
                if (contienePalabra(texto, tienda.getKey())) {
                    return new UbicacionVendedor(Alcance.EXTERIOR, tienda.getValue());
                }
            }
            if (TIENDAS_COLOMBIANAS.stream().anyMatch(tienda -> contienePalabra(texto, tienda))) {
                return COLOMBIA;
            }
            UbicacionVendedor porDominio = porDominio(texto);
            if (porDominio != null) {
                return porDominio;
            }
        }
        return DESCONOCIDA;
    }

    /**
     * @return {@code true} si se sabe que la tienda vende desde otro país
     */
    public boolean desdeExterior() {
        return alcance == Alcance.EXTERIOR;
    }

    /**
     * Texto corto para mostrar después de "Vende desde": el país, "el exterior" si es de afuera pero no se sabe de
     * dónde, o {@code null} si no se sabe.
     *
     * @return por ejemplo {@code "Colombia"} o {@code "el exterior"}
     */
    public String texto() {
        return switch (alcance) {
            case COLOMBIA -> "Colombia";
            case EXTERIOR -> pais == null ? "el exterior" : pais;
            case DESCONOCIDA -> null;
        };
    }

    private static UbicacionVendedor porDominio(String texto) {
        // Solo la última palabra, para que "Tienda.es Colombia" o "mi.co tienda" no confundan.
        String ultima = texto.substring(texto.lastIndexOf(' ') + 1);
        for (Map.Entry<String, String> dominio : DOMINIOS.entrySet()) {
            if (ultima.endsWith(dominio.getKey()) && ultima.length() > dominio.getKey().length()) {
                return "Colombia".equals(dominio.getValue()) ? COLOMBIA
                        : new UbicacionVendedor(Alcance.EXTERIOR, dominio.getValue());
            }
        }
        return null;
    }

    /** "eBay" no debe encontrarse dentro de otra palabra, ni "target" dentro de "targeta". */
    private static boolean contienePalabra(String texto, String palabra) {
        int desde = 0;
        while (true) {
            int posicion = texto.indexOf(palabra, desde);
            if (posicion < 0) {
                return false;
            }
            int fin = posicion + palabra.length();
            boolean inicioLibre = posicion == 0 || !Character.isLetterOrDigit(texto.charAt(posicion - 1));
            boolean finLibre = fin == texto.length() || !Character.isLetter(texto.charAt(fin));
            if (inicioLibre && finLibre) {
                return true;
            }
            desde = posicion + 1;
        }
    }

    private static String servidor(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String servidor = URI.create(url.strip()).getHost();
            // Los enlaces de búsqueda de Google no dicen nada de la tienda.
            if (servidor == null || servidor.contains("google.")) {
                return "";
            }
            return servidor.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).strip();
    }
}
