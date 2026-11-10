package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que opinan los compradores de un producto, tal como lo entrega la tienda.
 *
 * @param calificacion promedio de 0 a 5, o {@code null} si no se conoce
 * @param totalResenas cuántas opiniones hay en total, o {@code null}
 * @param distribucion cuántas opiniones hay de cada número de estrellas, de 5 a 1
 * @param resenas algunas opiniones escritas, las primeras que muestra la tienda
 */
public record ResenasProducto(BigDecimal calificacion, Integer totalResenas, List<Estrellas> distribucion,
                              List<Resena> resenas) {

    public ResenasProducto {
        distribucion = distribucion == null ? List.of() : List.copyOf(distribucion);
        resenas = resenas == null ? List.of() : List.copyOf(resenas);
    }

    /** @return {@code true} si la tienda no tiene ninguna opinión de este producto */
    public boolean estaVacia() {
        return resenas.isEmpty() && distribucion.isEmpty() && (totalResenas == null || totalResenas == 0);
    }

    /**
     * Cantidad de opiniones con un número de estrellas.
     *
     * @param estrellas de 1 a 5
     * @param cantidad opiniones con esas estrellas
     */
    public record Estrellas(int estrellas, int cantidad) {
    }

    /**
     * Una opinión escrita por un comprador.
     *
     * @param titulo título de la opinión, puede faltar
     * @param texto lo que escribió
     * @param autor nombre que muestra la tienda, puede faltar
     * @param fuente página donde se publicó (por ejemplo, la tienda)
     * @param calificacion estrellas que dio, o {@code null}
     * @param fecha fecha tal como la muestra la tienda ("hace 2 meses")
     */
    public record Resena(String titulo, String texto, String autor, String fuente, BigDecimal calificacion,
                         String fecha) {
    }
}
