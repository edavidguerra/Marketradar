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
    private final String resumenIa;
    private final int totalSinFiltro;
    private final Detalle detalle;

    /**
     * Crea el resultado de una búsqueda.
     *
     * @param ofertas ofertas de la página solicitada
     * @param advertencias mensajes sobre marketplaces que fallaron
     * @param totalResultados total de ofertas encontradas antes de paginar
     * @param idsPersistidos identificadores en la base de datos, por clave {@code codigoMarketplace|idExterno}
     * @param mejorPrecio costo conocido más bajo entre las ofertas que cumplen, {@code null} si no hay
     * @param productoMvp mejor opción de compra entre todas las ofertas, {@code null} si no hay ofertas
     */
    public ResultadoBusqueda(List<OfertaNormalizada> ofertas, List<String> advertencias, int totalResultados,
                             Map<String, Long> idsPersistidos, BigDecimal mejorPrecio, ProductoMvp productoMvp) {
        this(ofertas, advertencias, totalResultados, idsPersistidos, mejorPrecio, productoMvp, null);
    }

    /**
     * Crea el resultado de una búsqueda con el resumen escrito por la IA.
     *
     * @param ofertas ofertas de la página solicitada
     * @param advertencias mensajes sobre marketplaces que fallaron
     * @param totalResultados total de ofertas encontradas antes de paginar
     * @param idsPersistidos identificadores en la base de datos, por clave {@code codigoMarketplace|idExterno}
     * @param mejorPrecio costo conocido más bajo entre las ofertas que cumplen, {@code null} si no hay
     * @param productoMvp mejor opción de compra entre todas las ofertas, {@code null} si no hay ofertas
     * @param resumenIa resumen de los resultados escrito con IA, o {@code null}
     */
    public ResultadoBusqueda(List<OfertaNormalizada> ofertas, List<String> advertencias, int totalResultados,
                             Map<String, Long> idsPersistidos, BigDecimal mejorPrecio, ProductoMvp productoMvp,
                             String resumenIa) {
        this(ofertas, advertencias, totalResultados, idsPersistidos, mejorPrecio, productoMvp, resumenIa,
                totalResultados);
    }

    /**
     * @param totalSinFiltro cuántas ofertas había antes de aplicar el rango de precio de la persona
     */
    public ResultadoBusqueda(List<OfertaNormalizada> ofertas, List<String> advertencias, int totalResultados,
                             Map<String, Long> idsPersistidos, BigDecimal mejorPrecio, ProductoMvp productoMvp,
                             String resumenIa, int totalSinFiltro) {
        this(ofertas, advertencias, totalResultados, idsPersistidos, mejorPrecio, productoMvp, resumenIa,
                totalSinFiltro, Detalle.VACIO);
    }

    /**
     * Resultado completo, con las ofertas que no cumplen lo pedido y por qué no hay recomendación.
     *
     * @param ofertas ofertas de la página que cumplen lo que pidió la persona
     * @param advertencias tiendas que no respondieron
     * @param totalResultados cuántas ofertas cumplen (en todas las páginas)
     * @param idsPersistidos identificadores guardados, por clave
     * @param mejorPrecio costo conocido más bajo entre las que cumplen, o {@code null}
     * @param productoMvp recomendación, o {@code null} si no hay una que se pueda justificar
     * @param resumenIa resumen de la IA, o {@code null}
     * @param totalSinFiltro cuántas cumplen sin el rango de precio
     * @param detalle alternativas, requisitos y explicación
     */
    public ResultadoBusqueda(List<OfertaNormalizada> ofertas, List<String> advertencias, int totalResultados,
                             Map<String, Long> idsPersistidos, BigDecimal mejorPrecio, ProductoMvp productoMvp,
                             String resumenIa, int totalSinFiltro, Detalle detalle) {
        this.detalle = detalle == null ? Detalle.VACIO : detalle;
        this.resumenIa = resumenIa;
        this.totalSinFiltro = totalSinFiltro;
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
     * Indica si una oferta tiene el costo conocido más bajo de la búsqueda (puede no incluir el envío).
     *
     * @param oferta oferta del resultado
     * @return {@code true} si es el mejor precio
     */
    public boolean esMejorPrecio(OfertaNormalizada oferta) {
        return mejorPrecio != null && oferta.costoConocido().compareTo(mejorPrecio) == 0;
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

    /**
     * @return resumen de los resultados escrito con IA, si lo hay
     */
    public Optional<String> getResumenIa() {
        return Optional.ofNullable(resumenIa);
    }

    public List<String> getAdvertencias() {
        return new ArrayList<>(advertencias);
    }

    public int getTotalResultados() {
        return totalResultados;
    }

    /** @return cuántas ofertas había sin el rango de precio; igual a {@link #getTotalResultados()} si no hay rango */
    public int getTotalSinFiltro() {
        return totalSinFiltro;
    }

    /** @return alternativas, requisitos y por qué no hay recomendación */
    public Detalle getDetalle() {
        return detalle;
    }

    /**
     * Oferta que no cumple todo lo que pidió la persona, o que no se pudo confirmar.
     *
     * @param oferta oferta
     * @param nivel {@code SIN_CONFIRMAR} o {@code INCUMPLE}
     * @param motivos qué le falta, en frases cortas
     */
    public record Alternativa(OfertaNormalizada oferta, RequisitosBusqueda.Nivel nivel, List<String> motivos) {
        public Alternativa {
            motivos = List.copyOf(motivos);
        }
    }

    /**
     * Lo que acompaña a las ofertas.
     *
     * @param alternativas ofertas que no cumplen todo lo pedido, primero las que solo falta confirmar
     * @param requisitos lo que se exigió ("modelo 13", "128 GB", "nuevo")
     * @param motivoSinRecomendacion por qué no se recomienda ninguna, o {@code null} si hay recomendación
     * @param sinTotalConfirmado cuántas ofertas que cumplen no tienen el precio total confirmado
     * @param mejorPrecioConfirmado si la oferta más barata tiene su total confirmado
     */
    public record Detalle(List<Alternativa> alternativas, List<String> requisitos, String motivoSinRecomendacion,
                          int sinTotalConfirmado, boolean mejorPrecioConfirmado) {
        public static final Detalle VACIO = new Detalle(List.of(), List.of(), null, 0, true);

        public Detalle {
            alternativas = List.copyOf(alternativas);
            requisitos = List.copyOf(requisitos);
        }
    }
}
