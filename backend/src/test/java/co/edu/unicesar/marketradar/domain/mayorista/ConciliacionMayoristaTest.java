package co.edu.unicesar.marketradar.domain.mayorista;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Búsqueda contra ficha: el caso real de US$0,49 por par que figuraba como el más barato, mientras la ficha no
 * confirmaba el precio y mostraba variantes a US$5,00.
 */
class ConciliacionMayoristaTest {

    private static OfertaMayorista oferta(String fuente, Long minimo, UnidadVenta unidad, List<EscalaPrecio> escalas,
                                          String min, String max, String moneda,
                                          List<OfertaMayorista.Variante> variantes) {
        return new OfertaMayorista("1600000000077", "Stainless steel earrings", "https://www.alibaba.com/product-detail/x_1600000000077.html",
                null, "Yiwu", "CN", 3, false, false, minimo, unidad, unidad, null, escalas,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), moneda, null,
                variantes, null, fuente);
    }

    private static EscalaPrecio e(long min, Long max, String precio) {
        return new EscalaPrecio(min, max, new BigDecimal(precio));
    }

    private static final OfertaMayorista BUSQUEDA_049 = oferta("busqueda", 12L, UnidadVenta.PAR,
            List.of(e(12, null, "0.49")), "0.49", "0.49", "USD", List.of());

    @Test
    void laFichaSinEscalasNoConfirmaLasDeLaBusquedaYSusVariantesLasContradicen() {
        OfertaMayorista ficha = oferta("detalle", null, UnidadVenta.PAR, List.of(), null, null, "USD", List.of(
                new OfertaMayorista.Variante("sku-1", "Dorado", new BigDecimal("5.00"), true),
                new OfertaMayorista.Variante("sku-2", "Plateado", new BigDecimal("5.00"), true)));
        CalculadoraMayorista.Conciliacion c = CalculadoraMayorista.conciliar(BUSQUEDA_049, ficha);
        assertTrue(c.hayContradiccion());
        assertTrue(c.contradicciones().get(0).contains("US$0,49"), c.contradicciones().toString());
        assertTrue(c.contradicciones().get(0).contains("US$5,00"), c.contradicciones().toString());
        assertTrue(c.oferta().escalas().isEmpty(), "No se reutilizan las escalas de la búsqueda");
        CalculadoraMayorista.Calculo calculo = CalculadoraMayorista.calcular(c.oferta(), 100, UnidadVenta.PAR, null);
        assertFalse(calculo.aplicable(), "Sin escalas confirmadas no hay subtotal");
        assertNull(calculo.subtotal());
    }

    @Test
    void contrastaMonedaUnidadYPedidoMinimo() {
        OfertaMayorista ficha = oferta("detalle", 100L, UnidadVenta.PIEZA, List.of(e(100, null, "0.49")), "0.49", "0.49",
                "CNY", List.of());
        List<String> c = CalculadoraMayorista.conciliar(BUSQUEDA_049, ficha).contradicciones();
        assertTrue(c.stream().anyMatch(t -> t.contains("USD") && t.contains("CNY")), c.toString());
        assertTrue(c.stream().anyMatch(t -> t.contains("pares") && t.contains("piezas")), c.toString());
    }

    @Test
    void elPedidoMinimoDistintoEsUnaContradiccion() {
        OfertaMayorista ficha = oferta("detalle", 120L, UnidadVenta.PAR, List.of(e(12, null, "0.49")), "0.49", "0.49",
                "USD", List.of());
        List<String> c = CalculadoraMayorista.conciliar(BUSQUEDA_049, ficha).contradicciones();
        assertTrue(c.stream().anyMatch(t -> t.contains("pedido mínimo")), c.toString());
    }

    @Test
    void siLaFichaConfirmaLasEscalasNoHayContradiccion() {
        OfertaMayorista ficha = oferta("detalle", 12L, UnidadVenta.PAR, List.of(e(12, null, "0.49")), "0.49", "0.49",
                "USD", List.of(new OfertaMayorista.Variante("sku-1", "Dorado", new BigDecimal("0.49"), true)));
        CalculadoraMayorista.Conciliacion c = CalculadoraMayorista.conciliar(BUSQUEDA_049, ficha);
        assertFalse(c.hayContradiccion(), c.contradicciones().toString());
        CalculadoraMayorista.Calculo calculo = CalculadoraMayorista.calcular(c.oferta(), 100, UnidadVenta.PAR, "sku-1");
        assertTrue(calculo.aplicable());
        assertEquals(new BigDecimal("49.00"), calculo.subtotal());
    }

    @Test
    void variantesQueNoCoincidenConNingunaEscalaDejanElPrecioSinConfirmar() {
        OfertaMayorista ficha = oferta("detalle", 12L, UnidadVenta.PAR, List.of(e(12, null, "0.49")), "0.49", "0.49",
                "USD", List.of(new OfertaMayorista.Variante("sku-1", "Dorado", new BigDecimal("5.00"), true)));
        assertTrue(CalculadoraMayorista.conciliar(BUSQUEDA_049, ficha).hayContradiccion());
        assertTrue(CalculadoraMayorista.conciliar(null, ficha).hayContradiccion(), "También al abrir la ficha sola");
    }

    @Test
    void elPrecioDeUnaVarianteNoSeAplicaACualquierCantidad() {
        // Escalas 12-99 a 0,80 y 100 o más a 0,49; la variante dice 0,80. Para 500 pares vale la escala de 0,49, no
        // el precio suelto de la variante.
        OfertaMayorista ficha = oferta("detalle", 12L, UnidadVenta.PAR, List.of(e(12, 99L, "0.80"), e(100, null, "0.49")),
                "0.49", "0.80", "USD", List.of(new OfertaMayorista.Variante("sku-1", "Dorado", new BigDecimal("0.80"), true)));
        CalculadoraMayorista.Calculo para500 = CalculadoraMayorista.calcular(ficha, 500, UnidadVenta.PAR, "sku-1");
        assertFalse(para500.aplicable());
        assertTrue(para500.motivo().contains("para qué cantidades"), para500.motivo());
        CalculadoraMayorista.Calculo para50 = CalculadoraMayorista.calcular(ficha, 50, UnidadVenta.PAR, "sku-1");
        assertTrue(para50.aplicable(), "Para 50 la variante coincide con su escala");
        assertEquals(new BigDecimal("40.00"), para50.subtotal());
    }

    @Test
    void sinEscalasElPrecioDeLaVarianteNoReemplazaAlPrecioFijo() {
        OfertaMayorista ficha = oferta("detalle", 10L, UnidadVenta.PAR, List.of(), "1.00", "1.00", "USD",
                List.of(new OfertaMayorista.Variante("sku-1", "Dorado", new BigDecimal("5.00"), true)));
        assertFalse(CalculadoraMayorista.calcular(ficha, 20, UnidadVenta.PAR, "sku-1").aplicable());
        assertTrue(CalculadoraMayorista.calcular(ficha, 20, UnidadVenta.PAR, null).aplicable());
    }
}
