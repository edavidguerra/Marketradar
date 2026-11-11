package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.OfertaNormalizada;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deja los resultados que más se parecen a lo que la persona buscó.
 *
 * <ol>
 *   <li>Cuenta cuántas palabras de la búsqueda trae cada título ("licuadora oster" en "Licuadora Oster 600 W"
 *       son 2 de 2; en "Licuadora Imusa" es 1 de 2). Los números cuentan exactos: "playstation 5" no es
 *       "playstation 4". Se aceptan plurales ("ollas" y "olla") y abreviaturas comunes ("PS5", "TV").</li>
 *   <li>Se muestran primero los que tienen todas las palabras. Si son menos de {@value #MINIMO}, se agregan los
 *       que tienen una palabra menos, y así, para no dejar a la persona sin nada.</li>
 *   <li>Después se quitan los juegos y accesorios que no pidió ({@link FiltroComplementos}) y los precios
 *       absurdamente bajos, que casi siempre son de otro producto.</li>
 * </ol>
 */
final class FiltroRelevancia {

    static final int MINIMO = 3;

    /** Un precio por debajo de esta parte del precio del medio se considera de otro producto. */
    private static final BigDecimal FRACCION_MINIMA = new BigDecimal("0.25");

    /** Palabras que no dicen qué producto es. */
    private static final Set<String> VACIAS = Set.of("de", "del", "la", "el", "los", "las", "un", "una", "unos",
            "unas", "para", "con", "sin", "y", "o", "en", "por", "a", "al");

    /** Abreviaturas que usan las tiendas en los títulos. */
    private static final Map<String, String> ABREVIATURAS = abreviaturas();

    private FiltroRelevancia() {
    }

    /** En orden: "smart tv" se cambia antes que "tv". */
    private static Map<String, String> abreviaturas() {
        Map<String, String> mapa = new LinkedHashMap<>();
        mapa.put("ps5", "playstation 5");
        mapa.put("ps4", "playstation 4");
        mapa.put("ps3", "playstation 3");
        mapa.put("smart tv", "televisor");
        mapa.put("tv", "televisor");
        mapa.put("tele", "televisor");
        mapa.put("pc", "computador");
        mapa.put("laptop", "portatil");
        mapa.put("notebook", "portatil");
        mapa.put("celular", "telefono");
        return Collections.unmodifiableMap(mapa);
    }

    static List<OfertaNormalizada> filtrar(String termino, List<OfertaNormalizada> ofertas) {
        List<String> buscadas = palabras(termino);
        if (buscadas.isEmpty() || ofertas.size() <= MINIMO) {
            return sinPreciosAbsurdos(FiltroComplementos.filtrar(termino, ofertas));
        }
        // Puntaje: cuántas palabras de la búsqueda trae el título. Se conserva el orden de la tienda.
        TreeMap<Integer, List<OfertaNormalizada>> porPuntaje = new TreeMap<>(Comparator.reverseOrder());
        for (OfertaNormalizada oferta : ofertas) {
            porPuntaje.computeIfAbsent(coincidencias(buscadas, oferta.getTitulo()), k -> new ArrayList<>()).add(oferta);
        }
        List<OfertaNormalizada> elegidas = new ArrayList<>();
        for (Map.Entry<Integer, List<OfertaNormalizada>> grupo : porPuntaje.entrySet()) {
            if (grupo.getKey() == 0 && !elegidas.isEmpty()) {
                break;
            }
            elegidas.addAll(grupo.getValue());
            if (elegidas.size() >= MINIMO) {
                break;
            }
        }
        // Se respeta el orden original entre los elegidos.
        List<OfertaNormalizada> ordenadas = ofertas.stream().filter(elegidas::contains).toList();
        return sinPreciosAbsurdos(FiltroComplementos.filtrar(termino, ordenadas));
    }

    /**
     * Quita lo que cuesta menos de la cuarta parte del precio del medio: con "playstation 5", una consola de
     * $ 80.000 es una PlayStation vieja o un repuesto, no lo que la persona busca. Solo cuando quedan al menos
     * {@value #MINIMO} resultados.
     */
    static List<OfertaNormalizada> sinPreciosAbsurdos(List<OfertaNormalizada> ofertas) {
        if (ofertas.size() <= MINIMO) {
            return ofertas;
        }
        List<BigDecimal> precios = ofertas.stream().map(OfertaNormalizada::precioTotal).sorted().toList();
        BigDecimal minimo = precios.get(precios.size() / 2).multiply(FRACCION_MINIMA);
        List<OfertaNormalizada> razonables = ofertas.stream()
                .filter(oferta -> oferta.precioTotal().compareTo(minimo) >= 0).toList();
        return razonables.size() >= MINIMO ? razonables : ofertas;
    }

    static int coincidencias(List<String> buscadas, String titulo) {
        List<String> delTitulo = List.of(expandir(titulo).split(" "));
        int total = 0;
        for (String buscada : buscadas) {
            if (delTitulo.stream().anyMatch(palabra -> coincide(buscada, palabra))) {
                total++;
            }
        }
        return total;
    }

    private static boolean coincide(String buscada, String delTitulo) {
        if (buscada.equals(delTitulo)) {
            return true;
        }
        if (buscada.chars().anyMatch(Character::isDigit) || delTitulo.chars().anyMatch(Character::isDigit)) {
            return false;
        }
        String raizBuscada = raiz(buscada);
        String raizTitulo = raiz(delTitulo);
        return raizBuscada.length() >= 3 && raizBuscada.equals(raizTitulo);
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

    /** Escribe completas las abreviaturas: "Consola PS5" queda "consola playstation 5". */
    private static String expandir(String texto) {
        String expandido = " " + ServicioSugerencias.normalizar(texto) + " ";
        for (Map.Entry<String, String> abreviatura : ABREVIATURAS.entrySet()) {
            expandido = expandido.replace(" " + abreviatura.getKey() + " ", " " + abreviatura.getValue() + " ");
        }
        return expandido.strip();
    }

    static List<String> palabras(String termino) {
        Map<String, Boolean> unicas = new LinkedHashMap<>();
        for (String palabra : expandir(termino).split(" ")) {
            if (!palabra.isEmpty() && !VACIAS.contains(palabra)) {
                unicas.put(palabra, true);
            }
        }
        return List.copyOf(unicas.keySet());
    }
}
