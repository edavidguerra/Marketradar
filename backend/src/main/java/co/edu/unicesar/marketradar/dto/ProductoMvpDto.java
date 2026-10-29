package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.ProductoMvp;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;

import java.util.List;

/**
 * Mejor opción de compra de la búsqueda.
 *
 * @param oferta oferta recomendada
 * @param puntaje puntaje de 0 a 100
 * @param razones por qué se recomienda, en frases cortas
 * @param enlaceCompra enlace que abre exactamente este producto en la tienda
 */
public record ProductoMvpDto(OfertaDto oferta, int puntaje, List<String> razones, String enlaceCompra) {

    /**
     * @param mvp producto elegido
     * @param resultado resultado de la búsqueda, para el identificador y el mejor precio
     * @return DTO
     */
    public static ProductoMvpDto desde(ProductoMvp mvp, ResultadoBusqueda resultado) {
        OfertaDto oferta = OfertaDto.desde(mvp.oferta(), resultado.idPersistido(mvp.oferta()),
                resultado.esMejorPrecio(mvp.oferta()));
        return new ProductoMvpDto(oferta, mvp.puntaje(), mvp.razones(), mvp.oferta().getUrlDestino());
    }
}
