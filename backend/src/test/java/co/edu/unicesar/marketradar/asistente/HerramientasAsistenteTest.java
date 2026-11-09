package co.edu.unicesar.marketradar.asistente;

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
    void elEnlaceVaListoParaConvertirseEnBoton() {
        assertEquals("[Ver en Falabella](https://www.falabella.com.co/p/123)",
                HerramientasAsistente.enlace(oferta("Falabella", "https://www.falabella.com.co/p/123")));
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

        String resumen = new HerramientasAsistente(flexible, mock(ServicioTasasCambio.class))
                .buscarMejorOpcion("licuadorra");

        assertTrue(resumen.startsWith("SE BUSCÓ: \"licuadora\" (la persona escribió \"licuadorra\")"));
        assertTrue(resumen.contains("[Ver en Éxito](https://www.exito.com/licuadora)"));
    }

    private static OfertaNormalizada oferta(String vendedor, String url) {
        return OfertaNormalizada.builder().codigoMarketplace("googleshopping").idExterno("1").titulo("Licuadora")
                .precio(new BigDecimal("150000")).moneda("COP").vendedor(vendedor).urlDestino(url).build();
    }
}
