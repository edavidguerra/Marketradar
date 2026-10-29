package co.edu.unicesar.marketradar.domain;

import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsultaBusquedaTest {

    @Test
    void creaConsultaValidaConOrdenPorDefecto() {
        ConsultaBusqueda consulta = ConsultaBusqueda.crear("  licuadora ", null, Set.of(), 1, 10);

        assertEquals("licuadora", consulta.getTermino());
        assertEquals(OrdenResultados.PRECIO_ASC, consulta.getOrden());
    }

    @Test
    void rechazaTerminoMuyCorto() {
        assertThrows(ValidacionException.class, () -> ConsultaBusqueda.crear("a", null, Set.of(), 1, 10));
    }

    @Test
    void rechazaTerminoMuyLargo() {
        String largo = "x".repeat(101);
        assertThrows(ValidacionException.class, () -> ConsultaBusqueda.crear(largo, null, Set.of(), 1, 10));
    }

    @Test
    void rechazaCaracteresDeControl() {
        assertThrows(ValidacionException.class, () -> ConsultaBusqueda.crear("tv\u0000led", null, Set.of(), 1, 10));
    }

    @Test
    void rechazaTamanoMayorA20() {
        assertThrows(ValidacionException.class, () -> ConsultaBusqueda.crear("televisor", null, Set.of(), 1, 21));
    }

    @Test
    void rechazaOrdenDesconocido() {
        assertThrows(ValidacionException.class, () -> ConsultaBusqueda.crear("televisor", "barato", Set.of(), 1, 10));
    }
}
