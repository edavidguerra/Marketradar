package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resultado de una búsqueda: ofertas ordenadas y advertencias de los marketplaces que no respondieron.
 */
public final class ResultadoBusqueda {

    private final List<OfertaNormalizada> ofertas;
    private final List<String> advertencias;
    private final int totalResultados;
    private final Map<String, Long> idsPersistidos;
    private final BigDecimal mejorPrecio;
    private final ProductoMvp productoMvp;

    /**
     * Crea el resultado de una búsqueda.
     *
     * @param ofertas ofertas de la página solicitada
     * @param advertencias mensajes sobre marketplaces que fallaron
     * @param totalResultados total de ofertas encontradas antes de paginar
     * @param idsPersistidos identificadores en la base de datos, por clave {@code codigoMarketplace|idExterno}
     * @param mejorPrecio precio total más bajo entre todas las ofertas, {@code null} si no hay ofertas
     * @param productoMvp mejor opción de compra entre todas las ofertas, {@code null} si no hay ofertas
     */
    public ResultadoBusqueda(List<OfertaNormalizada> ofertas, List<String> advertencias, int totalResultados,
                             Map<String, Long> idsPersistidos, BigDecimal mejorPrecio, ProductoMvp productoMvp) {
        this.ofertas = new ArrayList<>(ofertas);
        this.advertencias = new ArrayList<>(advertencias);
        this.totalResultados = totalResultados;
        this.idsPersistidos = Map.copyOf(idsPersistidos);
        this.mejorPrecio = mejorPrecio;
        this.productoMvp = productoMvp;
    }

    /**
     * Identificador guardado de una oferta, para consultar su detalle e historial.
     *
     * @param oferta oferta del resultado
     * @return identificador, o {@code null} si no se pudo guardar
     */
    public Long idPersistido(OfertaNormalizada oferta) {
        return idsPersistidos.get(oferta.clave());
    }

    /**
     * Indica si una oferta tiene el precio total más bajo de toda la búsqueda.
     *
     * @param oferta oferta del resultado
     * @return {@code true} si es el mejor precio
     */
    public boolean esMejorPrecio(OfertaNormalizada oferta) {
        return mejorPrecio != null && oferta.precioTotal().compareTo(mejorPrecio) == 0;
    }

    /**
     * @return mejor opción de compra, si hay ofertas
     */
    public Optional<ProductoMvp> getProductoMvp() {
        return Optional.ofNullable(productoMvp);
    }

    public List<OfertaNormalizada> getOfertas() {
        return new ArrayList<>(ofertas);
    }

    public List<String> getAdvertencias() {
        return new ArrayList<>(advertencias);
    }

    public int getTotalResultados() {
        return totalResultados;
    }
}
