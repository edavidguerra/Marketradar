package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.service.ServicioBusquedaFlexible;

import java.math.BigDecimal;
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
 * @param precioMinimo precio mínimo que pidió la persona (se compara con el costo conocido: precio más el envío informado), o {@code null}
 * @param precioMaximo precio máximo que pidió la persona (se compara con el costo conocido), o {@code null}
 * @param totalSinFiltro cuántas ofertas hay sin el rango de precio, para decir "hay 12, ninguna en ese rango"
 * @param textoOriginal lo que escribió la persona; se manda de vuelta como {@code original} al pasar de página o
 *                      filtrar, para seguir exigiendo lo que pidió
 * @param requisitos lo que se exigió a cada oferta ("modelo 13", "128 GB", "nuevo"); vacío si nada en particular
 * @param alternativas ofertas que no cumplen todo lo pedido o que no se pudieron confirmar, con lo que les falta
 *                     (solo en la primera página)
 * @param motivoSinRecomendacion por qué no hay recomendación, o {@code null}
 * @param sinTotalConfirmado cuántas ofertas mostradas no tienen el precio total confirmado
 * @param mejorPrecioConfirmado si la oferta más barata tiene el total confirmado; si no, "más barata" es sin contar
 *                              lo que falta
 */
public record RespuestaBusquedaDto(String termino, int pagina, int tamano, int totalResultados,
                                   List<OfertaDto> ofertas, List<String> advertencias, ProductoMvpDto productoMvp,
                                   String terminoOriginal, boolean busquedaAmpliada, String resumenIa,
                                   BigDecimal precioMinimo, BigDecimal precioMaximo, int totalSinFiltro,
                                   String textoOriginal, List<String> requisitos, List<AlternativaDto> alternativas,
                                   String motivoSinRecomendacion, int sinTotalConfirmado,
                                   boolean mejorPrecioConfirmado) {

    /**
     * @param oferta oferta
     * @param nivel {@code sin_confirmar} o {@code incumple}
     * @param motivos qué le falta
     */
    public record AlternativaDto(OfertaDto oferta, String nivel, List<String> motivos) {
    }

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
        ResultadoBusqueda.Detalle detalle = resultado.getDetalle();
        List<AlternativaDto> alternativas = detalle.alternativas().stream()
                .map(a -> new AlternativaDto(OfertaDto.desde(a.oferta(), resultado.idPersistido(a.oferta()), false),
                        a.nivel().name().toLowerCase(java.util.Locale.ROOT), a.motivos()))
                .toList();
        return new RespuestaBusquedaDto(consulta.getTermino(), consulta.getPagina(), consulta.getTamano(),
                resultado.getTotalResultados(), ofertas, resultado.getAdvertencias(),
                resultado.getProductoMvp().map(mvp -> ProductoMvpDto.desde(mvp, resultado)).orElse(null),
                terminoOriginal, ampliada, resultado.getResumenIa().orElse(null), consulta.getPrecioMinimo(),
                consulta.getPrecioMaximo(), resultado.getTotalSinFiltro(), consulta.getTextoOriginal(),
                detalle.requisitos(), alternativas, detalle.motivoSinRecomendacion(), detalle.sinTotalConfirmado(),
                detalle.mejorPrecioConfirmado());
    }
}
