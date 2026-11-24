package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.TextoProducto;

import java.util.List;
import java.util.Set;

/**
 * Deja los resultados que más se parecen a lo que la persona buscó, sin completar con otros.
 *
 * <ol>
 *   <li>Quita los juegos y accesorios que no pidió ({@link FiltroComplementos}), siempre, queden los que queden.</li>
 *   <li>Cuenta cuántas palabras de la búsqueda trae cada título ("licuadora oster" en "Licuadora Oster 600 W"
 *       son 2 de 2; en "Licuadora Imusa" es 1 de 2), con la misma lectura de {@link TextoProducto} que usa la
 *       revisión de requisitos: los números cuentan exactos, los decimales no se parten ("3.5 pulgadas" no trae un
 *       5), y se aceptan plurales y abreviaturas ("PS5", "TV").</li>
 *   <li>Se dejan solo los que tienen el puntaje más alto. Si son uno o dos, quedan uno o dos: el tamaño de la lista
 *       no cambia la regla. Si ninguno trae ninguna palabra, no queda ninguno.</li>
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
        List<OfertaNormalizada> productos = FiltroComplementos.filtrar(termino, ofertas);
        List<String> buscadas = palabras(termino);
        if (buscadas.isEmpty()) {
            return productos;
        }
        int mejor = productos.stream().mapToInt(o -> coincidencias(buscadas, o.getTitulo())).max().orElse(0);
        if (mejor == 0) {
            return List.of();
        }
        // Se respeta el orden de la tienda entre los elegidos.
        return productos.stream().filter(o -> coincidencias(buscadas, o.getTitulo()) == mejor).toList();
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
