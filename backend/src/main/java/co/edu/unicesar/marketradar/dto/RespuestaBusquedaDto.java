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
 * @param productoMvp mejor opción de compra entre todas las ofertas; {@code null} si no hay ofertas
 * @param quizasQuisoDecir búsqueda corregida si alguna palabra parece mal escrita; {@code null} si no
 */
public record RespuestaBusquedaDto(String termino, int pagina, int tamano, int totalResultados,
                                   List<OfertaDto> ofertas, List<String> advertencias, ProductoMvpDto productoMvp,
                                   String quizasQuisoDecir) {

    /**
     * Construye la respuesta a partir del resultado del servicio.
     *
     * @param consulta consulta realizada
     * @param resultado resultado del servicio de búsqueda
     * @return DTO de respuesta
     */
    public static RespuestaBusquedaDto desde(ConsultaBusqueda consulta, ResultadoBusqueda resultado) {
        return desde(consulta, resultado, null);
    }

    /**
     * Construye la respuesta con una corrección de la búsqueda.
     *
     * @param consulta consulta realizada
     * @param resultado resultado del servicio de búsqueda
     * @param quizasQuisoDecir búsqueda corregida, o {@code null}
     * @return DTO de respuesta
     */
    public static RespuestaBusquedaDto desde(ConsultaBusqueda consulta, ResultadoBusqueda resultado,
                                             String quizasQuisoDecir) {
        List<OfertaDto> ofertas = resultado.getOfertas().stream()
                .map(oferta -> OfertaDto.desde(oferta, resultado.idPersistido(oferta), resultado.esMejorPrecio(oferta)))
                .toList();
        return new RespuestaBusquedaDto(consulta.getTermino(), consulta.getPagina(), consulta.getTamano(),
                resultado.getTotalResultados(), ofertas, resultado.getAdvertencias(),
                resultado.getProductoMvp().map(mvp -> ProductoMvpDto.desde(mvp, resultado)).orElse(null),
                quizasQuisoDecir);
    }
}
