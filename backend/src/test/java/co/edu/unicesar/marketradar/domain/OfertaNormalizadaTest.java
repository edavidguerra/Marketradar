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

    private static OfertaNormalizada.Builder base() {
        return OfertaNormalizada.builder()
                .codigoMarketplace("simulado").idExterno("1").titulo("Tensiómetro")
                .precio(new BigDecimal("10")).moneda("USD").urlDestino("https://ejemplo.com/1");
    }
}
