package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.Idioma;
import co.edu.unicesar.marketradar.service.ServicioTraduccion;

/**
 * Respuesta de {@code POST /api/v1/ofertas/{id}/resenas/{indice}/traduccion}.
 *
 * @param titulo título traducido, o {@code null}
 * @param texto texto traducido
 * @param idiomaOriginal código del idioma en que venía, o {@code null} si no se sabe
 * @param nombreIdiomaOriginal nombre en español del idioma en que venía ("inglés"), o {@code null}
 * @param idiomaDestino código del idioma al que se tradujo
 */
public record TraduccionDto(String titulo, String texto, String idiomaOriginal, String nombreIdiomaOriginal,
                            String idiomaDestino) {

    /**
     * @param traduccion resultado del servicio
     * @return respuesta para el frontend
     */
    public static TraduccionDto desde(ServicioTraduccion.Traduccion traduccion) {
        String nombre = Idioma.desdeCodigo(traduccion.idiomaOriginal()).map(Idioma::nombre).orElse(null);
        return new TraduccionDto(traduccion.titulo(), traduccion.texto(), traduccion.idiomaOriginal(), nombre,
                traduccion.idiomaDestino());
    }
}
