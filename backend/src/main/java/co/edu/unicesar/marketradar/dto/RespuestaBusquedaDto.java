package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;

import java.util.List;

/**
 * Respuesta de {@code GET /api/v1/busqueda}.
 *
 * @param termino texto buscado
 * @param pagina página devuelta
 * @param tamano resultados por página
 * @param totalResultados total de ofertas encontradas
 * @param ofertas ofertas de la página
 * @param advertencias tiendas que no respondieron
 */
public record RespuestaBusquedaDto(String termino, int pagina, int tamano, int totalResultados,
                                   List<OfertaDto> ofertas, List<String> advertencias) {

    /**
     * Construye la respuesta a partir del resultado del servicio.
     *
     * @param consulta consulta realizada
     * @param resultado resultado del servicio de búsqueda
     * @return DTO de respuesta
     */
    public static RespuestaBusquedaDto desde(ConsultaBusqueda consulta, ResultadoBusqueda resultado) {
        List<OfertaDto> ofertas = resultado.getOfertas().stream()
                .map(oferta -> OfertaDto.desde(oferta, resultado.idPersistido(oferta), resultado.esMejorPrecio(oferta)))
                .toList();
        return new RespuestaBusquedaDto(consulta.getTermino(), consulta.getPagina(), consulta.getTamano(),
                resultado.getTotalResultados(), ofertas, resultado.getAdvertencias());
    }
}
