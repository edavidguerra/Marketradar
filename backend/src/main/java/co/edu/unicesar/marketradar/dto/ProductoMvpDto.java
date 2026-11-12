package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.ProductoMvp;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;

import java.util.List;

/**
 * Mejor opción de compra de la búsqueda.
 *
 * @param oferta oferta recomendada
 * @param puntaje puntaje de conveniencia de 0 a 100, comparado con las demás ofertas que cumplen
 * @param razones por qué se recomienda, en frases cortas
 * @param enlaceCompra enlace de la oferta (lo que abre está en {@code oferta.enlace})
 * @param confianza {@code alta}, {@code media} o {@code baja}: qué tan completa es la información
 * @param faltantes qué información faltó
 * @param comparadas con cuántas ofertas se comparó
 */
public record ProductoMvpDto(OfertaDto oferta, int puntaje, List<String> razones, String enlaceCompra,
                             String confianza, List<String> faltantes, int comparadas) {

    /**
     * @param mvp producto elegido
     * @param resultado resultado de la búsqueda, para el identificador y el mejor precio
     * @return DTO
     */
    public static ProductoMvpDto desde(ProductoMvp mvp, ResultadoBusqueda resultado) {
        OfertaDto oferta = OfertaDto.desde(mvp.oferta(), resultado.idPersistido(mvp.oferta()),
                resultado.esMejorPrecio(mvp.oferta()));
        return new ProductoMvpDto(oferta, mvp.puntaje(), mvp.razones(), mvp.oferta().getUrlDestino(),
                mvp.confianza().name().toLowerCase(java.util.Locale.ROOT), mvp.faltantes(), mvp.comparadas());
    }
}
