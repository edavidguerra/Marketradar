package co.edu.unicesar.marketradar.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluadorPrecioTest {

    private static final BigDecimal UNO = BigDecimal.ONE;
    private static final String VARIANTE = "modelo=iphone 13;almacenamiento=128";

    private static Seguimiento seguimiento(String precio) {
        Seguimiento s = new Seguimiento();
        s.setUsuarioId(1);
        s.setOfertaId(10);
        s.setTipoAviso(Seguimiento.TipoAviso.BAJA_PRECIO);
        s.setEstado(Seguimiento.Estado.ACTIVO);
        s.setPrecioReferencia(precio == null ? null : new BigDecimal(precio));
        s.setMonedaReferencia("COP");
        s.setVendedorReferencia("Éxito");
        s.setCondicionReferencia("nuevo");
        s.setVarianteReferencia(VARIANTE);
        s.setReferenciaDesde(OffsetDateTime.parse("2026-11-01T10:00:00Z"));
        return s;
    }

    private static OfertaNormalizada.Builder oferta(String precio) {
        return OfertaNormalizada.builder().codigoMarketplace("googleshopping").idExterno("g-1")
                .titulo("iPhone 13 128 GB").precio(new BigDecimal(precio)).moneda("COP").vendedor("éxito ")
                .condicion("nuevo").urlDestino("https://www.exito.com/iphone").consultadoEn(OffsetDateTime.now());
    }

    private static EvaluadorPrecio.Decision evaluar(Seguimiento s, OfertaNormalizada o) {
        return EvaluadorPrecio.evaluar(s, Optional.ofNullable(o), VARIANTE, UNO);
    }

    @Test
    void unaBajadaRealDelMismoVendedorAvisa() {
        EvaluadorPrecio.Decision d = evaluar(seguimiento("3000000"), oferta("2700000").build());
        assertEquals(Seguimiento.Resultado.BAJO, d.resultado());
        assertTrue(d.actualizarReferencia());
        assertEquals(new BigDecimal("3000000"), d.evento().orElseThrow().precioAnterior());
        assertEquals(new BigDecimal("2700000"), d.evento().orElseThrow().precioActual());
    }

    @Test
    void unaBajadaMenorAlMinimoNoAvisa() {
        EvaluadorPrecio.Decision d = evaluar(seguimiento("3000000"), oferta("2990000").build());
        assertEquals(Seguimiento.Resultado.SIN_CAMBIO, d.resultado());
        assertTrue(d.evento().isEmpty());
    }

    @Test
    void siSubeNoAvisaPeroActualizaLaReferencia() {
        EvaluadorPrecio.Decision d = evaluar(seguimiento("3000000"), oferta("3200000").build());
        assertEquals(Seguimiento.Resultado.SUBIO, d.resultado());
        assertTrue(d.actualizarReferencia());
        assertTrue(d.evento().isEmpty());
    }

    @Test
    void siLaOfertaNoAparecioNoEsDescuento() {
        EvaluadorPrecio.Decision d = evaluar(seguimiento("3000000"), null);
        assertEquals(Seguimiento.Resultado.NO_ENCONTRADA, d.resultado());
        assertFalse(d.actualizarReferencia());
        assertTrue(d.evento().isEmpty());
    }

    @Test
    void unPrecioConAlertaNoSeUsa() {
        EvaluadorPrecio.Decision d = evaluar(seguimiento("3000000"),
                oferta("150000").alertaPrecio("Es el valor de una cuota").build());
        assertEquals(Seguimiento.Resultado.PRECIO_DUDOSO, d.resultado());
        assertFalse(d.actualizarReferencia());
        assertTrue(d.evento().isEmpty());
    }

    @Test
    void otroVendedorNoEsLaMismaOferta() {
        EvaluadorPrecio.Decision d = evaluar(seguimiento("3000000"), oferta("2000000").vendedor("Otra tienda").build());
        assertEquals(Seguimiento.Resultado.VARIANTE_DISTINTA, d.resultado());
        assertFalse(d.actualizarReferencia());
        assertTrue(d.evento().isEmpty());
    }

    @Test
    void otraCondicionNoEsLaMismaOferta() {
        EvaluadorPrecio.Decision d = evaluar(seguimiento("3000000"), oferta("2000000").condicion("reacondicionado").build());
        assertEquals(Seguimiento.Resultado.VARIANTE_DISTINTA, d.resultado());
        assertTrue(d.evento().isEmpty());
    }

    @Test
    void otraVarianteNoEsDescuento() {
        EvaluadorPrecio.Decision d = EvaluadorPrecio.evaluar(seguimiento("3000000"),
                Optional.of(oferta("2000000").build()), "modelo=iphone 13;almacenamiento=64", UNO);
        assertEquals(Seguimiento.Resultado.VARIANTE_DISTINTA, d.resultado());
        assertTrue(d.evento().isEmpty());
    }

    @Test
    void sinReferenciaLaPrimeraComprobacionSoloLaGuarda() {
        EvaluadorPrecio.Decision d = evaluar(seguimiento(null), oferta("2000000").build());
        assertEquals(Seguimiento.Resultado.REFERENCIA, d.resultado());
        assertTrue(d.actualizarReferencia());
        assertTrue(d.evento().isEmpty());
    }

    @Test
    void unCambioDeLaTrmNoEsDescuento() {
        Seguimiento s = seguimiento("400000");
        s.setPrecioOriginalReferencia(new BigDecimal("100.00"));
        s.setMonedaOriginalReferencia("USD");
        // Mismo precio en dólares; en pesos baja un 10 % porque bajó el dólar.
        OfertaNormalizada o = oferta("360000").precioOriginal(new BigDecimal("100.00")).monedaOriginal("USD").build();
        EvaluadorPrecio.Decision d = evaluar(s, o);
        assertEquals(Seguimiento.Resultado.SIN_CAMBIO, d.resultado());
        assertTrue(d.evento().isEmpty());
    }

    @Test
    void unaBajadaEnDolaresSiAvisa() {
        Seguimiento s = seguimiento("400000");
        s.setPrecioOriginalReferencia(new BigDecimal("100.00"));
        s.setMonedaOriginalReferencia("USD");
        OfertaNormalizada o = oferta("360000").precioOriginal(new BigDecimal("90.00")).monedaOriginal("USD").build();
        assertEquals(Seguimiento.Resultado.BAJO, evaluar(s, o).resultado());
    }

    @Test
    void elPrecioObjetivoSeAvisaUnaSolaVezHastaQueVuelvaASubir() {
        Seguimiento s = seguimiento("3000000");
        s.setTipoAviso(Seguimiento.TipoAviso.PRECIO_OBJETIVO);
        s.setPrecioObjetivo(new BigDecimal("2500000"));

        EvaluadorPrecio.Decision primera = evaluar(s, oferta("2400000").build());
        assertEquals(Seguimiento.Resultado.OBJETIVO_ALCANZADO, primera.resultado());
        assertTrue(primera.objetivoAvisado());
        assertEquals(Seguimiento.TipoAviso.PRECIO_OBJETIVO, primera.evento().orElseThrow().tipo());

        s.setObjetivoAvisado(true);
        s.setPrecioReferencia(new BigDecimal("2400000"));
        EvaluadorPrecio.Decision segunda = evaluar(s, oferta("2300000").build());
        assertTrue(segunda.evento().isEmpty(), "No se repite el aviso mientras siga por debajo del objetivo");
        assertTrue(segunda.objetivoAvisado());

        EvaluadorPrecio.Decision subio = evaluar(s, oferta("2700000").build());
        assertFalse(subio.objetivoAvisado(), "Al volver a subir se puede avisar de nuevo");
        assertTrue(subio.evento().isEmpty());
    }

    @Test
    void conPrecioObjetivoUnaBajadaQueNoLlegaNoAvisa() {
        Seguimiento s = seguimiento("3000000");
        s.setTipoAviso(Seguimiento.TipoAviso.PRECIO_OBJETIVO);
        s.setPrecioObjetivo(new BigDecimal("2000000"));
        assertTrue(evaluar(s, oferta("2700000").build()).evento().isEmpty());
    }
}
