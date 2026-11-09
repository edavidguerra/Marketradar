package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioBusquedaFlexibleTest {

    private final ServicioBusqueda busqueda = mock(ServicioBusqueda.class);
    private final ServicioSugerencias sugerencias = new ServicioSugerencias(ServicioSugerencias.leerVocabulario(),
            ServicioSugerencias.leerLista("/busqueda/palabras-comunes.txt"), List::of, Clock.systemUTC());
    private final ServicioBusquedaFlexible flexible = new ServicioBusquedaFlexible(busqueda, sugerencias);

    @Test
    void buscaDirectamenteLoQueLaPersonaQuisoDecir() {
        conResultados("licuadora", 3);

        ServicioBusquedaFlexible.Resultado resultado = flexible.buscar(consulta("licuadorra"), 7L, false);

        assertEquals("licuadora", resultado.consulta().getTermino());
        assertEquals("licuadorra", resultado.terminoOriginal());
        assertFalse(resultado.ampliada());
        verify(busqueda).buscar(argThat(c -> c.getTermino().equals("licuadora")), eq(7L));
    }

    @Test
    void entiendeFrasesCompletasSinAvisar() {
        conResultados("olla arrocera", 2);

        ServicioBusquedaFlexible.Resultado resultado =
                flexible.buscar(consulta("quiero comprar una olla arrocera por favor"), 7L, false);

        assertEquals("olla arrocera", resultado.consulta().getTermino());
        assertNull(resultado.terminoOriginal());
    }

    @Test
    void sinResultadosPruebaConLaPalabraPrincipal() {
        conResultados("licuadora oster roja 10 velocidades", 0);
        conResultados("licuadora", 4);

        ServicioBusquedaFlexible.Resultado resultado =
                flexible.buscar(consulta("licuadora oster roja 10 velocidades"), 7L, false);

        assertEquals("licuadora", resultado.consulta().getTermino());
        assertEquals("licuadora oster roja 10 velocidades", resultado.terminoOriginal());
        assertTrue(resultado.ampliada());
        verify(busqueda, never()).buscar(argThat(c -> c.getTermino().startsWith("licuadora oster")), any());
    }

    @Test
    void siPideBuscarExactoNoCorrige() {
        conResultados("licuadorra", 0);

        ServicioBusquedaFlexible.Resultado resultado = flexible.buscar(consulta("licuadorra"), 7L, true);

        assertEquals("licuadorra", resultado.consulta().getTermino());
        assertNull(resultado.terminoOriginal());
        verify(busqueda, never()).consultar(any());
    }

    @Test
    void loBienEscritoSeBuscaTalCual() {
        conResultados("tensiómetro digital", 5);

        ServicioBusquedaFlexible.Resultado resultado = flexible.buscar(consulta("tensiómetro digital"), null, false);

        assertEquals("tensiómetro digital", resultado.consulta().getTermino());
        assertNull(resultado.terminoOriginal());
    }

    private static ConsultaBusqueda consulta(String termino) {
        return ConsultaBusqueda.crear(termino, null, Set.of(), 1, 10);
    }

    private void conResultados(String termino, int total) {
        List<OfertaNormalizada> ofertas = java.util.stream.IntStream.range(0, total)
                .mapToObj(i -> OfertaNormalizada.builder().codigoMarketplace("googleshopping").idExterno("p" + i)
                        .titulo(termino + " " + i).precio(new BigDecimal("10000")).moneda("COP")
                        .urlDestino("https://ejemplo.com/" + i).build())
                .toList();
        ResultadoBusqueda resultado = new ResultadoBusqueda(ofertas, List.of(), total, Map.of(), null, null);
        when(busqueda.consultar(argThat(c -> c != null && c.getTermino().equals(termino)))).thenReturn(resultado);
        when(busqueda.buscar(argThat(c -> c != null && c.getTermino().equals(termino)), any())).thenReturn(resultado);
    }
}
