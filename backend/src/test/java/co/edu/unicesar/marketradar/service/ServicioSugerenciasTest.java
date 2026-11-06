package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.BusquedaDao.TerminoPopular;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioSugerenciasTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC);

    private static ServicioSugerencias conVocabulario(List<TerminoPopular> populares) {
        return new ServicioSugerencias(ServicioSugerencias.leerVocabulario(), () -> populares, RELOJ);
    }

    private final ServicioSugerencias servicio = conVocabulario(List.of());

    @Test
    void sugiereProductosQueEmpiezanIgual() {
        List<String> sugerencias = servicio.sugerir("compu");

        assertEquals("computador", sugerencias.get(0));
        assertTrue(sugerencias.contains("computador portátil"));
        assertTrue(sugerencias.size() <= ServicioSugerencias.MAXIMO_SUGERENCIAS);
    }

    @Test
    void ignoraTildesYMayusculasYDevuelveLaFormaConTilde() {
        assertEquals("tensiómetro", servicio.sugerir("TENSIO").get(0));
    }

    @Test
    void sugiereAunqueHayaUnErrorAlEscribir() {
        assertTrue(servicio.sugerir("lisuadora").contains("licuadora"));
        assertTrue(servicio.sugerir("televisr").stream().anyMatch(s -> s.startsWith("televisor")));
    }

    @Test
    void tambienBuscaPorLaSegundaPalabra() {
        assertTrue(servicio.sugerir("arrocera").contains("olla arrocera"));
    }

    @Test
    void conMenosDeDosLetrasNoSugiere() {
        assertTrue(servicio.sugerir("a").isEmpty());
        assertTrue(servicio.sugerir(null).isEmpty());
    }

    @Test
    void losMasBuscadosVanPrimero() {
        ServicioSugerencias conPopulares = conVocabulario(List.of(new TerminoPopular("celular redmi note 13", 40)));

        assertEquals("celular redmi note 13", conPopulares.sugerir("celu").get(0));
    }

    @Test
    void corrigeLasPalabrasMalEscritas() {
        assertEquals(Optional.of("licuadora"), servicio.corregir("licuadorra"));
        assertEquals(Optional.of("olla arrocera"), servicio.corregir("oya arrosera"));
        assertEquals(Optional.of("computador portátil"), servicio.corregir("conputador portatil"));
        assertEquals(Optional.of("tensiómetro"), servicio.corregir("tensiometro digitl").map(s -> s.split(" ")[0]));
    }

    @Test
    void noCorrigeLoQueYaEstaBienEscrito() {
        assertTrue(servicio.corregir("olla arrocera").isEmpty());
        assertTrue(servicio.corregir("Tensiómetro").isEmpty());
        assertTrue(servicio.corregir("televisor 55").isEmpty());
    }

    @Test
    void unTerminoPopularMalEscritoNoSeVuelveCorreccion() {
        ServicioSugerencias conPopulares = conVocabulario(List.of(new TerminoPopular("licuadorra", 2)));

        assertEquals(Optional.of("licuadora"), conPopulares.corregir("licuadorra"));
    }

    @Test
    void siLaBaseDeDatosFallaSigueConElVocabulario() {
        AtomicInteger llamadas = new AtomicInteger();
        ServicioSugerencias sinBase = new ServicioSugerencias(List.of("licuadora"), () -> {
            llamadas.incrementAndGet();
            throw new AccesoDatosException("sin conexión", null);
        }, RELOJ);

        assertEquals(List.of("licuadora"), sinBase.sugerir("licu"));
        sinBase.sugerir("licu");
        assertEquals(1, llamadas.get());
    }

    @Test
    void calculaLaDistanciaEntrePalabras() {
        assertEquals(0, ServicioSugerencias.distancia("olla", "olla", 2));
        assertEquals(1, ServicioSugerencias.distancia("oya", "olla", 2) - 1);
        assertEquals(3, ServicioSugerencias.distancia("abcdef", "uvwxyz", 2));
    }
}
