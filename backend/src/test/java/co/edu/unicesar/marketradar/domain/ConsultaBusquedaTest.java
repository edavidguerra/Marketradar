package co.edu.unicesar.marketradar.domain;

import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void leeLosPreciosComoLosEscribeLaPersona() {
        assertEquals(new BigDecimal("1500000"), ConsultaBusqueda.leerPrecio("1.500.000"));
        assertEquals(new BigDecimal("1500000"), ConsultaBusqueda.leerPrecio("$ 1.500.000"));
        assertEquals(new BigDecimal("1500000"), ConsultaBusqueda.leerPrecio("1,500,000"));
        assertEquals(new BigDecimal("59900"), ConsultaBusqueda.leerPrecio(" 59900,50 "));
        assertNull(ConsultaBusqueda.leerPrecio("  "));
        assertThrows(ValidacionException.class, () -> ConsultaBusqueda.leerPrecio("un millón"));
        assertThrows(ValidacionException.class, () -> ConsultaBusqueda.leerPrecio("-5000"));
    }

    @Test
    void elRangoDePrecioSeValidaYNoCambiaLaCache() {
        ConsultaBusqueda base = ConsultaBusqueda.crear("licuadora", null, Set.of(), 1, 10);
        ConsultaBusqueda conRango = base.conRangoDePrecio(new BigDecimal("100000"), new BigDecimal("300000"));

        assertTrue(conRango.tieneRangoDePrecio());
        assertTrue(conRango.precioEnRango(new BigDecimal("100000")));
        assertTrue(conRango.precioEnRango(new BigDecimal("300000")));
        assertFalse(conRango.precioEnRango(new BigDecimal("300001")));
        assertEquals(base.claveCache(), conRango.claveCache());
        assertEquals(new BigDecimal("300000"), conRango.conTermino("licuadora oster").getPrecioMaximo());
        assertFalse(conRango.sinRangoDePrecio().tieneRangoDePrecio());
        assertTrue(base.conRangoDePrecio(null, new BigDecimal("5000")).precioEnRango(BigDecimal.ZERO));
        assertThrows(ValidacionException.class,
                () -> base.conRangoDePrecio(new BigDecimal("300000"), new BigDecimal("100000")));
        assertThrows(ValidacionException.class, () -> base.conRangoDePrecio(new BigDecimal("-1"), null));
        assertThrows(ValidacionException.class,
                () -> base.conRangoDePrecio(null, ConsultaBusqueda.PRECIO_MAXIMO_PERMITIDO.add(BigDecimal.ONE)));
    }

    @Test
    void conservaLoQueEscribioLaPersonaAlCorregirPaginarYFiltrar() {
        ConsultaBusqueda consulta = ConsultaBusqueda.crear("iphone 13", null, Set.of(), 1, 10)
                .conTextoOriginal("iphone 13 de 128 gb nuevo");

        ConsultaBusqueda filtrada = consulta.conRangoDePrecio(null, new BigDecimal("3000000")).conTermino("iphone 13");

        assertEquals("iphone 13 de 128 gb nuevo", filtrada.getTextoOriginal());
        assertEquals(List.of("modelo iphone 13", "128 GB", "nuevo"), filtrada.requisitos().etiquetas());
        assertEquals(consulta.claveCache(), ConsultaBusqueda.crear("iphone 13", null, Set.of(), 1, 10).claveCache(),
                "el texto original no cambia lo que se busca en las tiendas");
    }

    @Test
    void ignoraUnTextoOriginalVacioOConCaracteresRaros() {
        ConsultaBusqueda consulta = ConsultaBusqueda.crear("licuadora", null, Set.of(), 1, 10);

        assertEquals("licuadora", consulta.conTextoOriginal(null).getTextoOriginal());
        assertEquals("licuadora", consulta.conTextoOriginal(" ").getTextoOriginal());
        assertEquals("licuadora", consulta.conTextoOriginal("licua\u0000dora").getTextoOriginal());
        assertEquals("licuadora", consulta.conTextoOriginal("x".repeat(101)).getTextoOriginal());
    }
}
