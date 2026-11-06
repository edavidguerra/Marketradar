package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.Busqueda;

import java.time.OffsetDateTime;

/**
 * Una búsqueda del historial de la persona.
 *
 * @param id identificador
 * @param termino texto buscado
 * @param totalResultados ofertas encontradas
 * @param creadaEn fecha y hora de la búsqueda
 * @param urlImagen foto de un producto que se encontró, para reconocer la búsqueda; puede ser {@code null}
 */
public record BusquedaHistorialDto(Long id, String termino, int totalResultados, OffsetDateTime creadaEn,
                                   String urlImagen) {

    /**
     * @param busqueda búsqueda guardada
     * @return DTO
     */
    public static BusquedaHistorialDto desde(Busqueda busqueda) {
        return new BusquedaHistorialDto(busqueda.getId(), busqueda.getTermino(), busqueda.getTotalResultados(),
                busqueda.getCreadaEn(), busqueda.getUrlImagen());
    }
}
