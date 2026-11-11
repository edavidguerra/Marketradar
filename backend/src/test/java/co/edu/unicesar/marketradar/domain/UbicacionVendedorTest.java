package co.edu.unicesar.marketradar.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UbicacionVendedorTest {

    private static final String BUSQUEDA = "https://www.google.com.co/search?tbm=shop&q=x";

    @Test
    void reconoceLasTiendasDeColombia() {
        for (String tienda : new String[] {"Mercadolibre Colombia", "Éxito", "exito.com", "Alkosto", "Homecenter",
                "Yaxa Colombia", "Falabella Colombia", "Tienda Online Puntos Colombia", "Pepeganga.com"}) {
            UbicacionVendedor ubicacion = UbicacionVendedor.de(tienda, BUSQUEDA);
            assertEquals(UbicacionVendedor.Alcance.COLOMBIA, ubicacion.alcance(), tienda);
            assertEquals("Colombia", ubicacion.texto());
            assertFalse(ubicacion.desdeExterior());
        }
        assertEquals("Colombia", UbicacionVendedor.de("Tienda Rosita", "https://www.tiendarosita.com.co/p/1").texto());
    }

    @Test
    void reconoceLasTiendasDelExterior() {
        assertEquals("el exterior", UbicacionVendedor.de("eBay - rossgames", BUSQUEDA).texto());
        assertEquals("Estados Unidos", UbicacionVendedor.de("Amazon.com", BUSQUEDA).texto());
        assertEquals("China", UbicacionVendedor.de("AliExpress", BUSQUEDA).texto());
        assertEquals("Italia", UbicacionVendedor.de("ConsoleModding.it", BUSQUEDA).texto());
        assertEquals("Emiratos Árabes Unidos", UbicacionVendedor.de("noon.com/uae-en/", BUSQUEDA).texto());
        assertEquals("México", UbicacionVendedor.de("Mercado Libre México", BUSQUEDA).texto());
        assertTrue(UbicacionVendedor.de("Tienda X", "https://www.ebay.com/itm/1").desdeExterior());
    }

    @Test
    void siNoSeSabeLoDice() {
        UbicacionVendedor ubicacion = UbicacionVendedor.de("Marketphone", BUSQUEDA);
        assertEquals(UbicacionVendedor.Alcance.DESCONOCIDA, ubicacion.alcance());
        assertNull(ubicacion.texto());
        assertNull(UbicacionVendedor.de(null, null).texto());
        // "target" no se confunde con otra palabra que la contiene.
        assertNull(UbicacionVendedor.de("Targetas Diaz", "nota rara").texto());
    }
}
