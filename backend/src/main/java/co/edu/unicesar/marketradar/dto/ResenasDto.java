package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.ResenasProducto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Respuesta de {@code GET /api/v1/ofertas/{id}/resenas}.
 *
 * @param disponibles {@code false} si la tienda no tiene opiniones de este producto
 * @param calificacion promedio de 0 a 5, o {@code null}
 * @param totalResenas cuántas opiniones hay, o {@code null}
 * @param distribucion opiniones por número de estrellas, de 5 a 1
 * @param resenas algunas opiniones escritas
 */
public record ResenasDto(boolean disponibles, BigDecimal calificacion, Integer totalResenas,
                         List<EstrellasDto> distribucion, List<ResenaDto> resenas) {

    /** @return respuesta para un producto sin opiniones */
    public static ResenasDto sinResenas() {
        return new ResenasDto(false, null, null, List.of(), List.of());
    }

    /**
     * @param producto reseñas de la tienda
     * @return DTO
     */
    public static ResenasDto desde(ResenasProducto producto) {
        return new ResenasDto(true, producto.calificacion(), producto.totalResenas(),
                producto.distribucion().stream().map(e -> new EstrellasDto(e.estrellas(), e.cantidad())).toList(),
                producto.resenas().stream().map(r -> new ResenaDto(r.titulo(), r.texto(), r.autor(), r.fuente(),
                        r.calificacion(), r.fecha())).toList());
    }

    /**
     * @param estrellas de 1 a 5
     * @param cantidad opiniones con esas estrellas
     */
    public record EstrellasDto(int estrellas, int cantidad) {
    }

    /**
     * @param titulo título de la opinión
     * @param texto lo que escribió el comprador
     * @param autor nombre que muestra la tienda
     * @param fuente página donde se publicó
     * @param calificacion estrellas que dio
     * @param fecha fecha como la muestra la tienda
     */
    public record ResenaDto(String titulo, String texto, String autor, String fuente, BigDecimal calificacion,
                            String fecha) {
    }
}
