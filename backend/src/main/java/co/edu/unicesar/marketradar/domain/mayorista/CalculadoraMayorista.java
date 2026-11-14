package co.edu.unicesar.marketradar.domain.mayorista;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Calcula cuánto cuesta la mercancía al por mayor para la cantidad que pide la persona, con la escala de precio que le
 * corresponde. Reglas:
 * <ul>
 *   <li>Respeta el pedido mínimo: por debajo no hay precio, se dice cuánto es el mínimo.</li>
 *   <li>No convierte unidades sin una equivalencia del proveedor: si vende por pares y se piden piezas, no aplica.
 *       Con una presentación confirmada ("paquete de 10 piezas"), las piezas se pasan a paquetes completos, hacia
 *       arriba, y se dice.</li>
 *   <li>Si las escalas se contradicen, tienen un hueco en esa cantidad, o solo hay un rango de precios, el precio es
 *       ambiguo y la oferta no entra al orden por precio.</li>
 *   <li>Es solo el valor de la mercancía: el envío y los impuestos no se conocen y no se inventan.</li>
 * </ul>
 */
public final class CalculadoraMayorista {

    /** Diferencia aceptada entre dos precios que deberían ser iguales (redondeos de la página). */
    static final BigDecimal TOLERANCIA = new BigDecimal("0.005");

    private CalculadoraMayorista() {
    }

    /** Si el precio se puede usar para comparar. */
    public enum Estado { APLICABLE, BAJO_MINIMO, UNIDAD_INCOMPATIBLE, PRECIO_AMBIGUO }

    /**
     * Resultado del cálculo.
     *
     * @param estado si aplica o por qué no
     * @param motivo explicación para la persona, o {@code null} si aplica sin novedades
     * @param cantidadCalculada cantidad en la unidad del proveedor con que se calculó
     * @param unidad unidad del proveedor
     * @param escala escala que corresponde, o {@code null}
     * @param precioUnitario precio por unidad del proveedor, o {@code null}
     * @param subtotal precio unitario por cantidad, o {@code null}
     * @param ajustes cambios que se hicieron a la cantidad (paquetes completos), para decirlos
     */
    public record Calculo(Estado estado, String motivo, long cantidadCalculada, UnidadVenta unidad, EscalaPrecio escala,
                          BigDecimal precioUnitario, BigDecimal subtotal, List<String> ajustes) {

        /** @return {@code true} si el subtotal se puede usar para ordenar */
        public boolean aplicable() {
            return estado == Estado.APLICABLE;
        }
    }

    /**
     * @param oferta publicación normalizada
     * @param cantidad cantidad que pide la persona
     * @param unidadPedida unidad en que la pide
     * @param varianteId variante elegida, o {@code null}
     * @return cálculo
     */
    public static Calculo calcular(OfertaMayorista oferta, long cantidad, UnidadVenta unidadPedida, String varianteId) {
        List<String> ajustes = new ArrayList<>();
        UnidadVenta unidad = oferta.unidad();
        if (unidad == UnidadVenta.DESCONOCIDA) {
            return sinPrecio(Estado.PRECIO_AMBIGUO, "El proveedor no dice en qué unidad vende (piezas, pares, paquetes).",
                    cantidad, unidad, ajustes);
        }
        long enUnidadDelProveedor = cantidad;
        if (unidadPedida != unidad) {
            OfertaMayorista.Presentacion presentacion = oferta.presentacion();
            if (presentacion == null || presentacion.unidadBase() != unidadPedida || presentacion.cantidad() < 1) {
                return sinPrecio(Estado.UNIDAD_INCOMPATIBLE, "Vende por " + unidad.plural() + " y usted pidió "
                        + unidadPedida.plural() + ". No lo convertimos porque el proveedor no informa cuántas "
                        + unidadPedida.plural() + " trae cada " + unidad.singular() + ".", cantidad, unidad, ajustes);
            }
            enUnidadDelProveedor = (cantidad + presentacion.cantidad() - 1) / presentacion.cantidad();
            long recibe = enUnidadDelProveedor * presentacion.cantidad();
            ajustes.add("Cada " + unidad.singular() + " trae " + unidadPedida.con(presentacion.cantidad())
                    + ": se calculó con " + unidad.con(enUnidadDelProveedor)
                    + (recibe != cantidad ? " (" + unidadPedida.con(recibe) + ")" : "") + ".");
        }

        Long minimo = minimoEfectivo(oferta);
        if (minimo != null && enUnidadDelProveedor < minimo) {
            return sinPrecio(Estado.BAJO_MINIMO, "El pedido mínimo es de " + unidad.con(minimo)
                    + ". Con menos no le venden.", enUnidadDelProveedor, unidad, ajustes);
        }

        Optional<String> problema = problemaEscalas(oferta.escalas());
        if (problema.isPresent()) {
            return sinPrecio(Estado.PRECIO_AMBIGUO, problema.get(), enUnidadDelProveedor, unidad, ajustes);
        }
        BigDecimal precio;
        EscalaPrecio escala = null;
        if (!oferta.escalas().isEmpty()) {
            long finalCantidad = enUnidadDelProveedor;
            Optional<EscalaPrecio> encontrada = oferta.escalas().stream().filter(e -> e.incluye(finalCantidad)).findFirst();
            if (encontrada.isEmpty()) {
                return sinPrecio(Estado.PRECIO_AMBIGUO, "El proveedor no da un precio para " + unidad.con(finalCantidad)
                        + ". Pregúntele en Alibaba.", enUnidadDelProveedor, unidad, ajustes);
            }
            escala = encontrada.get();
            precio = escala.precio();
        } else if (oferta.precioMinimo() != null && oferta.precioMaximo() != null
                && casiIgual(oferta.precioMinimo(), oferta.precioMaximo())) {
            precio = oferta.precioMinimo();
        } else {
            return sinPrecio(Estado.PRECIO_AMBIGUO, "Solo muestra un rango de precios ("
                    + (oferta.textoPrecio() == null ? "sin escalas" : oferta.textoPrecio())
                    + ") sin decir cuánto cuesta por cantidad.", enUnidadDelProveedor, unidad, ajustes);
        }

        if (varianteId != null && !varianteId.isBlank()) {
            Optional<OfertaMayorista.Variante> variante = oferta.variantes().stream()
                    .filter(v -> varianteId.equals(v.id())).findFirst();
            if (variante.isEmpty()) {
                return sinPrecio(Estado.PRECIO_AMBIGUO, "La variante elegida ya no aparece en la publicación.",
                        enUnidadDelProveedor, unidad, ajustes);
            }
            if (Boolean.FALSE.equals(variante.get().disponible())) {
                return sinPrecio(Estado.PRECIO_AMBIGUO, "El proveedor marca la variante elegida como no disponible.",
                        enUnidadDelProveedor, unidad, ajustes);
            }
            BigDecimal precioVariante = variante.get().precio();
            if (precioVariante != null && !casiIgual(precioVariante, precio)) {
                boolean esDeAlgunaEscala = oferta.escalas().stream().anyMatch(e -> casiIgual(e.precio(), precioVariante));
                if (!oferta.escalas().isEmpty() && !esDeAlgunaEscala) {
                    return sinPrecio(Estado.PRECIO_AMBIGUO, "La variante tiene un precio propio que no coincide con las "
                            + "escalas por cantidad. Confírmelo con el proveedor.", enUnidadDelProveedor, unidad, ajustes);
                }
                if (oferta.escalas().isEmpty()) {
                    precio = precioVariante;
                }
            }
        }
        BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(enUnidadDelProveedor)).setScale(2, RoundingMode.HALF_UP);
        return new Calculo(Estado.APLICABLE, null, enUnidadDelProveedor, unidad, escala, precio, subtotal,
                List.copyOf(ajustes));
    }

    /**
     * El pedido mínimo que vale: el mayor entre el que se informa y el de la primera escala (si la primera escala
     * empieza más arriba, por debajo no hay precio). Si el mínimo está en otra unidad, no se usa.
     */
    static Long minimoEfectivo(OfertaMayorista oferta) {
        Long informado = oferta.pedidoMinimo() != null && oferta.unidadPedidoMinimo() == oferta.unidad()
                ? oferta.pedidoMinimo() : null;
        Long deEscalas = oferta.escalas().stream().map(EscalaPrecio::minimo).min(Long::compare).orElse(null);
        if (informado == null) {
            return deEscalas;
        }
        return deEscalas == null ? informado : Math.max(informado, deEscalas);
    }

    /**
     * Revisa que las escalas no se contradigan: ordenadas, sin pisarse y sin que el precio suba al comprar más.
     *
     * @param escalas escalas
     * @return qué está mal, o vacío
     */
    public static Optional<String> problemaEscalas(List<EscalaPrecio> escalas) {
        List<EscalaPrecio> ordenadas = escalas.stream().sorted(Comparator.comparingLong(EscalaPrecio::minimo)).toList();
        for (int i = 1; i < ordenadas.size(); i++) {
            EscalaPrecio anterior = ordenadas.get(i - 1);
            EscalaPrecio actual = ordenadas.get(i);
            if (anterior.maximo() == null || anterior.maximo() >= actual.minimo()) {
                return Optional.of("Las escalas de precio se pisan entre sí: no se sabe cuál aplica.");
            }
            if (actual.precio().compareTo(anterior.precio()) > 0) {
                return Optional.of("El precio sube al comprar más cantidad: las escalas parecen estar mal.");
            }
        }
        return Optional.empty();
    }

    /**
     * Qué no cuadra en una publicación de la búsqueda; si hay algo, conviene consultar el detalle.
     *
     * @param oferta publicación
     * @return discrepancias encontradas, vacías si todo cuadra
     */
    public static List<String> discrepancias(OfertaMayorista oferta) {
        List<String> encontradas = new ArrayList<>();
        if (oferta.escalas().isEmpty()) {
            encontradas.add("sin escalas de precio");
        } else {
            BigDecimal menor = oferta.escalas().stream().map(EscalaPrecio::precio).min(BigDecimal::compareTo).orElseThrow();
            BigDecimal mayor = oferta.escalas().stream().map(EscalaPrecio::precio).max(BigDecimal::compareTo).orElseThrow();
            if (oferta.precioMinimo() != null && !casiIgual(oferta.precioMinimo(), menor)
                    || oferta.precioMaximo() != null && !casiIgual(oferta.precioMaximo(), mayor)) {
                encontradas.add("el rango de precio no coincide con las escalas");
            }
            problemaEscalas(oferta.escalas()).ifPresent(encontradas::add);
        }
        if (oferta.unidad() == UnidadVenta.DESCONOCIDA) {
            encontradas.add("unidad desconocida");
        }
        if (oferta.pedidoMinimo() == null) {
            encontradas.add("sin pedido mínimo");
        } else if (oferta.unidadPedidoMinimo() != oferta.unidad()) {
            encontradas.add("el pedido mínimo está en otra unidad");
        } else if (!oferta.escalas().isEmpty() && oferta.escalas().get(0).minimo() != oferta.pedidoMinimo()) {
            encontradas.add("el pedido mínimo no coincide con la primera escala");
        }
        return encontradas;
    }

    /**
     * Diferencias entre los precios de la búsqueda y los del detalle.
     *
     * @param busqueda publicación según la búsqueda
     * @param detalle publicación según el detalle
     * @return {@code true} si las escalas o el mínimo no coinciden
     */
    public static boolean seContradicen(OfertaMayorista busqueda, OfertaMayorista detalle) {
        if (busqueda.escalas().isEmpty() || detalle.escalas().isEmpty()) {
            return false;
        }
        if (busqueda.escalas().size() != detalle.escalas().size()) {
            return true;
        }
        for (int i = 0; i < busqueda.escalas().size(); i++) {
            EscalaPrecio a = busqueda.escalas().get(i);
            EscalaPrecio b = detalle.escalas().get(i);
            if (a.minimo() != b.minimo() || !casiIgual(a.precio(), b.precio())) {
                return true;
            }
        }
        return false;
    }

    static boolean casiIgual(BigDecimal a, BigDecimal b) {
        BigDecimal mayor = a.abs().max(b.abs());
        return mayor.signum() == 0 || a.subtract(b).abs().compareTo(mayor.multiply(TOLERANCIA)) <= 0;
    }

    private static Calculo sinPrecio(Estado estado, String motivo, long cantidad, UnidadVenta unidad, List<String> ajustes) {
        return new Calculo(estado, motivo, cantidad, unidad, null, null, null, List.copyOf(ajustes));
    }
}
