package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.BusquedaDao;
import co.edu.unicesar.marketradar.dao.BusquedaDao.TerminoPopular;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Ayuda a escribir la búsqueda: sugiere productos mientras la persona escribe y propone una corrección
 * ("¿Quizás quiso decir…?") cuando una palabra parece mal escrita.
 *
 * <p>Usa una lista de productos comunes ({@code busqueda/vocabulario.txt}) y los términos que más se buscan en
 * Marketradar. No llama a ninguna API externa, así que no gasta búsquedas de las tiendas. Tolera tildes,
 * mayúsculas y errores de una o dos letras (distancia de Levenshtein).</p>
 */
@Service
public class ServicioSugerencias {

    /** Sugerencias que se muestran como máximo. */
    public static final int MAXIMO_SUGERENCIAS = 6;

    private static final Logger LOG = LoggerFactory.getLogger(ServicioSugerencias.class);
    private static final Duration VIGENCIA_POPULARES = Duration.ofMinutes(10);
    private static final int TERMINOS_POPULARES = 300;
    private static final int PESO_VOCABULARIO = 1;
    /** Un término popular entra al diccionario de corrección solo si se buscó varias veces (puede venir mal escrito). */
    private static final int VECES_PARA_CORREGIR = 5;

    /** Palabras que se quitan al comienzo de una frase: "quiero comprar una licuadora" busca "licuadora". */
    private static final Set<String> RELLENO_INICIAL = Set.of("hola", "buenas", "buenos", "dias", "tardes", "noches",
            "quiero", "quisiera", "necesito", "busco", "buscar", "buscando", "comprar", "compro", "compra", "me",
            "gustaria", "regalar", "ver", "mostrar", "muestreme", "muestrame", "donde", "puedo", "consigo", "encuentro",
            "hay", "algun", "alguna", "cuanto", "cuesta", "cuestan", "vale", "valen", "precio", "precios", "el", "la",
            "los", "las", "un", "una", "unos", "unas", "de", "del", "por", "favor", "porfa", "porfavor");

    /** Palabras que se quitan al final: "licuadora por favor" busca "licuadora". */
    private static final Set<String> RELLENO_FINAL = Set.of("por", "favor", "porfa", "porfavor", "gracias");

    /** Palabras de unión que no sirven solas como búsqueda. */
    private static final Set<String> UNIONES = Set.of("de", "del", "la", "el", "los", "las", "un", "una", "unos", "unas",
            "para", "por", "con", "sin", "y", "o", "en", "a", "al", "que", "mas", "muy", "tipo", "marca", "modelo");

    private final List<String> vocabulario;
    private final List<String> palabrasComunes;
    private final Supplier<List<TerminoPopular>> fuentePopulares;
    private final Clock reloj;

    private volatile Indice indice;
    private volatile Instant indiceCreadoEn = Instant.MIN;

    /**
     * Constructor que usa Spring.
     *
     * @param busquedaDao historial de búsquedas, para los términos más buscados
     */
    @Autowired
    public ServicioSugerencias(BusquedaDao busquedaDao) {
        this(leerVocabulario(), leerLista("/busqueda/palabras-comunes.txt"),
                () -> busquedaDao.terminosPopulares(TERMINOS_POPULARES), Clock.systemUTC());
    }

    /**
     * Constructor para las pruebas.
     *
     * @param vocabulario productos comunes
     * @param fuentePopulares términos más buscados
     * @param reloj reloj para renovar los términos populares
     */
    ServicioSugerencias(List<String> vocabulario, Supplier<List<TerminoPopular>> fuentePopulares, Clock reloj) {
        this(vocabulario, List.of(), fuentePopulares, reloj);
    }

    /**
     * Constructor para las pruebas, con palabras comunes que nunca se corrigen.
     *
     * @param vocabulario productos comunes
     * @param palabrasComunes palabras bien escritas que no son productos (colores, marcas, "para"...)
     * @param fuentePopulares términos más buscados
     * @param reloj reloj para renovar los términos populares
     */
    ServicioSugerencias(List<String> vocabulario, List<String> palabrasComunes,
                        Supplier<List<TerminoPopular>> fuentePopulares, Clock reloj) {
        this.vocabulario = List.copyOf(vocabulario);
        this.palabrasComunes = List.copyOf(palabrasComunes);
        this.fuentePopulares = fuentePopulares;
        this.reloj = reloj;
    }

    /**
     * Sugiere productos parecidos a lo que la persona va escribiendo.
     *
     * @param texto lo escrito hasta ahora
     * @return hasta {@value #MAXIMO_SUGERENCIAS} sugerencias, las más parecidas y buscadas primero
     */
    public List<String> sugerir(String texto) {
        String buscado = normalizar(texto);
        if (buscado.length() < 2 || buscado.length() > 100) {
            return List.of();
        }
        int tolerancia = tolerancia(buscado.length());
        List<Candidato> candidatos = new ArrayList<>();
        for (Termino termino : indice().terminos()) {
            int puntaje = puntaje(termino.normalizado(), buscado, tolerancia);
            if (puntaje > 0) {
                candidatos.add(new Candidato(termino, puntaje));
            }
        }
        return candidatos.stream()
                .sorted(Comparator.comparingInt(Candidato::puntaje).reversed()
                        .thenComparing(Comparator.comparingInt((Candidato c) -> c.termino().peso()).reversed())
                        .thenComparingInt(c -> c.termino().normalizado().length()))
                .limit(MAXIMO_SUGERENCIAS)
                .map(c -> c.termino().texto())
                .toList();
    }

    /**
     * Corrige las palabras que parecen mal escritas, por ejemplo "licuadorra" por "licuadora".
     *
     * @param termino lo que buscó la persona
     * @return la búsqueda corregida, o vacío si todo parece bien escrito
     */
    public Optional<String> corregir(String termino) {
        String normalizado = normalizar(termino);
        if (normalizado.isEmpty()) {
            return Optional.empty();
        }
        Indice actual = indice();
        String[] palabras = normalizado.split(" ");
        List<String> corregidas = new ArrayList<>();
        boolean cambio = false;
        for (String palabra : palabras) {
            String corregida = actual.palabras().containsKey(palabra) ? null : corregirPalabra(palabra, actual);
            if (corregida == null) {
                corregidas.add(actual.formaVisible(palabra));
            } else {
                corregidas.add(corregida);
                cambio = true;
            }
        }
        return cambio ? Optional.of(String.join(" ", corregidas)) : Optional.empty();
    }

    /**
     * Quita las palabras de relleno con que muchas personas empiezan o terminan una búsqueda, como en
     * "quiero comprar una olla arrocera por favor". Las palabras del medio no se tocan ("olla de presión").
     *
     * @param texto lo que escribió la persona
     * @return el producto, o el texto original si al quitar el relleno no queda nada
     */
    public static String limpiarFrase(String texto) {
        if (texto == null) {
            return "";
        }
        List<String> palabras = new ArrayList<>(List.of(texto.strip().split("\\s+")));
        while (!palabras.isEmpty() && RELLENO_INICIAL.contains(normalizar(palabras.get(0)))) {
            palabras.remove(0);
        }
        while (!palabras.isEmpty() && RELLENO_FINAL.contains(normalizar(palabras.get(palabras.size() - 1)))) {
            palabras.remove(palabras.size() - 1);
        }
        String limpio = String.join(" ", palabras).replaceAll("[¿?¡!.,;:]+$", "").strip();
        return limpio.length() >= 2 ? limpio : texto.strip();
    }

    /**
     * Palabra principal de una búsqueda larga, para intentar de nuevo cuando no hay resultados:
     * "licuadora oster roja de 10 velocidades" queda en "licuadora".
     *
     * @param termino búsqueda de dos o más palabras
     * @return la primera palabra que no es de unión, o vacío si la búsqueda ya es de una sola palabra
     */
    public static Optional<String> nucleo(String termino) {
        if (termino == null) {
            return Optional.empty();
        }
        String[] palabras = termino.strip().split("\\s+");
        if (palabras.length < 2) {
            return Optional.empty();
        }
        for (String palabra : palabras) {
            String normal = normalizar(palabra);
            if (normal.length() >= 3 && !UNIONES.contains(normal) && normal.chars().noneMatch(Character::isDigit)) {
                return Optional.of(palabra.replaceAll("[¿?¡!.,;:]", ""));
            }
        }
        return Optional.empty();
    }

    private static String corregirPalabra(String palabra, Indice indice) {
        if (palabra.length() < 3 || palabra.chars().anyMatch(Character::isDigit)) {
            return null;
        }
        int tolerancia = Math.max(1, tolerancia(palabra.length()));
        String sonido = sonido(palabra);
        String mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        int mejorPeso = -1;
        for (Map.Entry<String, Integer> entrada : indice.palabras().entrySet()) {
            String candidata = entrada.getKey();
            if (Math.abs(candidata.length() - palabra.length()) > tolerancia + 1) {
                continue;
            }
            // Errores que suenan igual en español (oya/olla, arrosera/arrocera) cuentan como uno solo.
            int distancia = Math.min(distancia(palabra, candidata, tolerancia),
                    sonido.equals(sonido(candidata)) ? 1 : distancia(sonido, sonido(candidata), tolerancia));
            if (distancia <= tolerancia && (distancia < mejorDistancia
                    || distancia == mejorDistancia && entrada.getValue() > mejorPeso)) {
                mejor = candidata;
                mejorDistancia = distancia;
                mejorPeso = entrada.getValue();
            }
        }
        return mejor == null ? null : indice.formaVisible(mejor);
    }

    /**
     * Cómo suena la palabra en español: ll y y, v y b, z, c suave y s, y la h muda se escriben igual.
     *
     * @param palabra palabra normalizada
     * @return forma fonética
     */
    static String sonido(String palabra) {
        return palabra.replace("ll", "y")
                .replace('v', 'b')
                .replace('z', 's')
                .replaceAll("c([ei])", "s$1")
                .replaceAll("qu([ei])", "k$1")
                .replaceAll("c([aou])", "k$1")
                .replace("h", "");
    }

    /** 3: empieza igual; 2: alguna palabra empieza igual; 1: se parece aunque tenga errores. */
    private static int puntaje(String termino, String buscado, int tolerancia) {
        if (termino.startsWith(buscado)) {
            return 3;
        }
        if (termino.contains(" " + buscado)) {
            return 2;
        }
        if (tolerancia == 0) {
            return 0;
        }
        String inicio = termino.length() > buscado.length() ? termino.substring(0, buscado.length()) : termino;
        return distancia(buscado, inicio, tolerancia) <= tolerancia ? 1 : 0;
    }

    /** Errores permitidos: ninguno en palabras cortas, uno desde 4 letras y dos desde 7. */
    static int tolerancia(int largo) {
        if (largo >= 7) {
            return 2;
        }
        return largo >= 4 ? 1 : 0;
    }

    /**
     * Distancia de Levenshtein (letras que hay que cambiar, quitar o agregar), cortada en {@code maximo + 1}.
     *
     * @param a primera palabra
     * @param b segunda palabra
     * @param maximo distancia a partir de la cual ya no interesa el valor exacto
     * @return distancia, o {@code maximo + 1} si es mayor
     */
    static int distancia(String a, String b, int maximo) {
        int[] anterior = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            int minimoFila = actual[0];
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + costo);
                minimoFila = Math.min(minimoFila, actual[j]);
            }
            if (minimoFila > maximo) {
                return maximo + 1;
            }
            int[] cambio = anterior;
            anterior = actual;
            actual = cambio;
        }
        return Math.min(anterior[b.length()], maximo + 1);
    }

    /**
     * Minúsculas, sin tildes, sin signos y con un solo espacio entre palabras.
     *
     * @param texto texto original
     * @return texto para comparar
     */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9ñ ]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private Indice indice() {
        Indice actual = indice;
        Instant ahora = reloj.instant();
        if (actual == null || ahora.isAfter(indiceCreadoEn.plus(VIGENCIA_POPULARES))) {
            synchronized (this) {
                if (indice == null || ahora.isAfter(indiceCreadoEn.plus(VIGENCIA_POPULARES))) {
                    indice = crearIndice(cargarPopulares());
                    indiceCreadoEn = ahora;
                }
                actual = indice;
            }
        }
        return actual;
    }

    private List<TerminoPopular> cargarPopulares() {
        try {
            return fuentePopulares.get();
        } catch (AccesoDatosException e) {
            LOG.warn("No se pudieron leer los términos más buscados; se sugiere solo con la lista de productos", e);
            return List.of();
        }
    }

    private Indice crearIndice(List<TerminoPopular> populares) {
        Map<String, Termino> terminos = new LinkedHashMap<>();
        Map<String, Integer> palabras = new HashMap<>();
        Map<String, String> visibles = new HashMap<>();
        for (TerminoPopular popular : populares) {
            agregar(popular.termino(), popular.veces() + PESO_VOCABULARIO, popular.veces() >= VECES_PARA_CORREGIR,
                    terminos, palabras, visibles);
        }
        for (String producto : vocabulario) {
            agregar(producto, PESO_VOCABULARIO, true, terminos, palabras, visibles);
        }
        // Las palabras comunes se reconocen como bien escritas, pero no se sugieren ni ganan a un producto.
        for (String comun : palabrasComunes) {
            String normal = normalizar(comun);
            if (!normal.isEmpty() && !normal.contains(" ")) {
                palabras.putIfAbsent(normal, 0);
                String visible = comun.strip().toLowerCase(Locale.ROOT);
                if (!visible.equals(normal)) {
                    visibles.putIfAbsent(normal, visible);
                }
            }
        }
        return new Indice(List.copyOf(terminos.values()), palabras, visibles);
    }

    private static void agregar(String texto, int peso, boolean sirveParaCorregir, Map<String, Termino> terminos,
                                Map<String, Integer> palabras, Map<String, String> visibles) {
        String limpio = texto == null ? "" : texto.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        String normalizado = normalizar(limpio);
        if (normalizado.length() < 2 || normalizado.length() > 60) {
            return;
        }
        Termino existente = terminos.get(normalizado);
        if (existente == null) {
            terminos.put(normalizado, new Termino(limpio, normalizado, peso));
        } else if (existente.texto().equals(normalizado) && !limpio.equals(normalizado)) {
            // Se prefiere la forma con tildes del vocabulario ("tensiómetro" en vez de "tensiometro").
            terminos.put(normalizado, new Termino(limpio, normalizado, existente.peso() + peso));
        } else {
            terminos.put(normalizado, new Termino(existente.texto(), normalizado, existente.peso() + peso));
        }
        if (!sirveParaCorregir) {
            return;
        }
        String[] originales = limpio.split(" ");
        String[] normales = normalizado.split(" ");
        for (int i = 0; i < normales.length; i++) {
            palabras.merge(normales[i], peso, Integer::sum);
            if (originales.length == normales.length && !originales[i].equals(normales[i])) {
                visibles.putIfAbsent(normales[i], originales[i]);
            }
        }
    }

    static List<String> leerVocabulario() {
        return leerLista("/busqueda/vocabulario.txt");
    }

    static List<String> leerLista(String recurso) {
        try (InputStream entrada = ServicioSugerencias.class.getResourceAsStream(recurso)) {
            if (entrada == null) {
                return List.of();
            }
            BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            return lector.lines()
                    .map(String::strip)
                    .filter(linea -> !linea.isEmpty() && !linea.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + recurso, e);
        }
    }

    private record Termino(String texto, String normalizado, int peso) {
    }

    private record Candidato(Termino termino, int puntaje) {
    }

    private record Indice(List<Termino> terminos, Map<String, Integer> palabras, Map<String, String> visibles) {

        String formaVisible(String palabra) {
            return visibles.getOrDefault(palabra, palabra);
        }
    }
}
