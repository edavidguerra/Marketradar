package co.edu.unicesar.marketradar.asistente;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.service.ServicioBusquedaFlexible;
import co.edu.unicesar.marketradar.service.ServicioTasasCambio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HerramientasAsistenteTest {

    @Test
    void elEnlaceVaListoParaConvertirseEnBotonYDiceADondeLleva() {
        assertEquals("[Ver la oferta en falabella.com.co](https://www.falabella.com.co/p/123)",
                HerramientasAsistente.enlace(oferta("Falabella", "https://www.falabella.com.co/p/123")));
    }

    @Test
    void unaBusquedaDeGoogleNoSePresentaComoLaTienda() {
        String enlace = HerramientasAsistente.enlace(oferta("Falabella",
                "https://www.google.com.co/search?tbm=shop&gl=co&hl=es&q=Licuadora+Falabella"));

        assertTrue(enlace.startsWith("[Buscar esta oferta en Google]("));
        assertFalse(enlace.contains("Ver en Falabella"));
    }

    @Test
    void explicaElPuntajeConLosPesosConfigurados() {
        String explicacion = new HerramientasAsistente(mock(ServicioBusquedaFlexible.class),
                mock(ServicioTasasCambio.class), PropiedadesPrueba.crear()).explicarPuntaje();

        assertTrue(explicacion.contains("Precio (60 %)"));
        assertTrue(explicacion.contains("Opiniones (30 %)"));
        assertTrue(explicacion.contains("Envío (10 %)"));
        assertTrue(explicacion.contains("No es una probabilidad"));
    }

    @Test
    void sinRecomendacionLoDiceYDaLasAlternativasConLoQueLesFalta() {
        ServicioBusquedaFlexible flexible = mock(ServicioBusquedaFlexible.class);
        OfertaNormalizada reacondicionado = oferta("Tienda", "https://tienda.com/iphone");
        ResultadoBusqueda resultado = new ResultadoBusqueda(List.of(), List.of(), 0, Map.of(), null, null, null, 0,
                new ResultadoBusqueda.Detalle(List.of(new ResultadoBusqueda.Alternativa(reacondicionado,
                        co.edu.unicesar.marketradar.domain.RequisitosBusqueda.Nivel.INCUMPLE,
                        List.of("Es reacondicionado, no nuevo"))), List.of("modelo 13", "nuevo"),
                        "Ninguna oferta confirma todo lo que usted pidió", 0, true));
        when(flexible.buscar(any(), eq(null), eq(false))).thenReturn(new ServicioBusquedaFlexible.Resultado(
                ConsultaBusqueda.crear("iphone 13 nuevo", null, Set.of(), 1, 3), resultado, null, false));

        String resumen = new HerramientasAsistente(flexible, mock(ServicioTasasCambio.class),
                PropiedadesPrueba.crear()).buscarMejorOpcion("iphone 13 nuevo");

        assertTrue(resumen.contains("LO QUE PIDIÓ LA PERSONA"));
        assertTrue(resumen.contains("SIN RECOMENDACIÓN"));
        assertTrue(resumen.contains("Le falta: Es reacondicionado, no nuevo"));
        assertFalse(resumen.contains("MEJOR OPCIÓN"));
    }

    @Test
    void noOfreceEnlacesInseguros() {
        String enlace = HerramientasAsistente.enlace(oferta("Tienda", "http://tienda.com/p/1"));

        assertFalse(enlace.contains("http"));
        assertTrue(enlace.contains("sin enlace seguro"));
    }

    @Test
    void cuentaQueSeBuscoLaPalabraCorregida() {
        ServicioBusquedaFlexible flexible = mock(ServicioBusquedaFlexible.class);
        OfertaNormalizada licuadora = oferta("Éxito", "https://www.exito.com/licuadora");
        ResultadoBusqueda resultado = new ResultadoBusqueda(List.of(licuadora), List.of(), 1, Map.of(), null, null);
        when(flexible.buscar(any(), eq(null), eq(false))).thenReturn(new ServicioBusquedaFlexible.Resultado(
                ConsultaBusqueda.crear("licuadora", null, Set.of(), 1, 3), resultado, "licuadorra", false));

        String resumen = new HerramientasAsistente(flexible, mock(ServicioTasasCambio.class),
                PropiedadesPrueba.crear()).buscarMejorOpcion("licuadorra");

        assertTrue(resumen.startsWith("SE BUSCÓ: \"licuadora\" (la persona escribió \"licuadorra\")"));
        assertTrue(resumen.contains("[Ver la oferta en exito.com](https://www.exito.com/licuadora)"));
        assertTrue(resumen.contains("MÁS ENVÍO"), "Sin envío informado no se presenta como total");
    }

    private static OfertaNormalizada oferta(String vendedor, String url) {
        return OfertaNormalizada.builder().codigoMarketplace("googleshopping").idExterno("1").titulo("Licuadora")
                .precio(new BigDecimal("150000")).moneda("COP").vendedor(vendedor).urlDestino(url).build();
    }
}
