package co.edu.unicesar.marketradar.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfertaNormalizadaTest {

    @Test
    void conservaLasResenasAlConvertirElPrecio() {
        OfertaNormalizada original = base().calificacion(new BigDecimal("4.5")).totalResenas(120).build();

        OfertaNormalizada convertida = original.conPrecio(new BigDecimal("40000"), null, "COP");

        assertEquals(new BigDecimal("4.5"), convertida.getCalificacion());
        assertEquals(120, convertida.getTotalResenas());
        assertEquals("COP", convertida.getMoneda());
        assertTrue(convertida.tieneResenas());
    }

    @Test
    void sinResenasCuandoLaTiendaNoLasInforma() {
        assertFalse(base().build().tieneResenas());
        assertFalse(base().calificacion(new BigDecimal("4")).totalResenas(0).build().tieneResenas());
    }

    @Test
    void rechazaCalificacionFueraDeRango() {
        assertThrows(IllegalArgumentException.class,
                () -> base().calificacion(new BigDecimal("5.5")).totalResenas(3).build());
    }

    @Test
    void rechazaPrecioNegativo() {
        assertThrows(IllegalArgumentException.class, () -> base().precio(new BigDecimal("-1")).build());
    }

    @Test
    void exigeEnlaceALaTienda() {
        assertThrows(NullPointerException.class, () -> base().urlDestino(null).build());
    }

    @Test
    void unEnvioQueNoSeInformaNoValeCero() {
        OfertaNormalizada oferta = enPesos().costoEnvio(null).build();

        assertEquals(EstadoEnvio.DESCONOCIDO, oferta.getEstadoEnvio());
        assertEquals(new BigDecimal("100000"), oferta.costoConocido());
        assertTrue(oferta.totalConfirmado().isEmpty());
        assertEquals("no incluye el envío, que la tienda no informa", oferta.faltaParaElTotal().orElseThrow());
    }

    @Test
    void elTotalSeConfirmaSoloConElEnvioGratisOConValor() {
        assertEquals(new BigDecimal("100000"), enPesos().costoEnvio(BigDecimal.ZERO).build().totalConfirmado()
                .orElseThrow());
        assertEquals(EstadoEnvio.GRATIS, enPesos().costoEnvio(BigDecimal.ZERO).build().getEstadoEnvio());
        assertEquals(new BigDecimal("112900"), enPesos().costoEnvio(new BigDecimal("12900")).build()
                .totalConfirmado().orElseThrow());
    }

    @Test
    void unEnvioEstimadoSeSumaPeroNoConfirmaElTotal() {
        OfertaNormalizada oferta = enPesos().costoEnvio(new BigDecimal("9900")).estadoEnvio(EstadoEnvio.ESTIMADO)
                .build();

        assertEquals(new BigDecimal("109900"), oferta.costoConocido());
        assertTrue(oferta.totalConfirmado().isEmpty());
        assertTrue(oferta.faltaParaElTotal().orElseThrow().contains("aproximado"));
    }

    @Test
    void desdeElExteriorFaltanLosImpuestos() {
        OfertaNormalizada oferta = enPesos().vendedor("eBay - rossgames").costoEnvio(new BigDecimal("45000")).build();

        assertTrue(oferta.totalConfirmado().isEmpty());
        assertEquals("no incluye impuestos de importación, que no se conocen", oferta.faltaParaElTotal().orElseThrow());
    }

    @Test
    void unPrecioConAlertaNoSeUsaParaRecomendar() {
        OfertaNormalizada oferta = enPesos().costoEnvio(BigDecimal.ZERO).build().conAlertaPrecio("Revíselo");

        assertFalse(oferta.precioVerificado());
        assertTrue(oferta.totalConfirmado().isEmpty());
    }

    @Test
    void laCondicionYElEnlaceSeLeenDeLaOferta() {
        OfertaNormalizada oferta = enPesos().condicion("reacondicionado")
                .urlDestino("https://www.google.com.co/search?q=x").build();

        assertEquals(CondicionProducto.REACONDICIONADO, oferta.condicionProducto().orElseThrow());
        assertEquals("Buscar esta oferta en Google", oferta.enlace().textoBoton());
    }

    private static OfertaNormalizada.Builder enPesos() {
        return base().precio(new BigDecimal("100000")).moneda("COP").vendedor("Éxito");
    }

    private static OfertaNormalizada.Builder base() {
        return OfertaNormalizada.builder()
                .codigoMarketplace("simulado").idExterno("1").titulo("Tensiómetro")
                .precio(new BigDecimal("10")).moneda("USD").urlDestino("https://ejemplo.com/1");
    }
}
