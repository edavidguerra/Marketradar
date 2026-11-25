package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.TextoProducto;

import java.util.List;
import java.util.Set;

/**
 * Prepara las candidatas que pasan a la revisión de identidad, sin completar con otras.
 *
 * <ol>
 *   <li>Quita los juegos y accesorios que no pidió ({@link FiltroComplementos}), siempre, queden los que queden.</li>
 *   <li>Cuenta cuántas palabras de la búsqueda trae cada título ("licuadora oster" en "Licuadora Oster 600 W"
 *       son 2 de 2; en "Licuadora Imusa" es 1 de 2), con la misma lectura de {@link TextoProducto} que usa la
 *       revisión de requisitos: los números cuentan exactos, los decimales no se parten ("3.5 pulgadas" no trae un
 *       5), y se aceptan plurales y abreviaturas ("PS5", "TV").</li>
 *   <li>El puntaje solo ordena. Se quitan las que no traen ninguna palabra de la búsqueda y tampoco cumplen lo
 *       pedido; las demás pasan a la revisión, porque quitar por puntaje antes de revisar la identidad dejaba fuera
 *       ofertas confirmadas. Si ninguna sirve, no queda ninguna: no se completa con otras.</li>
 * </ol>
 *
 * <p>El precio no se usa para decidir qué producto es: una oferta muy barata o muy cara se muestra con un aviso de
 * precio atípico (ver {@link ServicioBusqueda#marcarPreciosAtipicos}), no se esconde.</p>
 *
 * <p>Esto solo escoge candidatos. Qué es coincidencia exacta, qué es una alternativa y qué es otro producto lo decide
 * después la revisión de identidad y requisitos ({@link co.edu.unicesar.marketradar.domain.RequisitosBusqueda}).</p>
 */
final class FiltroRelevancia {

    private FiltroRelevancia() {
    }

    static List<OfertaNormalizada> filtrar(String termino, List<OfertaNormalizada> ofertas) {
        return filtrar(termino, ofertas, co.edu.unicesar.marketradar.domain.RequisitosBusqueda.de(termino));
    }

    /**
     * Candidatas para la revisión final. Primero se quitan los complementos; después la similitud de palabras solo
     * ordena: una oferta que las reglas confirman nunca se quita porque otra repita más palabras de la búsqueda
     * ("Samsung Galaxy A16 128GB" no cae frente a "Celular Samsung A16 4GB RAM"). Se quitan solo las que no comparten
     * ninguna palabra con la búsqueda y tampoco cumplen lo pedido.
     *
     * @param termino lo que se buscó en la tienda
     * @param ofertas resultados de las tiendas
     * @param requisitos lo que pidió la persona
     * @return candidatas, de la más parecida a la menos; entre iguales, en el orden de la tienda
     */
    static List<OfertaNormalizada> filtrar(String termino, List<OfertaNormalizada> ofertas,
                                           co.edu.unicesar.marketradar.domain.RequisitosBusqueda requisitos) {
        List<OfertaNormalizada> productos = FiltroComplementos.filtrar(termino, ofertas);
        List<String> buscadas = palabras(termino);
        if (buscadas.isEmpty()) {
            return productos;
        }
        java.util.Map<OfertaNormalizada, Integer> puntajes = new java.util.IdentityHashMap<>();
        productos.forEach(o -> puntajes.put(o, coincidencias(buscadas, o.getTitulo())));
        return productos.stream()
                .filter(o -> puntajes.get(o) > 0 || requisitos.evaluar(o.getTitulo(), o.condicionProducto()).nivel()
                        == co.edu.unicesar.marketradar.domain.RequisitosBusqueda.Nivel.CUMPLE)
                .sorted(java.util.Comparator.comparingInt((OfertaNormalizada o) -> puntajes.get(o)).reversed())
                .toList();
    }

    /**
     * Revisa lo que eligió la IA: quita accesorios, juegos, repuestos y cajas que se le hayan colado ("Funda iPhone 17
     * Pro"). Lo que queda no se da por exacto: pasa por la misma revisión de identidad que todo lo demás.
     *
     * @param termino lo que buscó la persona
     * @param elegidas lo que eligió la IA
     * @param candidatas todo lo que se le mostró a la IA
     * @return lo elegido que sí puede ser el producto; vacío si nada sirve
     */
    static List<OfertaNormalizada> revisarEleccion(String termino, List<OfertaNormalizada> elegidas,
                                                   List<OfertaNormalizada> candidatas) {
        String buscado = ServicioSugerencias.normalizar(termino);
        Set<String> palabrasBuscadas = Set.of(buscado.split(" "));
        // En el orden de la tienda, no en el que las nombró la IA.
        return candidatas.stream()
                .filter(elegidas::contains)
                .filter(oferta -> !FiltroComplementos.esComplemento(oferta, buscado, palabrasBuscadas))
                .filter(oferta -> !FiltroComplementos.esSinProducto(oferta, buscado, palabrasBuscadas))
                .toList();
    }

    static int coincidencias(List<String> buscadas, String titulo) {
        return TextoProducto.coincidencias(buscadas, titulo);
    }

    /**
     * Palabras de la búsqueda que dicen qué producto es. La capacidad y la condición ("nuevo", "usado") no cuentan:
     * casi ningún título dice "nuevo", y una oferta de otra capacidad o condición no se esconde aquí, sino que se
     * muestra aparte diciendo qué le falta.
     */
    static List<String> palabras(String termino) {
        List<String> palabras = new java.util.ArrayList<>(TextoProducto.palabrasClave(termino));
        List<String> leidas = TextoProducto.palabras(termino);
        // "128 gb", "1 tb": la capacidad la revisa RequisitosBusqueda, que dice cuál es la que no cumple.
        for (int i = 0; i + 1 < leidas.size(); i++) {
            if (leidas.get(i).matches("\\d{1,4}") && leidas.get(i + 1).matches("gb|tb|gigas?|teras?")) {
                palabras.remove(leidas.get(i));
                palabras.remove(leidas.get(i + 1));
            }
        }
        palabras.removeIf(p -> p.matches("\\d{1,4}(gb|tb)"));
        return List.copyOf(palabras);
    }
}
