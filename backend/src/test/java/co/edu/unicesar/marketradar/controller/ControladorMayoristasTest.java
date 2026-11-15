package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La cantidad de Emprender: "1.5" ya no se lee como 15. Mismas reglas que mayoristas.js. */
class ControladorMayoristasTest {

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource({"1,1", "500,500", "' 500 ',500", "10.000,10000", "1.000.000,1000000", "10000000,10000000",
            "10.000.000,10000000"})
    void aceptaEnterosYMilesConPunto(String texto, long esperado) {
        assertEquals(esperado, ControladorMayoristas.cantidad(texto));
    }

    @ParameterizedTest(name = "\"{0}\" se rechaza")
    @ValueSource(strings = {"1.5", "1,5", "1 5", "", "  ", "0", "-1", "10000001", "10.000.001", "1.50", "1.0000",
            "12.34.567", "+5", "5e3", "١٢"})
    void rechazaDecimalesSeparadoresMalPuestosYFueraDeRango(String texto) {
        assertThrows(ValidacionException.class, () -> ControladorMayoristas.cantidad(texto));
    }

    @Test
    void explicaElFormato() {
        ValidacionException e = assertThrows(ValidacionException.class, () -> ControladorMayoristas.cantidad("1.5"));
        assertTrue(e.getMessage().contains("sin decimales") && e.getMessage().contains("10.000"), e.getMessage());
        assertTrue(assertThrows(ValidacionException.class, () -> ControladorMayoristas.cantidad("0")).getMessage()
                .contains("entre 1 y 10.000.000"));
        assertTrue(assertThrows(ValidacionException.class, () -> ControladorMayoristas.cantidad("-1")).getMessage()
                .contains("entre 1 y 10.000.000"), "Un negativo no se explica como decimal");
    }

    @Test
    void leeLasVariantesElegidasEIgnoraLoQueNoTieneFormato() {
        assertEquals(Map.of("1600000000001", "sku-1", "1600000000002", "sku-9"),
                ControladorMayoristas.variantes("1600000000001:sku-1, 1600000000002:sku-9,malo,abc:x"));
        assertTrue(ControladorMayoristas.variantes(null).isEmpty());
    }
}
