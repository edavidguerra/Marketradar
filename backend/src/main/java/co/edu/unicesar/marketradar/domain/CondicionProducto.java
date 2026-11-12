package co.edu.unicesar.marketradar.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Estado del producto que se vende. Nuevo, usado, reacondicionado y de exhibición son cosas distintas: un equipo de
 * exhibición o reacondicionado nunca cuenta como nuevo, y si la tienda no dice nada la condición queda sin saberse
 * (no se supone que es nuevo).
 */
public enum CondicionProducto {

    NUEVO("nuevo", "Nuevo"),
    USADO("usado", "Usado"),
    REACONDICIONADO("reacondicionado", "Reacondicionado"),
    EXHIBICION("exhibicion", "De exhibición");

    /*
     * Se revisan en este orden: "reacondicionado como nuevo" y "exhibición, como nuevo" no son nuevos, y
     * "como nuevo" a secas es un usado en buen estado.
     */
    private static final Pattern EXHIBICION_TEXTO = Pattern.compile(
            "\\b(exhibicion|exhibido|de vitrina|open box|display unit|demo unit)\\b");
    private static final Pattern REACONDICIONADO_TEXTO = Pattern.compile(
            "\\b(reacondicionad[oa]s?|refurbished|renovad[oa]s?|remanufacturad[oa]s?|reconstruid[oa]s?)\\b");
    private static final Pattern USADO_TEXTO = Pattern.compile(
            "\\b(usad[oa]s?|used|segunda mano|seminuev[oa]s?|semi nuev[oa]s?|pre ?owned|como nuev[oa]s?|like new)\\b");
    private static final Pattern NUEVO_TEXTO = Pattern.compile(
            "\\b(nuev[oa]s?|new|sellad[oa]s?|sealed|brand new)\\b");

    private final String codigo;
    private final String texto;

    CondicionProducto(String codigo, String texto) {
        this.codigo = codigo;
        this.texto = texto;
    }

    /** @return código que se guarda en la base de datos y se envía al frontend, por ejemplo {@code reacondicionado} */
    public String codigo() {
        return codigo;
    }

    /** @return nombre para mostrar, por ejemplo "De exhibición" */
    public String texto() {
        return texto;
    }

    /**
     * @param codigo código guardado
     * @return la condición, o vacío si el código no se reconoce o falta
     */
    public static Optional<CondicionProducto> deCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        String limpio = normalizar(codigo);
        for (CondicionProducto condicion : values()) {
            if (condicion.codigo.equals(limpio)) {
                return Optional.of(condicion);
            }
        }
        return Optional.empty();
    }

    /**
     * Busca la condición en los textos que trae la tienda (el título, etiquetas como "Usado" o "Refurbished").
     * El primer texto que diga algo decide.
     *
     * @param textos textos de la tienda, en orden de confianza; los {@code null} se saltan
     * @return la condición, o vacío si ningún texto la dice
     */
    public static Optional<CondicionProducto> detectar(String... textos) {
        for (String texto : textos) {
            Optional<CondicionProducto> encontrada = detectarEn(texto);
            if (encontrada.isPresent()) {
                return encontrada;
            }
        }
        return Optional.empty();
    }

    private static Optional<CondicionProducto> detectarEn(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String limpio = normalizar(texto);
        if (EXHIBICION_TEXTO.matcher(limpio).find()) {
            return Optional.of(EXHIBICION);
        }
        if (REACONDICIONADO_TEXTO.matcher(limpio).find()) {
            return Optional.of(REACONDICIONADO);
        }
        if (USADO_TEXTO.matcher(limpio).find()) {
            return Optional.of(USADO);
        }
        if (NUEVO_TEXTO.matcher(limpio).find()) {
            return Optional.of(NUEVO);
        }
        return Optional.empty();
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9ñ]+", " ").strip();
    }
}
