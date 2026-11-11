package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.service.ServicioBusquedaFlexible;

import java.util.List;

/**
 * Respuesta de {@code GET /api/v1/busqueda}.
 *
 * @param termino texto que se buscó de verdad (corregido, si la persona escribió con errores)
 * @param pagina página devuelta
 * @param tamano resultados por página
 * @param totalResultados total de ofertas encontradas
 * @param ofertas ofertas de la página
 * @param advertencias tiendas que no respondieron
 * @param productoMvp mejor opción de compra entre todas las ofertas; {@code null} si no hay ofertas
 * @param terminoOriginal lo que escribió la persona, si se buscó otra cosa; {@code null} si se buscó tal cual
 * @param busquedaAmpliada {@code true} si no hubo resultados exactos y se buscó solo la palabra principal
 * @param resumenIa resumen de los resultados escrito con IA (Gemini), o {@code null}
 */
public record RespuestaBusquedaDto(String termino, int pagina, int tamano, int totalResultados,
                                   List<OfertaDto> ofertas, List<String> advertencias, ProductoMvpDto productoMvp,
                                   String terminoOriginal, boolean busquedaAmpliada, String resumenIa) {

    /**
     * Construye la respuesta a partir del resultado del servicio.
     *
     * @param consulta consulta realizada
     * @param resultado resultado del servicio de búsqueda
     * @return DTO de respuesta
     */
    public static RespuestaBusquedaDto desde(ConsultaBusqueda consulta, ResultadoBusqueda resultado) {
        return desde(consulta, resultado, null, false);
    }

    /**
     * Construye la respuesta de una búsqueda flexible.
     *
     * @param flexible lo que se buscó y lo que se encontró
     * @return DTO de respuesta
     */
    public static RespuestaBusquedaDto desde(ServicioBusquedaFlexible.Resultado flexible) {
        return desde(flexible.consulta(), flexible.resultado(), flexible.terminoOriginal(), flexible.ampliada());
    }

    private static RespuestaBusquedaDto desde(ConsultaBusqueda consulta, ResultadoBusqueda resultado,
                                              String terminoOriginal, boolean ampliada) {
        List<OfertaDto> ofertas = resultado.getOfertas().stream()
                .map(oferta -> OfertaDto.desde(oferta, resultado.idPersistido(oferta), resultado.esMejorPrecio(oferta)))
                .toList();
        return new RespuestaBusquedaDto(consulta.getTermino(), consulta.getPagina(), consulta.getTamano(),
                resultado.getTotalResultados(), ofertas, resultado.getAdvertencias(),
                resultado.getProductoMvp().map(mvp -> ProductoMvpDto.desde(mvp, resultado)).orElse(null),
                terminoOriginal, ampliada, resultado.getResumenIa().orElse(null));
    }
}
