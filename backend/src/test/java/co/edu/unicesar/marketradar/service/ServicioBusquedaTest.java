package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplace;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioBusquedaTest {

    private ExecutorService ejecutor;
    private ServicioPersistenciaOfertas persistencia;

    @BeforeEach
    void preparar() {
        ejecutor = Executors.newFixedThreadPool(4);
        persistencia = mock(ServicioPersistenciaOfertas.class);
        when(persistencia.guardarOfertas(any())).thenReturn(Map.of());
    }

    @AfterEach
    void cerrar() {
        ejecutor.shutdownNow();
    }

    @Test
    void ordenaPorPrecioTotalYMarcaElMejorPrecio() {
        AdaptadorMarketplace tiendaA = adaptador("a", List.of(oferta("a", "1", "30000", "5000"),
                oferta("a", "2", "20000", null)));
        AdaptadorMarketplace tiendaB = adaptador("b", List.of(oferta("b", "3", "25000", "0")));

        ResultadoBusqueda resultado = servicio(tiendaA, tiendaB).buscar(consulta(null));

        List<String> ids = resultado.getOfertas().stream().map(OfertaNormalizada::getIdExterno).toList();
        assertEquals(List.of("2", "3", "1"), ids);
        assertTrue(resultado.esMejorPrecio(resultado.getOfertas().get(0)));
        assertTrue(resultado.getAdvertencias().isEmpty());
    }

    @Test
    void siUnaTiendaFallaDevuelveLasDemasConAdvertencia() {
        AdaptadorMarketplace sana = adaptador("sana", List.of(oferta("sana", "1", "10000", null)));
        AdaptadorMarketplace rota = mock(AdaptadorMarketplace.class);
        when(rota.codigo()).thenReturn("rota");
        when(rota.estaHabilitado()).thenReturn(true);
        when(rota.buscar(any())).thenThrow(new MarketplaceException("rota", "caída"));

        ResultadoBusqueda resultado = servicio(sana, rota).buscar(consulta(null));

        assertEquals(1, resultado.getTotalResultados());
        assertEquals(1, resultado.getAdvertencias().size());
        assertTrue(resultado.getAdvertencias().get(0).contains("rota"));
    }

    @Test
    void reutilizaLaCacheEnBusquedasRepetidas() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(oferta("a", "1", "10000", null)));
        ServicioBusqueda servicio = servicio(tienda);

        servicio.buscar(consulta("precio_asc"));
        servicio.buscar(consulta("precio_desc"));

        verify(tienda, times(1)).buscar(any());
    }

    @Test
    void sinTiendasHabilitadasAvisaAlUsuario() {
        ResultadoBusqueda resultado = servicio().buscar(consulta(null));

        assertEquals(0, resultado.getTotalResultados());
        assertEquals(1, resultado.getAdvertencias().size());
    }

    private ServicioBusqueda servicio(AdaptadorMarketplace... adaptadores) {
        return new ServicioBusqueda(List.of(adaptadores), ejecutor, CircuitBreakerRegistry.ofDefaults(),
                persistencia, PropiedadesPrueba.crear());
    }

    private static ConsultaBusqueda consulta(String orden) {
        return ConsultaBusqueda.crear("licuadora", orden, Set.of(), 1, 10);
    }

    private static AdaptadorMarketplace adaptador(String codigo, List<OfertaNormalizada> ofertas) {
        AdaptadorMarketplace adaptador = mock(AdaptadorMarketplace.class);
        when(adaptador.codigo()).thenReturn(codigo);
        when(adaptador.estaHabilitado()).thenReturn(true);
        when(adaptador.buscar(any())).thenReturn(ofertas);
        return adaptador;
    }

    private static OfertaNormalizada oferta(String tienda, String id, String precio, String envio) {
        return OfertaNormalizada.builder()
                .codigoMarketplace(tienda).idExterno(id).titulo("Licuadora " + id)
                .precio(new BigDecimal(precio)).moneda("COP")
                .costoEnvio(envio == null ? null : new BigDecimal(envio))
                .condicion("nuevo").vendedor("Vendedor").urlDestino("https://ejemplo.com/" + id)
                .build();
    }
}
