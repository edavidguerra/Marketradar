package co.edu.unicesar.marketradar.service.mayorista;

import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.adapter.alibaba.ApiShException;
import co.edu.unicesar.marketradar.adapter.alibaba.ClienteAlibabaApiSh;
import co.edu.unicesar.marketradar.adapter.alibaba.LectorRespuestasAlibaba;
import co.edu.unicesar.marketradar.config.PropiedadesMayoristas;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import co.edu.unicesar.marketradar.domain.mayorista.EscalaPrecio;
import co.edu.unicesar.marketradar.domain.mayorista.OfertaMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.UnidadVenta;
import co.edu.unicesar.marketradar.exception.ServicioNoDisponibleException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioMayoristasTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ClienteAlibabaApiSh cliente;
    private ConversorMoneda conversor;

    @BeforeEach
    void preparar() {
        cliente = mock(ClienteAlibabaApiSh.class);
        when(cliente.estaConfigurado()).thenReturn(true);
        conversor = mock(ConversorMoneda.class);
        when(conversor.getMonedaDestino()).thenReturn("COP");
        when(conversor.tasaPara("USD")).thenReturn(Optional.of(
                new TasaCambio("USD", new BigDecimal("4000"), LocalDate.of(2026, 11, 14), "TRM")));
    }

    private ServicioMayoristas servicio(int consultasPorDia, int detalles) {
        return new ServicioMayoristas(cliente,
                new PropiedadesMayoristas("clave", null, 6, 3, detalles, consultasPorDia, 60), conversor,
                Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneOffset.UTC));
    }

    private static OfertaMayorista oferta(String id, String titulo, String proveedor, boolean patrocinado, Long minimo,
                                          List<EscalaPrecio> escalas) {
        BigDecimal menor = escalas.stream().map(EscalaPrecio::precio).min(BigDecimal::compareTo).orElse(null);
        BigDecimal mayor = escalas.stream().map(EscalaPrecio::precio).max(BigDecimal::compareTo).orElse(null);
        return new OfertaMayorista(id, titulo, "https://www.alibaba.com/product-detail/x_" + id + ".html", null,
                proveedor, "CN", 3, false, patrocinado, minimo, UnidadVenta.PAR, UnidadVenta.PAR, null, escalas,
                menor, mayor, "USD", null, List.of(), null, "busqueda");
    }

    private static EscalaPrecio e(long min, Long max, String precio) {
        return new EscalaPrecio(min, max, new BigDecimal(precio));
    }

    private void responder(List<OfertaMayorista> ofertas) {
        when(cliente.buscar(anyString(), anyInt()))
                .thenReturn(new LectorRespuestasAlibaba.Pagina(ofertas, 2, 0, null, false));
    }

    @Test
    void ordenaPorSubtotalYMarcaAnunciosYRepetidos() {
        responder(List.of(
                oferta("1", "Cotton socks A", "Prov A", false, 100L, List.of(e(100, 999L, "0.90"), e(1000, null, "0.70"))),
                oferta("2", "Cotton socks B", "Prov B", true, 100L, List.of(e(100, null, "0.60"))),
                oferta("2", "Cotton socks B", "Prov B", true, 100L, List.of(e(100, null, "0.60"))),
                oferta("3", "Cotton socks B", "Prov B", false, 100L, List.of(e(100, null, "0.60")))));
        ServicioMayoristas.Busqueda b = servicio(200, 4).buscar("cotton socks", 500, UnidadVenta.PAR, 1);

        assertEquals(2, b.resultados().size(), "Se quitan el id repetido y el mismo título del mismo proveedor");
        assertEquals("2", b.resultados().get(0).oferta().id());
        assertEquals(1, b.resultados().get(0).posicion());
        assertEquals(new BigDecimal("300.00"), b.resultados().get(0).calculo().subtotal());
        assertEquals(new BigDecimal("1200000"), b.resultados().get(0).aproximadoPesos());
        assertTrue(b.resultados().get(0).oferta().patrocinado());
        assertTrue(b.avisos().stream().anyMatch(a -> a.contains("anuncio")));
        assertEquals(2, b.descartados());
    }

    @Test
    void quitaLosResultadosQueNoTienenQueVer() {
        List<OfertaMayorista> ofertas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ofertas.add(oferta("s" + i, "Cotton socks " + i, "P" + i, false, 100L, List.of(e(100, null, "1.00"))));
        }
        ofertas.add(oferta("x", "Bluetooth speaker", "PX", false, 100L, List.of(e(100, null, "1.00"))));
        responder(ofertas);
        ServicioMayoristas.Busqueda b = servicio(200, 4).buscar("socks", 100, UnidadVenta.PAR, 1);
        assertEquals(4, b.resultados().size());
        assertTrue(b.resultados().stream().noneMatch(r -> r.oferta().id().equals("x")));
    }

    @Test
    void siLaBusquedaYElDetalleSeContradicenQuedaFueraDelOrden() throws Exception {
        // El pedido mínimo (20) no coincide con la primera escala (100): se consulta el detalle.
        OfertaMayorista dudosa = oferta("1600000000001", "Cotton socks men", "Zhejiang", false, 20L,
                List.of(e(100, 999L, "0.80"), e(1000, null, "0.50")));
        responder(List.of(dudosa));
        String detalle = """
                {"id":"1600000000001","title":"Cotton socks men","url":"https://www.alibaba.com/product-detail/x_1600000000001.html",
                 "price":{"currency":"USD","unit":"pairs","tiers":[{"min_quantity":100,"max_quantity":999,"price":0.95},
                 {"min_quantity":1000,"max_quantity":null,"price":0.50}]},"min_order":{"quantity":100,"unit":"pairs"}}""";
        when(cliente.producto(anyString())).thenReturn(JSON.readTree(detalle));

        ServicioMayoristas.Busqueda b = servicio(200, 4).buscar("cotton socks", 500, UnidadVenta.PAR, 1);
        ServicioMayoristas.Resultado r = b.resultados().get(0);
        assertTrue(r.consultoDetalle());
        assertNull(r.posicion());
        assertTrue(r.motivoFuera().contains("distintas"));
    }

    @Test
    void elDetalleAclaraUnaOfertaQueNoSeContradice() throws Exception {
        OfertaMayorista sinEscalas = oferta("1600000000009", "Cotton socks men", "Zhejiang", false, null, List.of());
        responder(List.of(sinEscalas));
        when(cliente.producto(anyString())).thenReturn(JSON.readTree("""
                {"id":"1600000000009","title":"Cotton socks men","url":"https://www.alibaba.com/product-detail/x_1600000000009.html",
                 "price":{"currency":"USD","unit":"pairs","tiers":[{"min_quantity":50,"max_quantity":null,"price":0.70}]},
                 "min_order":{"quantity":50,"unit":"pairs"}}"""));
        ServicioMayoristas.Resultado r = servicio(200, 4).buscar("cotton socks", 60, UnidadVenta.PAR, 1).resultados().get(0);
        assertEquals(1, r.posicion());
        assertEquals(new BigDecimal("42.00"), r.calculo().subtotal());
    }

    @Test
    void unaOfertaAmbiguaSinDetalleNoEntraAlOrden() {
        responder(List.of(oferta("1", "Cotton socks", "P", false, null, List.of())));
        ServicioMayoristas.Busqueda b = servicio(200, 0).buscar("cotton socks", 500, UnidadVenta.PAR, 1);
        assertNull(b.resultados().get(0).posicion());
        assertNotNull(b.resultados().get(0).motivoFuera());
        verify(cliente, never()).producto(anyString());
    }

    @Test
    void laCacheEvitaGastarOtraConsulta() {
        responder(List.of(oferta("1", "Cotton socks", "P", false, 100L, List.of(e(100, null, "1.00")))));
        ServicioMayoristas s = servicio(200, 4);
        s.buscar("Cotton socks", 100, UnidadVenta.PAR, 1);
        s.buscar("cotton socks", 300, UnidadVenta.PAR, 1);
        verify(cliente, times(1)).buscar(anyString(), anyInt());
    }

    @Test
    void respetaElTopeDiarioDeConsultas() {
        responder(List.of());
        ServicioMayoristas s = servicio(1, 4);
        s.buscar("socks", 100, UnidadVenta.PAR, 1);
        assertThrows(ServicioNoDisponibleException.class, () -> s.buscar("shoes", 100, UnidadVenta.PAR, 1));
    }

    @Test
    void despuesDeUn429EsperaSinConsultar() {
        when(cliente.buscar(anyString(), anyInt()))
                .thenThrow(new ApiShException(ApiShException.Tipo.LIMITE, "429", 120L));
        ServicioMayoristas s = servicio(200, 4);
        assertThrows(ServicioNoDisponibleException.class, () -> s.buscar("socks", 100, UnidadVenta.PAR, 1));
        assertThrows(ServicioNoDisponibleException.class, () -> s.buscar("shoes", 100, UnidadVenta.PAR, 1));
        verify(cliente, times(1)).buscar(anyString(), anyInt());
    }

    @Test
    void lasFallasDelProveedorLlegaComoMensajeClaro() {
        when(cliente.buscar(anyString(), anyInt()))
                .thenThrow(new ApiShException(ApiShException.Tipo.SIN_CREDITOS, "402", null));
        ServicioNoDisponibleException e = assertThrows(ServicioNoDisponibleException.class,
                () -> servicio(200, 4).buscar("socks", 100, UnidadVenta.PAR, 1));
        assertTrue(e.getMessage().contains("API.SH"));
    }

    @Test
    void sinClaveNoConsultaNada() {
        when(cliente.estaConfigurado()).thenReturn(false);
        ServicioMayoristas s = new ServicioMayoristas(cliente,
                new PropiedadesMayoristas("", null, null, null, null, null, null), conversor);
        assertThrows(ServicioNoDisponibleException.class, () -> s.buscar("socks", 100, UnidadVenta.PAR, 1));
        verify(cliente, never()).buscar(anyString(), anyInt());
    }

    @Test
    void validaCantidadPaginaYReferencia() {
        ServicioMayoristas s = servicio(200, 4);
        assertThrows(ValidacionException.class, () -> s.buscar("socks", 0, UnidadVenta.PAR, 1));
        assertThrows(ValidacionException.class, () -> s.buscar("socks", 10, UnidadVenta.DESCONOCIDA, 1));
        assertThrows(ValidacionException.class, () -> s.buscar("socks", 10, UnidadVenta.PAR, 4));
        assertThrows(ValidacionException.class, () -> s.detalle("https://evil.example.com/x", 10, UnidadVenta.PAR, null));
        verify(cliente, never()).buscar(anyString(), anyInt());
    }
}
