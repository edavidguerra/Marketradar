package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.Idioma;
import co.edu.unicesar.marketradar.domain.ResenasProducto;
import co.edu.unicesar.marketradar.service.DetectorIdioma;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Respuesta de {@code GET /api/v1/ofertas/{id}/resenas}.
 *
 * @param disponibles {@code false} si la tienda no tiene opiniones de este producto
 * @param calificacion promedio de 0 a 5, o {@code null}
 * @param totalResenas cuántas opiniones hay, o {@code null}
 * @param distribucion opiniones por número de estrellas, de 5 a 1
 * @param resenas algunas opiniones escritas
 * @param idiomaUsuario código del idioma de la cuenta, por ejemplo {@code es}
 * @param nombreIdiomaUsuario nombre del idioma de la cuenta, por ejemplo {@code español}, para el botón "Traducir al
 *                            español"
 */
public record ResenasDto(boolean disponibles, BigDecimal calificacion, Integer totalResenas,
                         List<EstrellasDto> distribucion, List<ResenaDto> resenas, String idiomaUsuario,
                         String nombreIdiomaUsuario) {

    /**
     * @param idioma idioma de la cuenta
     * @return respuesta para un producto sin opiniones
     */
    public static ResenasDto sinResenas(Idioma idioma) {
        return new ResenasDto(false, null, null, List.of(), List.of(), idioma.codigo(), idioma.nombre());
    }

    /**
     * Arma la respuesta y marca qué reseñas están en otro idioma.
     *
     * @param producto reseñas de la tienda
     * @param idioma idioma de la cuenta
     * @param traduccionDisponible si Gemini está configurado; sin él no se ofrece traducir
     * @return respuesta para el frontend
     */
    public static ResenasDto desde(ResenasProducto producto, Idioma idioma, boolean traduccionDisponible) {
        List<ResenasProducto.Resena> resenas = producto.resenas();
        return new ResenasDto(true, producto.calificacion(), producto.totalResenas(),
                producto.distribucion().stream().map(e -> new EstrellasDto(e.estrellas(), e.cantidad())).toList(),
                IntStream.range(0, resenas.size())
                        .mapToObj(i -> ResenaDto.desde(i, resenas.get(i), idioma, traduccionDisponible)).toList(),
                idioma.codigo(), idioma.nombre());
    }

    /**
     * @param estrellas de 1 a 5
     * @param cantidad cuántas opiniones dieron esas estrellas
     */
    public record EstrellasDto(int estrellas, int cantidad) {
    }

    /**
     * @param indice posición en la lista, para pedir la traducción
     *               ({@code POST /api/v1/ofertas/{id}/resenas/{indice}/traduccion})
     * @param titulo título de la opinión, o {@code null}
     * @param texto lo que escribió el comprador
     * @param autor nombre del comprador, o {@code null}
     * @param fuente sitio donde se publicó, o {@code null}
     * @param calificacion estrellas que dio, o {@code null}
     * @param fecha cuándo la escribió, como lo dice la tienda
     * @param idioma código del idioma en que parece estar escrita, o {@code null} si no se pudo saber
     * @param traducible si se ofrece el botón "Traducir": está en otro idioma y la traducción está disponible
     */
    public record ResenaDto(int indice, String titulo, String texto, String autor, String fuente,
                            BigDecimal calificacion, String fecha, String idioma, boolean traducible) {

        static ResenaDto desde(int indice, ResenasProducto.Resena resena, Idioma idiomaUsuario,
                               boolean traduccionDisponible) {
            String muestra = (resena.titulo() == null ? "" : resena.titulo() + " ")
                    + (resena.texto() == null ? "" : resena.texto());
            Idioma detectado = DetectorIdioma.detectar(muestra).orElse(null);
            boolean traducible = traduccionDisponible && detectado != null && detectado != idiomaUsuario
                    && resena.texto() != null && !resena.texto().isBlank();
            return new ResenaDto(indice, resena.titulo(), resena.texto(), resena.autor(), resena.fuente(),
                    resena.calificacion(), resena.fecha(), detectado == null ? null : detectado.codigo(), traducible);
        }
    }
}
