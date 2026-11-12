package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.Idioma;
import co.edu.unicesar.marketradar.domain.ResenasProducto;
import co.edu.unicesar.marketradar.service.ServicioResenas;
import co.edu.unicesar.marketradar.service.DetectorIdioma;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * @param procedencia {@code verificada}, {@code sin_verificar} o {@code rechazada}: si son opiniones del mismo producto
 * @param notaProcedencia de dónde son las opiniones, para mostrarlo arriba de ellas
 * @param notaConteo por qué el total, las estrellas y las opiniones escritas no suman lo mismo, o {@code null}
 */
public record ResenasDto(boolean disponibles, BigDecimal calificacion, Integer totalResenas,
                         List<EstrellasDto> distribucion, List<ResenaDto> resenas, String idiomaUsuario,
                         String nombreIdiomaUsuario, String procedencia, String notaProcedencia, String notaConteo) {

    /**
     * @param idioma idioma de la cuenta
     * @return respuesta para un producto sin opiniones
     */
    public static ResenasDto sinResenas(Idioma idioma) {
        return new ResenasDto(false, null, null, List.of(), List.of(), idioma.codigo(), idioma.nombre(), null, null,
                null);
    }

    /**
     * @param idioma idioma de la cuenta
     * @param nota por qué no se muestran
     * @return respuesta para opiniones que Google asoció a otro producto
     */
    public static ResenasDto rechazadas(Idioma idioma, String nota) {
        return new ResenasDto(false, null, null, List.of(), List.of(), idioma.codigo(), idioma.nombre(), "rechazada",
                nota, null);
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
        return desde(producto, idioma, traduccionDisponible, null);
    }

    /**
     * Arma la respuesta con la procedencia de las opiniones.
     *
     * @param producto reseñas de la tienda
     * @param idioma idioma de la cuenta
     * @param traduccionDisponible si Gemini está configurado
     * @param procedencia de dónde son, o {@code null}
     * @return respuesta para el frontend
     */
    public static ResenasDto desde(ResenasProducto producto, Idioma idioma, boolean traduccionDisponible,
                                   ServicioResenas.Procedencia procedencia) {
        if (procedencia != null && procedencia.estado() == ServicioResenas.Procedencia.Estado.RECHAZADA) {
            return rechazadas(idioma, procedencia.nota());
        }
        List<ResenasProducto.Resena> resenas = producto.resenas();
        return new ResenasDto(true, producto.calificacion(), producto.totalResenas(),
                producto.distribucion().stream().map(e -> new EstrellasDto(e.estrellas(), e.cantidad())).toList(),
                IntStream.range(0, resenas.size())
                        .mapToObj(i -> ResenaDto.desde(i, resenas.get(i), idioma, traduccionDisponible)).toList(),
                idioma.codigo(), idioma.nombre(),
                procedencia == null ? null : procedencia.estado().name().toLowerCase(Locale.ROOT),
                procedencia == null ? null : procedencia.nota(), notaConteo(producto));
    }

    /** Google da el total, las estrellas y unas pocas opiniones escritas por separado: se explica si no cuadran. */
    private static String miles(int numero) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")).format(numero);
    }

    static String notaConteo(ResenasProducto producto) {
        List<String> partes = new ArrayList<>();
        Integer total = producto.totalResenas();
        int suma = producto.totalDistribucion();
        if (total != null && suma > 0 && suma != total) {
            partes.add("El total de " + miles(total) + " opiniones y la suma de las estrellas (" + miles(suma)
                    + ") vienen de conteos distintos de Google");
        }
        if (total != null && !producto.resenas().isEmpty() && producto.resenas().size() < total) {
            partes.add("se muestran " + producto.resenas().size() + " de las " + miles(total) + " opiniones");
        }
        if (partes.isEmpty()) {
            return null;
        }
        String texto = String.join("; ", partes) + ".";
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
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
