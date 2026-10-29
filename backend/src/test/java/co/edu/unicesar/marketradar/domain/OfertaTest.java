package co.edu.unicesar.marketradar.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfertaTest {

    @Test
    void precioTotalSumaElEnvio() {
        Oferta oferta = new Oferta();
        oferta.setPrecioActual(new BigDecimal("100000.00"));
        oferta.setCostoEnvio(new BigDecimal("12000.00"));

        assertEquals(new BigDecimal("112000.00"), oferta.calcularPrecioTotal());
    }

    @Test
    void precioTotalSinEnvioEsElPrecio() {
        Oferta oferta = new Oferta();
        oferta.setPrecioActual(new BigDecimal("50000.00"));

        assertEquals(new BigDecimal("50000.00"), oferta.calcularPrecioTotal());
    }

    @Test
    void detectaCambioDePrecioIgnorandoEscala() {
        Oferta oferta = new Oferta();
        oferta.setPrecioActual(new BigDecimal("50000.00"));

        assertFalse(oferta.cambioPrecio(new BigDecimal("50000")));
        assertTrue(oferta.cambioPrecio(new BigDecimal("49000")));
    }

    @Test
    void rechazaPrecioNegativo() {
        Oferta oferta = new Oferta();
        assertThrows(IllegalArgumentException.class, () -> oferta.setPrecioActual(new BigDecimal("-1")));
    }
}
