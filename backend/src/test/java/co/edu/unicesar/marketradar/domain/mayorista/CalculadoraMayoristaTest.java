package co.edu.unicesar.marketradar.domain.mayorista;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculadoraMayoristaTest {

    private static EscalaPrecio escala(long min, Long max, String precio) {
        return new EscalaPrecio(min, max, new BigDecimal(precio));
    }

    private static final List<EscalaPrecio> ESCALAS = List.of(
            escala(100, 499L, "2.00"), escala(500, 999L, "1.80"), escala(1000, null, "1.50"));

    private static OfertaMayorista oferta(Long minimo, UnidadVenta unidad, OfertaMayorista.Presentacion presentacion,
                                          List<EscalaPrecio> escalas, List<OfertaMayorista.Variante> variantes) {
        return new OfertaMayorista("1600000000001", "Cotton socks", "https://www.alibaba.com/product-detail/x_1600000000001.html",
                null, "Proveedor S.A.", "CN", 5, true, false, minimo, unidad, unidad, presentacion, escalas,
                new BigDecimal("1.50"), new BigDecimal("2.00"), "USD", "US $1.50-2.00", variantes, null, "detalle");
    }

    private static OfertaMayorista pares() {
        return oferta(100L, UnidadVenta.PAR, null, ESCALAS, List.of());
    }

    @Test
    void usaLaEscalaQueCorrespondeALaCantidad() {
        CalculadoraMayorista.Calculo c = CalculadoraMayorista.calcular(pares(), 600, UnidadVenta.PAR, null);
        assertTrue(c.aplicable());
        assertEquals(new BigDecimal("1.80"), c.precioUnitario());
        assertEquals(new BigDecimal("1080.00"), c.subtotal());
        assertEquals(500, c.escala().minimo());
    }

    @Test
    void enLosLimitesDeLasEscalasCambiaElPrecio() {
        assertEquals(new BigDecimal("2.00"), CalculadoraMayorista.calcular(pares(), 499, UnidadVenta.PAR, null).precioUnitario());
        assertEquals(new BigDecimal("1.80"), CalculadoraMayorista.calcular(pares(), 500, UnidadVenta.PAR, null).precioUnitario());
        assertEquals(new BigDecimal("1.50"), CalculadoraMayorista.calcular(pares(), 1000, UnidadVenta.PAR, null).precioUnitario());
        assertEquals(new BigDecimal("1.50"), CalculadoraMayorista.calcular(pares(), 50000, UnidadVenta.PAR, null).precioUnitario());
    }

    @Test
    void pordebajoDelPedidoMinimoNoHayPrecio() {
        CalculadoraMayorista.Calculo c = CalculadoraMayorista.calcular(pares(), 99, UnidadVenta.PAR, null);
        assertEquals(CalculadoraMayorista.Estado.BAJO_MINIMO, c.estado());
        assertNull(c.subtotal());
        assertTrue(c.motivo().contains("100 pares"));
    }

    @Test
    void siLaPrimeraEscalaEmpiezaMasArribaQueElMinimoValeLaEscala() {
        OfertaMayorista o = oferta(50L, UnidadVenta.PAR, null, ESCALAS, List.of());
        assertEquals(CalculadoraMayorista.Estado.BAJO_MINIMO, CalculadoraMayorista.calcular(o, 80, UnidadVenta.PAR, null).estado());
    }

    @Test
    void paresYPiezasNoSeMezclanSinEquivalencia() {
        CalculadoraMayorista.Calculo c = CalculadoraMayorista.calcular(pares(), 600, UnidadVenta.PIEZA, null);
        assertEquals(CalculadoraMayorista.Estado.UNIDAD_INCOMPATIBLE, c.estado());
        assertNull(c.subtotal());
    }

    @Test
    void conPresentacionConfirmadaSeRedondeaAPaquetesCompletos() {
        OfertaMayorista paquetes = oferta(10L, UnidadVenta.PAQUETE,
                new OfertaMayorista.Presentacion(12, UnidadVenta.PIEZA),
                List.of(escala(10, 49L, "6.00"), escala(50, null, "5.00")), List.of());
        CalculadoraMayorista.Calculo c = CalculadoraMayorista.calcular(paquetes, 130, UnidadVenta.PIEZA, null);
        assertTrue(c.aplicable());
        assertEquals(11, c.cantidadCalculada(), "130 piezas son 11 paquetes de 12 (132 piezas)");
        assertEquals(new BigDecimal("66.00"), c.subtotal());
        assertTrue(c.ajustes().get(0).contains("132 piezas"));
    }

    @Test
    void escalasQueSePisanSonAmbiguas() {
        OfertaMayorista o = oferta(100L, UnidadVenta.PIEZA, null,
                List.of(escala(100, 600L, "2.00"), escala(500, null, "1.80")), List.of());
        assertEquals(CalculadoraMayorista.Estado.PRECIO_AMBIGUO, CalculadoraMayorista.calcular(o, 550, UnidadVenta.PIEZA, null).estado());
    }

    @Test
    void siElPrecioSubeConLaCantidadEsAmbiguo() {
        OfertaMayorista o = oferta(100L, UnidadVenta.PIEZA, null,
                List.of(escala(100, 499L, "1.00"), escala(500, null, "1.20")), List.of());
        assertEquals(CalculadoraMayorista.Estado.PRECIO_AMBIGUO, CalculadoraMayorista.calcular(o, 600, UnidadVenta.PIEZA, null).estado());
    }

    @Test
    void unRangoSinEscalasNoSeInterpreta() {
        OfertaMayorista o = oferta(100L, UnidadVenta.PIEZA, null, List.of(), List.of());
        CalculadoraMayorista.Calculo c = CalculadoraMayorista.calcular(o, 600, UnidadVenta.PIEZA, null);
        assertEquals(CalculadoraMayorista.Estado.PRECIO_AMBIGUO, c.estado());
        assertTrue(c.motivo().contains("rango"));
    }

    @Test
    void sinUnidadConocidaNoSeCalcula() {
        OfertaMayorista o = oferta(100L, UnidadVenta.DESCONOCIDA, null, ESCALAS, List.of());
        assertEquals(CalculadoraMayorista.Estado.PRECIO_AMBIGUO, CalculadoraMayorista.calcular(o, 600, UnidadVenta.PIEZA, null).estado());
    }

    @Test
    void unaVarianteConPrecioQueNoCuadraConLasEscalasEsAmbigua() {
        OfertaMayorista o = oferta(100L, UnidadVenta.PAR, null, ESCALAS, List.of(
                new OfertaMayorista.Variante("sku-rojo", "Rojo", new BigDecimal("1.80"), true),
                new OfertaMayorista.Variante("sku-oro", "Dorado", new BigDecimal("3.40"), true),
                new OfertaMayorista.Variante("sku-azul", "Azul", null, false)));
        assertTrue(CalculadoraMayorista.calcular(o, 600, UnidadVenta.PAR, "sku-rojo").aplicable());
        assertEquals(CalculadoraMayorista.Estado.PRECIO_AMBIGUO, CalculadoraMayorista.calcular(o, 600, UnidadVenta.PAR, "sku-oro").estado());
        assertEquals(CalculadoraMayorista.Estado.PRECIO_AMBIGUO, CalculadoraMayorista.calcular(o, 600, UnidadVenta.PAR, "sku-azul").estado());
        assertEquals(CalculadoraMayorista.Estado.PRECIO_AMBIGUO, CalculadoraMayorista.calcular(o, 600, UnidadVenta.PAR, "no-existe").estado());
    }

    @Test
    void detectaDiscrepanciasYContradicciones() {
        OfertaMayorista busqueda = oferta(20L, UnidadVenta.PAR, null, ESCALAS, List.of());
        assertFalse(CalculadoraMayorista.discrepancias(busqueda).isEmpty(), "El mínimo no coincide con la primera escala");
        assertTrue(CalculadoraMayorista.discrepancias(pares()).isEmpty());

        OfertaMayorista otroDetalle = oferta(100L, UnidadVenta.PAR, null,
                List.of(escala(100, 499L, "2.50"), escala(500, 999L, "1.80"), escala(1000, null, "1.50")), List.of());
        assertTrue(CalculadoraMayorista.seContradicen(pares(), otroDetalle));
        assertFalse(CalculadoraMayorista.seContradicen(pares(), pares()));
    }

    @Test
    void leeLasUnidadesEnInglesYEspanol() {
        assertEquals(UnidadVenta.PIEZA, UnidadVenta.leer("Pieces"));
        assertEquals(UnidadVenta.PAR, UnidadVenta.leer("pairs"));
        assertEquals(UnidadVenta.PAQUETE, UnidadVenta.leer("20 packs"));
        assertEquals(UnidadVenta.DESCONOCIDA, UnidadVenta.leer("lote"));
        assertEquals("1 par", UnidadVenta.PAR.con(1));
        assertEquals("20 pares", UnidadVenta.PAR.con(20));
    }
}
