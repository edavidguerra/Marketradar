package co.edu.unicesar.marketradar.domain.mayorista;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decide si una publicación al por mayor es lo que se buscó, mirando el <b>tipo de producto</b> (aretes, collar,
 * pulsera...) y los <b>atributos</b> que se pidieron (material), no solo si comparte alguna palabra.
 *
 * <ul>
 *   <li>Reconoce sinónimos, singular y plural, y español e inglés ("aretes" = "earrings" = "ear studs").</li>
 *   <li>Si el título es de otro tipo de producto ("necklace" cuando se buscó "earrings") o de otro material
 *       ("sterling silver" cuando se buscó "stainless steel"), es {@link Nivel#INCOMPATIBLE} y no entra en la lista
 *       principal.</li>
 *   <li>Si el título no dice el tipo o el material, eso <b>no</b> se da por cumplido: queda {@link Nivel#DUDOSA},
 *       aparte y fuera del orden por precio.</li>
 *   <li>Los recubrimientos ("gold plated", "bañado en oro") no son el material: un arete de acero bañado en oro sigue
 *       siendo de acero.</li>
 * </ul>
 *
 * <p>Para las palabras que no reconoce no exige coincidencia literal de todas: basta con la mitad.</p>
 */
public final class RelevanciaMayorista {

    /** Qué tan seguro es que la publicación sea lo buscado. */
    public enum Nivel { COINCIDE, DUDOSA, INCOMPATIBLE }

    /**
     * @param nivel resultado
     * @param motivo explicación para la persona, o {@code null} si coincide
     */
    public record Evaluacion(Nivel nivel, String motivo) {
    }

    private enum Grupo { TIPO, MATERIAL }

    private record Concepto(String id, Grupo grupo, String nombre, List<String> frases) {
    }

    /** Ordenados de la frase más larga a la más corta al buscarlos, para que "ear ring" gane a "ring". */
    private static final List<Concepto> CONCEPTOS = List.of(
            tipo("ARETES", "aretes", "earring", "earrings", "ear ring", "ear rings", "ear stud", "ear studs", "stud earring",
                    "stud earrings", "studs", "huggie", "huggies", "hoop earrings", "ear cuff", "ear cuffs", "ear clip",
                    "ear clips", "ear hook", "ear hooks", "ear line", "arete", "aretes", "pendiente", "pendientes",
                    "zarcillo", "zarcillos", "topo", "topos", "candonga", "candongas"),
            tipo("PIERCING", "piercings", "nose ring", "nose rings", "nose stud", "nose studs", "belly ring", "belly rings",
                    "lip ring", "lip rings", "septum", "piercing", "piercings", "navel ring", "tongue ring"),
            tipo("COLLAR", "collares", "necklace", "necklaces", "choker", "chokers", "collar", "collares", "gargantilla",
                    "gargantillas"),
            tipo("DIJE", "dijes", "pendant", "pendants", "charm", "charms", "dije", "dijes"),
            tipo("PULSERA", "pulseras", "bracelet", "bracelets", "bangle", "bangles", "wristband", "wristbands", "pulsera",
                    "pulseras", "brazalete", "brazaletes", "manilla", "manillas", "esclava", "esclavas"),
            tipo("TOBILLERA", "tobilleras", "anklet", "anklets", "tobillera", "tobilleras"),
            tipo("ANILLO", "anillos", "ring", "rings", "finger ring", "anillo", "anillos", "sortija", "sortijas"),
            tipo("BROCHE", "broches", "brooch", "brooches", "broche", "broches"),
            tipo("LLAVERO", "llaveros", "keychain", "keychains", "key chain", "key chains", "keyring", "keyrings",
                    "key ring", "key rings", "llavero", "llaveros"),
            tipo("RELOJ", "relojes", "watch", "watches", "wristwatch", "reloj", "relojes"),
            tipo("FUNDA", "fundas", "phone case", "phone cases", "phone cover", "phone covers", "funda", "fundas",
                    "carcasa", "carcasas"),
            tipo("CABLE", "cables", "cable", "cables"),
            tipo("CARGADOR", "cargadores", "charger", "chargers", "cargador", "cargadores"),
            tipo("AUDIFONOS", "audífonos", "earphone", "earphones", "headphone", "headphones", "earbud", "earbuds",
                    "headset", "headsets", "audifono", "audifonos", "auricular", "auriculares"),
            tipo("PARLANTE", "parlantes", "speaker", "speakers", "parlante", "parlantes", "bocina", "bocinas"),
            tipo("BOLSO", "bolsos", "handbag", "handbags", "purse", "purses", "tote bag", "tote bags", "bolso", "bolsos",
                    "cartera", "carteras"),
            tipo("ZAPATOS", "zapatos", "shoe", "shoes", "sneaker", "sneakers", "zapato", "zapatos", "tenis"),
            tipo("CAMISETA", "camisetas", "t shirt", "t shirts", "tshirt", "tshirts", "camiseta", "camisetas"),
            tipo("MEDIAS", "medias", "sock", "socks", "medias", "calcetin", "calcetines"),
            tipo("GORRA", "gorras", "baseball cap", "baseball caps", "gorra", "gorras"),
            tipo("GAFAS", "gafas", "sunglasses", "eyeglasses", "gafas", "lentes de sol"),
            material("ACERO", "acero inoxidable", "stainless steel", "stainless", "surgical steel", "316l", "316 l",
                    "304 steel", "acero inoxidable", "acero quirurgico", "inox"),
            material("PLATA", "plata de ley (925)", "sterling silver", "925 silver", "silver 925", "s925", "925 sterling",
                    "plata 925", "plata de ley", "plata esterlina"),
            material("ORO", "oro macizo", "solid gold", "10k gold", "14k gold", "18k gold", "24k gold", "oro macizo",
                    "oro solido", "oro 18k", "oro 14k"),
            material("TITANIO", "titanio", "titanium", "titanio", "g23 titanium", "astm f136"),
            material("LATON", "latón", "brass", "laton"),
            material("COBRE", "cobre", "copper", "cobre"),
            material("ALEACION", "aleación", "alloy", "zinc alloy", "aleacion", "zamak"),
            material("TUNGSTENO", "tungsteno", "tungsten", "tungsteno"),
            material("ACRILICO", "acrílico o resina", "acrylic", "resin", "acrilico", "resina"),
            material("PLASTICO", "plástico", "plastic", "plastico"),
            material("MADERA", "madera", "wood", "wooden", "madera"),
            material("CUERO", "cuero", "leather", "pu leather", "cuero"));

    /** Se borran antes de buscar materiales: son recubrimientos o colores, no el material de la pieza. */
    private static final List<String> RECUBRIMIENTOS = List.of("rose gold plated", "18k gold plated", "14k gold plated",
            "24k gold plated", "gold plated", "silver plated", "gold filled", "gold tone", "silver tone", "gold color",
            "silver color", "gold colour", "silver colour", "pvd plated", "pvd gold", "plated", "banado en oro",
            "chapado en oro", "enchapado en oro", "banado en plata", "chapado en plata", "enchapado");

    /**
     * Nombres de material que no dejan claro de qué es la pieza ("titanium steel" se usa para acero, pero no dice que
     * sea inoxidable): no se dan por confirmados. Se buscan antes que los materiales, para que "titanium steel" no se
     * lea como titanio.
     */
    private static final List<String> MATERIALES_DUDOSOS = List.of("titanium steel", "titanio acero", "acero titanio",
            "acero de titanio");
    /** Nombres genéricos: en un título no confirman el material; en la búsqueda se entienden como acero. */
    private static final List<String> MATERIALES_GENERICOS = List.of("steel", "metal", "acero");

    private static final Set<String> PALABRAS_VACIAS = Set.of("de", "la", "el", "los", "las", "para", "con", "por",
            "del", "en", "y", "un", "una", "the", "for", "and", "with", "of", "in", "a", "new", "hot", "sale", "wholesale",
            "fashion", "style", "mayor", "por mayor", "al", "unidades", "pcs", "pieces");

    private static final Map<String, String> TRADUCCIONES = Map.ofEntries(Map.entry("mujer", "women"),
            Map.entry("mujeres", "women"), Map.entry("dama", "women"), Map.entry("damas", "women"),
            Map.entry("hombre", "men"), Map.entry("hombres", "men"), Map.entry("nina", "girl"),
            Map.entry("ninas", "girl"), Map.entry("nino", "boy"), Map.entry("ninos", "boy"),
            Map.entry("hipoalergenico", "hypoallergenic"), Map.entry("hipoalergenicos", "hypoallergenic"),
            Map.entry("perla", "pearl"), Map.entry("perlas", "pearl"), Map.entry("corazon", "heart"),
            Map.entry("estrella", "star"), Map.entry("flor", "flower"), Map.entry("flores", "flower"));

    private RelevanciaMayorista() {
    }

    private static Concepto tipo(String id, String nombre, String... frases) {
        return new Concepto(id, Grupo.TIPO, nombre, List.of(frases));
    }

    private static Concepto material(String id, String nombre, String... frases) {
        return new Concepto(id, Grupo.MATERIAL, nombre, List.of(frases));
    }

    /** Lo que se entendió de la búsqueda. */
    private record Pedido(Set<Concepto> tipos, Set<Concepto> materiales, List<String> otras) {
        boolean entendido() {
            return !tipos.isEmpty() || !materiales.isEmpty();
        }
    }

    /**
     * @param termino lo que buscó la persona
     * @param titulo título de la publicación
     * @return si coincide, es dudosa o es de otro tipo o material
     */
    public static Evaluacion evaluar(String termino, String titulo) {
        Pedido pedido = entender(termino);
        Encontrado enTitulo = buscarConceptos(normalizar(titulo));
        if (!pedido.entendido()) {
            return porPalabras(pedido.otras(), enTitulo.resto());
        }
        List<String> dudas = new ArrayList<>();
        if (!pedido.tipos().isEmpty()) {
            Set<Concepto> tipos = enTitulo.de(Grupo.TIPO);
            boolean tiene = tipos.stream().anyMatch(pedido.tipos()::contains);
            List<String> otros = tipos.stream().filter(t -> !pedido.tipos().contains(t)).map(Concepto::nombre).toList();
            if (!tiene && !otros.isEmpty()) {
                return new Evaluacion(Nivel.INCOMPATIBLE, "Es otro producto (" + String.join(", ", otros) + "), no "
                        + nombres(pedido.tipos()) + ".");
            }
            if (!tiene) {
                dudas.add("el título no dice que sean " + nombres(pedido.tipos()));
            } else if (!otros.isEmpty()) {
                dudas.add("parece un conjunto que también trae " + String.join(", ", otros));
            }
        }
        if (!pedido.materiales().isEmpty()) {
            Set<Concepto> materiales = enTitulo.de(Grupo.MATERIAL);
            boolean tiene = materiales.stream().anyMatch(pedido.materiales()::contains);
            List<String> otros = materiales.stream().filter(m -> !pedido.materiales().contains(m))
                    .map(Concepto::nombre).toList();
            if (!tiene && !otros.isEmpty()) {
                return new Evaluacion(Nivel.INCOMPATIBLE, "Es de otro material (" + String.join(", ", otros)
                        + "), no de " + nombres(pedido.materiales()) + ".");
            }
            if (!tiene) {
                dudas.add(enTitulo.materialDudoso() ? "el material que dice no confirma que sea de "
                        + nombres(pedido.materiales()) : "el título no dice de qué material es");
            }
        }
        if (!dudas.isEmpty()) {
            String texto = String.join("; ", dudas);
            return new Evaluacion(Nivel.DUDOSA, Character.toUpperCase(texto.charAt(0)) + texto.substring(1)
                    + ". Revise la publicación antes de comprar.");
        }
        return new Evaluacion(Nivel.COINCIDE, null);
    }

    private static Evaluacion porPalabras(List<String> palabras, String titulo) {
        if (palabras.isEmpty()) {
            return new Evaluacion(Nivel.COINCIDE, null);
        }
        Set<String> enTitulo = new LinkedHashSet<>();
        for (String palabra : titulo.split(" ")) {
            if (!palabra.isBlank()) {
                enTitulo.add(raiz(palabra));
            }
        }
        long coinciden = palabras.stream().filter(p -> enTitulo.contains(raiz(p))
                || TRADUCCIONES.containsKey(p) && enTitulo.contains(raiz(TRADUCCIONES.get(p)))).count();
        if (coinciden * 2 >= palabras.size() && coinciden > 0) {
            return new Evaluacion(Nivel.COINCIDE, null);
        }
        return new Evaluacion(Nivel.DUDOSA, "El título casi no menciona lo que buscó. Revise la publicación antes de "
                + "comprar.");
    }

    private static Pedido entender(String termino) {
        Encontrado encontrado = buscarConceptos(normalizar(termino));
        Set<Concepto> materiales = encontrado.de(Grupo.MATERIAL);
        if (materiales.isEmpty() && encontrado.materialDudoso()) {
            // "aretes de acero", "titanium steel earrings": se entiende que pide acero.
            CONCEPTOS.stream().filter(c -> c.id().equals("ACERO")).forEach(materiales::add);
        }
        List<String> otras = new ArrayList<>();
        for (String palabra : encontrado.resto().split(" ")) {
            if (palabra.length() >= 2 && !PALABRAS_VACIAS.contains(palabra)) {
                otras.add(palabra);
            }
        }
        return new Pedido(encontrado.de(Grupo.TIPO), materiales, otras);
    }

    private record Encontrado(Map<Concepto, Boolean> conceptos, String resto, boolean materialDudoso) {
        Set<Concepto> de(Grupo grupo) {
            Set<Concepto> lista = new LinkedHashSet<>();
            conceptos.keySet().stream().filter(c -> c.grupo() == grupo).forEach(lista::add);
            return lista;
        }
    }

    /** Busca las frases de la más larga a la más corta y va borrando lo encontrado. */
    private static Encontrado buscarConceptos(String normal) {
        String texto = " " + normal + " ";
        for (String recubrimiento : RECUBRIMIENTOS) {
            texto = texto.replace(" " + recubrimiento + " ", " ");
        }
        boolean dudoso = false;
        for (String frase : MATERIALES_DUDOSOS) {
            if (texto.contains(" " + frase + " ")) {
                dudoso = true;
                texto = texto.replace(" " + frase + " ", " ");
            }
        }
        Map<Concepto, Boolean> encontrados = new LinkedHashMap<>();
        List<Object[]> frases = new ArrayList<>();
        for (Concepto concepto : CONCEPTOS) {
            for (String frase : concepto.frases()) {
                frases.add(new Object[] {normalizar(frase), concepto});
            }
        }
        frases.sort((a, b) -> Integer.compare(((String) b[0]).length(), ((String) a[0]).length()));
        for (Object[] par : frases) {
            String frase = " " + par[0] + " ";
            if (texto.contains(frase)) {
                encontrados.put((Concepto) par[1], true);
                texto = texto.replace(frase, " ");
            }
        }
        if (encontrados.keySet().stream().noneMatch(c -> c.grupo() == Grupo.MATERIAL)) {
            for (String frase : MATERIALES_GENERICOS) {
                if (texto.contains(" " + frase + " ")) {
                    dudoso = true;
                    texto = texto.replace(" " + frase + " ", " ");
                }
            }
        }
        return new Encontrado(encontrados, texto.replaceAll("\\s+", " ").strip(), dudoso);
    }

    private static String nombres(Set<Concepto> conceptos) {
        return String.join(" o ", conceptos.stream().map(Concepto::nombre).toList());
    }

    private static String raiz(String palabra) {
        if (palabra.length() > 4 && palabra.endsWith("es")) {
            return palabra.substring(0, palabra.length() - 2);
        }
        return palabra.length() > 3 && palabra.endsWith("s") ? palabra.substring(0, palabra.length() - 1) : palabra;
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").strip();
    }
}
