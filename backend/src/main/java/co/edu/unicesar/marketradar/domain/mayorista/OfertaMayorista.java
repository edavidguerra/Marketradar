package co.edu.unicesar.marketradar.domain.mayorista;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Una publicación al por mayor (Alibaba), ya normalizada: proveedor, pedido mínimo, escalas de precio y variantes.
 *
 * @param id identificador del producto en Alibaba
 * @param titulo nombre del producto
 * @param url enlace a la publicación en Alibaba
 * @param imagen foto, o {@code null}
 * @param proveedor nombre del proveedor, o {@code null}
 * @param paisProveedor país del proveedor (código de dos letras o nombre), o {@code null}
 * @param aniosProveedor años del proveedor en Alibaba, o {@code null}
 * @param proveedorVerificado si Alibaba lo marca como verificado
 * @param patrocinado si es un anuncio pagado
 * @param pedidoMinimo cantidad mínima, o {@code null} si no se informa
 * @param unidadPedidoMinimo unidad del pedido mínimo
 * @param unidad unidad en que vende (la de las escalas de precio)
 * @param presentacion cuántas unidades base trae cada unidad de venta, si el proveedor lo informa; o {@code null}
 * @param escalas precios por cantidad, de la menor a la mayor
 * @param precioMinimo precio unitario más bajo que anuncia, o {@code null}
 * @param precioMaximo precio unitario más alto que anuncia, o {@code null}
 * @param moneda moneda de los precios
 * @param textoPrecio el precio como lo escribe Alibaba, por ejemplo "US $1.75-$2.00"
 * @param variantes variantes con precio propio (color, talla...), si las hay
 * @param actualizadoEn cuándo se consultó la fuente
 * @param fuente {@code busqueda} o {@code detalle}
 */
public record OfertaMayorista(String id, String titulo, String url, String imagen, String proveedor, String paisProveedor,
                              Integer aniosProveedor, boolean proveedorVerificado, boolean patrocinado,
                              Long pedidoMinimo, UnidadVenta unidadPedidoMinimo, UnidadVenta unidad,
                              Presentacion presentacion, List<EscalaPrecio> escalas, BigDecimal precioMinimo,
                              BigDecimal precioMaximo, String moneda, String textoPrecio, List<Variante> variantes,
                              OffsetDateTime actualizadoEn, String fuente) {

    /** Copia las listas. */
    public OfertaMayorista {
        escalas = escalas == null ? List.of() : List.copyOf(escalas);
        variantes = variantes == null ? List.of() : List.copyOf(variantes);
        unidad = unidad == null ? UnidadVenta.DESCONOCIDA : unidad;
        unidadPedidoMinimo = unidadPedidoMinimo == null ? UnidadVenta.DESCONOCIDA : unidadPedidoMinimo;
    }

    /**
     * Cuántas unidades base trae cada unidad de venta: por ejemplo, un paquete de 10 piezas.
     *
     * @param cantidad unidades base por unidad de venta
     * @param unidadBase unidad base (piezas, pares)
     */
    public record Presentacion(long cantidad, UnidadVenta unidadBase) {
    }

    /**
     * Una variante con su precio.
     *
     * @param id identificador (SKU)
     * @param nombre opciones, por ejemplo "Rojo / Talla M"
     * @param precio precio unitario de la variante, o {@code null}
     * @param disponible si el proveedor la marca como disponible, o {@code null} si no lo dice
     */
    public record Variante(String id, String nombre, BigDecimal precio, Boolean disponible) {
    }
}
