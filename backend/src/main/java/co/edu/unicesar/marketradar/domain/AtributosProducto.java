package co.edu.unicesar.marketradar.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Características que la persona puede pedir de forma expresa y que cambian qué producto es: cómo se conecta
 * (inalámbrico o con cable), cómo funciona (freidora de aire o de aceite) y el color. Se leen igual en la búsqueda,
 * en el título de cada oferta y en la comparación, con sinónimos ("bluetooth" e "inalámbrico" dicen lo mismo).
 *
 * <p>Solo se reconocen frases claras. Si el título no dice nada de un grupo, no se supone nada: queda por confirmar.
 * Si dice dos valores del mismo grupo ("inalámbrico y con cable", "rojo y negro"), tiene los dos.</p>
 */
public final class AtributosProducto {

    /**
     * Un grupo de valores que se excluyen entre sí.
     *
     * @param clave nombre interno ("conectividad", "funcionamiento", "color")
     * @param pregunta cómo se dice que falta ("si es", "el color")
     * @param valores valor → patrón sobre el texto normalizado
     */
    private record Grupo(String clave, String pregunta, Map<String, Pattern> valores) {
    }

    /**
     * Valor leído.
     *
     * @param grupo "conectividad", "funcionamiento" o "color"
     * @param valor texto para mostrar ("inalámbrico", "con cable", "de aire", "rojo")
     */
    public record Atributo(String grupo, String valor) {
    }

    private static final List<Grupo> GRUPOS = List.of(
            new Grupo("conectividad", "si es", valores(
                    "inalámbrico", "\\b(inalambric[oa]s?|bluetooth|wireless|tws|true wireless|sin cables?)\\b",
                    // "con cable de carga" o "cable USB incluido" no dicen cómo se conecta el producto.
                    "con cable", "\\b(con cable|cables?)\\b(?! (de carga|usb|tipo c|para cargar|de alimentacion))"
                            + "|\\b(alambric[oa]s?|cablead[oa]s?|wired|jack|plug)\\b|\\b3\\.5 ?mm\\b")),
            new Grupo("funcionamiento", "si es", valores(
                    "de aire", "\\b(de aire|air fryer|airfryer|sin aceite|aire caliente)\\b",
                    "de aceite", "(?<!sin )\\b(de aceite|con aceite|de inmersion)\\b")),
            new Grupo("color", "el color", valores(
                    "negro", "\\b(negr[oa]s?|black)\\b", "blanco", "\\b(blanc[oa]s?|white)\\b",
                    "rojo", "\\b(roj[oa]s?|red)\\b", "azul", "\\b(azul|azules|blue)\\b",
                    "verde", "\\b(verdes?|green)\\b", "gris", "\\b(gris|grises|gray|grey|grafito)\\b",
                    "plateado", "\\b(platead[oa]s?|plata|silver)\\b", "dorado", "\\b(dorad[oa]s?|gold)\\b",
                    "rosado", "\\b(rosad[oa]s?|rosa|pink)\\b", "morado", "\\b(morad[oa]s?|lila|purple|violeta)\\b",
                    "amarillo", "\\b(amarill[oa]s?|yellow)\\b", "naranja", "\\b(naranjas?|orange)\\b",
                    "beige", "\\bbeige\\b", "café", "\\b(cafe|marron)\\b")));

    /** Palabras que solo dicen una característica: no se exigen además como palabra suelta del título. */
    private static final Pattern PALABRA_DE_ATRIBUTO = Pattern.compile("^(inalambric[oa]s?|bluetooth|wireless|tws|"
            + "cables?|alambric[oa]s?|cablead[oa]s?|wired|aire|aceite|inmersion|negr[oa]s?|blanc[oa]s?|roj[oa]s?|"
            + "azul(es)?|verdes?|gris(es)?|platead[oa]s?|plata|dorad[oa]s?|rosad[oa]s?|rosa|morad[oa]s?|lila|violeta|"
            + "amarill[oa]s?|naranjas?|beige|cafe|marron)$");

    private AtributosProducto() {
    }

    private static Map<String, Pattern> valores(String... pares) {
        Map<String, Pattern> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put(pares[i], Pattern.compile(pares[i + 1]));
        }
        return mapa;
    }

    /**
     * Características que dice un texto (búsqueda o título).
     *
     * @param texto texto libre
     * @return por grupo, los valores encontrados; grupos sin valores no aparecen
     */
    public static Map<String, Set<String>> leer(String texto) {
        String leido = String.join(" ", TextoProducto.palabras(texto == null ? "" : texto));
        Map<String, Set<String>> encontrados = new LinkedHashMap<>();
        for (Grupo grupo : GRUPOS) {
            for (Map.Entry<String, Pattern> valor : grupo.valores().entrySet()) {
                if (valor.getValue().matcher(leido).find()) {
                    encontrados.computeIfAbsent(grupo.clave(), g -> new LinkedHashSet<>()).add(valor.getKey());
                }
            }
        }
        // "Inalámbricos con cable de carga" ya lo cubre el patrón; si un título dice las dos cosas, se respetan las dos.
        return encontrados;
    }

    /**
     * @param texto texto libre
     * @return lista plana de lo que dice
     */
    public static List<Atributo> lista(String texto) {
        return leer(texto).entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(v -> new Atributo(e.getKey(), v))).toList();
    }

    /**
     * @param palabra palabra ya normalizada
     * @return si la palabra solo expresa una característica ("roja", "inalámbricos", "aire")
     */
    public static boolean esPalabraDeAtributo(String palabra) {
        return PALABRA_DE_ATRIBUTO.matcher(palabra).matches();
    }

    /**
     * Cómo se dice que el título no informa un grupo: "El título no dice si es inalámbrico", "…el color (rojo)".
     *
     * @param atributo lo pedido
     * @return frase
     */
    public static String falta(Atributo atributo) {
        Grupo grupo = GRUPOS.stream().filter(g -> g.clave().equals(atributo.grupo())).findFirst().orElseThrow();
        return "color".equals(grupo.clave()) ? "El título no dice el color (" + atributo.valor() + ")"
                : "El título no dice " + grupo.pregunta() + " " + atributo.valor();
    }

    /**
     * Cómo se dice una contradicción: "Es con cable, no inalámbrico", "Es de color blanco, no rojo".
     *
     * @param pedido lo pedido
     * @param tiene lo que dice el título del mismo grupo
     * @return frase
     */
    public static String contradice(Atributo pedido, Set<String> tiene) {
        String valores = String.join(" y ", tiene);
        return "color".equals(pedido.grupo()) ? "Es de color " + valores + ", no " + pedido.valor()
                : "Es " + valores + ", no " + pedido.valor();
    }

    /**
     * Grupos en que dos textos dicen valores distintos y sin ninguno en común ("con cable" contra "inalámbrico"). El
     * color no cuenta: dos colores del mismo modelo siguen siendo el mismo producto para comparar.
     *
     * @param a un título
     * @param b otro título
     * @return descripción de cada diferencia, vacía si no hay
     */
    public static List<String> diferenciasDeProducto(String a, String b) {
        Map<String, Set<String>> deA = leer(a);
        Map<String, Set<String>> deB = leer(b);
        List<String> diferencias = new java.util.ArrayList<>();
        for (String grupo : List.of("conectividad", "funcionamiento")) {
            Set<String> va = deA.getOrDefault(grupo, Set.of());
            Set<String> vb = deB.getOrDefault(grupo, Set.of());
            if (!va.isEmpty() && !vb.isEmpty() && va.stream().noneMatch(vb::contains)) {
                diferencias.add(String.join(" y ", va) + " frente a " + String.join(" y ", vb));
            }
        }
        return diferencias;
    }
}
