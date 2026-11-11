package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.Idioma;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adivina en qué idioma está escrita una reseña contando sus palabras más comunes ("the", "and", "is" en inglés;
 * "el", "que", "muy" en español). Es rápido y gratis, por eso se usa para decidir si se muestra el botón
 * "Traducir"; la traducción en sí la hace Gemini.
 */
public final class DetectorIdioma {

    /** Con menos palabras conocidas que esto no se puede saber (por ejemplo "Excelente!!" o "OK"). */
    static final int MINIMO_PALABRAS = 2;

    private static final Map<Idioma, Set<String>> PALABRAS = new EnumMap<>(Idioma.class);

    static {
        PALABRAS.put(Idioma.ES, Set.of("el", "la", "los", "las", "de", "del", "que", "y", "es", "muy", "pero", "con",
                "para", "por", "una", "un", "lo", "se", "no", "mi", "su", "esta", "este", "como", "bien", "todo",
                "producto", "llego", "funciona", "excelente", "bueno", "buena", "calidad", "precio", "recomiendo",
                "compre", "hace", "tiene", "mas", "porque", "cuando", "tambien", "estoy", "fue", "son"));
        PALABRAS.put(Idioma.EN, Set.of("the", "and", "is", "it", "this", "was", "for", "with", "not", "but", "very",
                "my", "of", "to", "in", "you", "have", "are", "great", "good", "they", "that", "on", "so", "would",
                "product", "works", "love", "bought", "item", "seller", "after", "when", "its", "i", "me", "be"));
        PALABRAS.put(Idioma.PT, Set.of("o", "os", "as", "do", "da", "dos", "das", "que", "e", "muito", "mas", "com",
                "para", "por", "uma", "um", "nao", "meu", "minha", "esta", "isso", "produto", "chegou", "otimo",
                "bom", "boa", "qualidade", "recomendo", "comprei", "voce", "tem", "foi", "sao", "bem", "ele"));
        PALABRAS.put(Idioma.FR, Set.of("le", "la", "les", "de", "des", "du", "et", "est", "tres", "mais", "avec",
                "pour", "par", "une", "un", "pas", "je", "il", "ce", "cette", "produit", "bien", "qualite", "prix",
                "recommande", "achete", "sont", "tout", "sur", "dans", "nous", "vous", "j'ai", "c'est", "bon"));
        PALABRAS.put(Idioma.IT, Set.of("il", "lo", "gli", "di", "del", "della", "che", "e", "molto", "ma", "con",
                "per", "una", "uno", "non", "mio", "questo", "questa", "prodotto", "arrivato", "ottimo", "buono",
                "qualita", "prezzo", "consiglio", "comprato", "sono", "tutto", "anche", "come", "ho", "nel"));
        PALABRAS.put(Idioma.DE, Set.of("der", "die", "das", "und", "ist", "nicht", "sehr", "aber", "mit", "fur",
                "ein", "eine", "ich", "es", "zu", "auf", "produkt", "gut", "qualitat", "preis", "empfehlen",
                "gekauft", "sind", "auch", "wie", "hat", "war", "mein", "den", "dem", "funktioniert"));
    }

    private DetectorIdioma() {
    }

    /**
     * Idioma más probable del texto.
     *
     * @param texto título y texto de la reseña
     * @return el idioma, o vacío si el texto es muy corto o no se parece a ninguno
     */
    public static Optional<Idioma> detectar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String limpio = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String[] palabras = limpio.split("[^\\p{L}']+");
        Map<Idioma, Integer> puntos = new EnumMap<>(Idioma.class);
        for (String palabra : palabras) {
            if (palabra.isEmpty()) {
                continue;
            }
            PALABRAS.forEach((idioma, comunes) -> {
                if (comunes.contains(palabra)) {
                    puntos.merge(idioma, 1, Integer::sum);
                }
            });
        }
        Idioma mejor = null;
        int maximo = 0;
        boolean empate = false;
        for (Map.Entry<Idioma, Integer> entrada : puntos.entrySet()) {
            if (entrada.getValue() > maximo) {
                mejor = entrada.getKey();
                maximo = entrada.getValue();
                empate = false;
            } else if (entrada.getValue() == maximo) {
                empate = true;
            }
        }
        if (mejor == null || maximo < MINIMO_PALABRAS || empate) {
            return Optional.empty();
        }
        return Optional.of(mejor);
    }
}
