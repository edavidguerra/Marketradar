package co.edu.unicesar.marketradar.service.mayorista;

import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.adapter.alibaba.ApiShException;
import co.edu.unicesar.marketradar.adapter.alibaba.ClienteAlibabaApiSh;
import co.edu.unicesar.marketradar.adapter.alibaba.LectorRespuestasAlibaba;
import co.edu.unicesar.marketradar.config.PropiedadesMayoristas;
import co.edu.unicesar.marketradar.domain.mayorista.EscalaPrecio;
import co.edu.unicesar.marketradar.domain.mayorista.OfertaMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.RelevanciaMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.UnidadVenta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Páginas acumuladas, empates, conciliación con la ficha, relevancia y límites de consultas de Emprender. Todo con
 * respuestas simuladas: no se gasta ningún crédito de API.SH.
 */
class ServicioMayoristasPaginasTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private ClienteAlibabaApiSh cliente;

    @BeforeEach
    void preparar() {
        cliente = mock(ClienteAlibabaApiSh.class);
        when(cliente.estaConfigurado()).thenReturn(true);
    }

    private ServicioMayoristas servicio(int detalles) {
        ConversorMoneda conversor = mock(ConversorMoneda.class);
        when(conversor.getMonedaDestino()).thenReturn("COP");
        when(conversor.tasaPara(anyString())).thenReturn(Optional.empty());
        return new ServicioMayoristas(cliente, new PropiedadesMayoristas("clave", null, 6, 3, detalles, 200, 60),
                conversor, Clock.fixed(Instant.parse("2026-11-14T15:00:00Z"), ZoneOffset.UTC));
    }

    private static OfertaMayorista aretes(String id, String proveedor, String precio) {
        return new OfertaMayorista(id, "Stainless Steel Earrings model " + id, "https://www.alibaba.com/product-detail/x_" + id
                + ".html", null, proveedor, "CN", 3, false, false, 1L, UnidadVenta.PAR, UnidadVenta.PAR, null,
                List.of(new EscalaPrecio(1, null, new BigDecimal(precio))), new BigDecimal(precio),
                new BigDecimal(precio), "USD", null, List.of(), null, "busqueda");
    }

    private void paginas(List<OfertaMayorista> uno, List<OfertaMayorista> dos) {
        when(cliente.buscar(anyString(), eq(1))).thenReturn(new LectorRespuestasAlibaba.Pagina(uno, 2, 0, null, false));
        when(cliente.buscar(anyString(), eq(2))).thenReturn(new LectorRespuestasAlibaba.Pagina(dos, 3, 0, null, false));
    }

    @Test
    void alCargarLaSegundaPaginaHayUnSoloMinimoYSinRepetidos() {
        // Página 1: el más barato es US$85 (para 1 par). Página 2 trae uno de US$45 y siete repetidos de la página 1.
        List<OfertaMayorista> uno = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            uno.add(aretes("16000000000" + (10 + i), "P" + i, String.valueOf(85 + i)));
        }
        List<OfertaMayorista> dos = new ArrayList<>(uno.subList(0, 7));
        dos.add(aretes("1600000000099", "Nuevo", "45"));
        paginas(uno, dos);
        ServicioMayoristas s = servicio(0);
        ServicioMayoristas.Busqueda primera = s.buscar("stainless steel earrings", 1, UnidadVenta.PAR, 1);
        assertEquals(new BigDecimal("85.00"), primera.resultados().get(0).calculo().subtotal());

        ServicioMayoristas.Busqueda ambas = s.buscar("stainless steel earrings", 1, UnidadVenta.PAR, 2, Map.of());
        assertEquals(9, ambas.resultados().size(), "8 de la página 1 + 1 nueva; los 7 repetidos no se muestran");
        Set<String> ids = new HashSet<>();
        ambas.resultados().forEach(r -> assertTrue(ids.add(r.oferta().id()), "Repetido: " + r.oferta().id()));
        List<ServicioMayoristas.Resultado> primeros = ambas.resultados().stream()
                .filter(r -> Integer.valueOf(1).equals(r.posicion())).toList();
        assertEquals(1, primeros.size(), "Un solo \"subtotal más bajo\"");
        assertEquals("1600000000099", primeros.get(0).oferta().id());
        assertEquals(2, ambas.resultados().get(1).posicion(), "El de US$85 baja al puesto 2");
        assertEquals(9, ambas.comparables());
        assertTrue(ambas.descartados() >= 7);
        verify(cliente, times(1)).buscar(anyString(), eq(1)); // La página 1 salió de la caché la segunda vez.
    }

    @Test
    void losEmpatesCompartenPuestoYSeMarcan() {
        paginas(List.of(aretes("1600000000001", "A", "2.00"), aretes("1600000000002", "B", "2.00"),
                aretes("1600000000003", "C", "3.00")), List.of());
        ServicioMayoristas.Busqueda b = servicio(0).buscar("stainless steel earrings", 10, UnidadVenta.PAR, 1);
        assertEquals(1, b.resultados().get(0).posicion());
        assertEquals(1, b.resultados().get(1).posicion());
        assertTrue(b.resultados().get(0).empate() && b.resultados().get(1).empate());
        assertEquals(3, b.resultados().get(2).posicion());
        assertFalse(b.resultados().get(2).empate());
    }

    @Test
    void laOfertaDe049QueLaFichaNoConfirmaSaleDelOrden() throws Exception {
        OfertaMayorista barata = new OfertaMayorista("1600000000049", "Stainless Steel Huggie Earrings",
                "https://www.alibaba.com/product-detail/x_1600000000049.html", null, "Yiwu", "CN", 2, false, false, 12L,
                UnidadVenta.PAR, UnidadVenta.PAR, null, List.of(new EscalaPrecio(12, null, new BigDecimal("0.49"))),
                new BigDecimal("0.49"), new BigDecimal("0.60"), "USD", null, List.of(), null, "busqueda");
        paginas(List.of(barata, aretes("1600000000002", "B", "1.20")), List.of());
        when(cliente.producto(anyString())).thenReturn(JSON.readTree("""
                {"id":"1600000000049","title":"Stainless Steel Huggie Earrings","url":"https://www.alibaba.com/product-detail/x_1600000000049.html",
                 "price":{"currency":"USD","unit":"pairs","range":"Precio por determinar"},
                 "skus":[{"id":"sku-1","price":5.00,"options":["Dorado"]},{"id":"sku-2","price":5.00,"options":["Plata"]}],
                 "supplier":{"name":"Yiwu"}}"""));
        ServicioMayoristas.Busqueda b = servicio(4).buscar("stainless steel earrings", 100, UnidadVenta.PAR, 1);
        ServicioMayoristas.Resultado primero = b.resultados().get(0);
        assertEquals("1600000000002", primero.oferta().id(), "La de US$0,49 ya no es la más barata");
        assertEquals(1, primero.posicion());
        ServicioMayoristas.Resultado dudosa = b.resultados().stream()
                .filter(r -> r.oferta().id().equals("1600000000049")).findFirst().orElseThrow();
        assertNull(dudosa.posicion());
        assertNull(dudosa.calculo().subtotal(), "No se muestra un subtotal que la ficha contradice");
        assertTrue(dudosa.motivoFuera().contains("US$0,49") && dudosa.motivoFuera().contains("US$5,00"),
                dudosa.motivoFuera());
        assertTrue(dudosa.oferta().escalas().isEmpty(), "La tarjeta muestra lo que dice la ficha");

        // Al abrir el detalle se aplican las mismas reglas: tampoco hay subtotal.
        ServicioMayoristas.Resultado detalle = servicio(4).detalle("1600000000049", 100, UnidadVenta.PAR, null);
        assertNull(detalle.calculo().subtotal());
    }

    @Test
    void elDetalleYaConsultadoActualizaLaListaSinGastarConsultas() throws Exception {
        paginas(List.of(aretes("1600000000001", "A", "2.00"), aretes("1600000000002", "B", "3.00")), List.of());
        when(cliente.producto(anyString())).thenReturn(JSON.readTree("""
                {"id":"1600000000001","title":"Stainless Steel Earrings model 1600000000001","url":"https://www.alibaba.com/product-detail/x_1600000000001.html",
                 "price":{"currency":"USD","unit":"pairs","tiers":[{"min_quantity":1,"max_quantity":null,"price":4.00}]},
                 "min_order":{"quantity":1,"unit":"pairs"}}"""));
        ServicioMayoristas s = servicio(4);
        s.detalle("1600000000001", 10, UnidadVenta.PAR, null);
        ServicioMayoristas.Busqueda b = s.buscar("stainless steel earrings", 10, UnidadVenta.PAR, 1);
        // La ficha dice 4,00 y la búsqueda 2,00: se contradicen y queda fuera; la otra pasa a ser la primera.
        assertEquals("1600000000002", b.resultados().get(0).oferta().id());
        verify(cliente, times(1)).producto(anyString());
    }

    @Test
    void cadaIntentoDeFichaCuentaAunqueFalleYDosFallosSeguidosCortan() {
        List<OfertaMayorista> dudosas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            // Sin escalas: todas necesitan la ficha.
            dudosas.add(new OfertaMayorista("16000000000" + (20 + i), "Stainless Steel Earrings " + i,
                    "https://www.alibaba.com/product-detail/x_" + i + ".html", null, "P" + i, "CN", 1, false, false,
                    null, UnidadVenta.PAR, UnidadVenta.PAR, null, List.of(), null, null, "USD", null, List.of(), null,
                    "busqueda"));
        }
        paginas(dudosas, List.of());
        when(cliente.producto(anyString())).thenThrow(new ApiShException(ApiShException.Tipo.NO_DISPONIBLE,
                "timeout", null));
        ServicioMayoristas s = servicio(4);
        ServicioMayoristas.Busqueda b = s.buscar("stainless steel earrings", 10, UnidadVenta.PAR, 1);
        verify(cliente, times(2)).producto(anyString());
        assertTrue(b.resultados().stream().allMatch(r -> r.posicion() == null), "Sin ficha no hay precio claro");
        assertTrue(b.parcial());
        assertTrue(b.avisos().stream().anyMatch(a -> a.contains("varias veces seguidas")), b.avisos().toString());
        assertEquals(3, s.consultasHoy(), "1 búsqueda + 2 intentos de ficha fallidos cuentan en el tope diario");
        assertEquals(2, b.siguientePagina(), "Una ficha que falló no esconde la página siguiente");
    }

    @Test
    void siFallaLaSegundaPaginaSeMuestranLasAnterioresYSeDice() {
        when(cliente.buscar(anyString(), eq(1))).thenReturn(new LectorRespuestasAlibaba.Pagina(
                List.of(aretes("1600000000001", "A", "2.00")), 2, 0, null, false));
        when(cliente.buscar(anyString(), eq(2))).thenThrow(new ApiShException(ApiShException.Tipo.NO_DISPONIBLE,
                "503", null));
        ServicioMayoristas.Busqueda b = servicio(0).buscar("stainless steel earrings", 1, UnidadVenta.PAR, 2, Map.of());
        assertEquals(1, b.resultados().size());
        assertTrue(b.parcial());
        assertEquals(2, b.siguientePagina(), "La página que falló se puede pedir otra vez con Ver más");
        assertTrue(b.avisos().stream().anyMatch(a -> a.contains("página 2")));
        verify(cliente, times(2)).buscar(anyString(), eq(2)); // Un reintento acotado.
    }

    @Test
    void lasDudosasVanAparteYLasDeOtroTipoSeQuitan() {
        paginas(List.of(aretes("1600000000001", "A", "2.00"),
                new OfertaMayorista("1600000000002", "Stainless Steel Necklace", "https://www.alibaba.com/product-detail/x_2.html",
                        null, "B", "CN", 1, false, false, 1L, UnidadVenta.PAR, UnidadVenta.PAR, null,
                        List.of(new EscalaPrecio(1, null, new BigDecimal("0.10"))), new BigDecimal("0.10"),
                        new BigDecimal("0.10"), "USD", null, List.of(), null, "busqueda"),
                new OfertaMayorista("1600000000003", "Pearl Drop Earrings", "https://www.alibaba.com/product-detail/x_3.html",
                        null, "C", "CN", 1, false, false, 1L, UnidadVenta.PAR, UnidadVenta.PAR, null,
                        List.of(new EscalaPrecio(1, null, new BigDecimal("0.20"))), new BigDecimal("0.20"),
                        new BigDecimal("0.20"), "USD", null, List.of(), null, "busqueda")), List.of());
        ServicioMayoristas.Busqueda b = servicio(0).buscar("stainless steel earrings", 10, UnidadVenta.PAR, 1);
        assertEquals(2, b.resultados().size(), "El collar no aparece");
        assertEquals("1600000000001", b.resultados().get(0).oferta().id());
        assertEquals(1, b.resultados().get(0).posicion());
        ServicioMayoristas.Resultado perla = b.resultados().get(1);
        assertEquals(RelevanciaMayorista.Nivel.DUDOSA, perla.relevancia());
        assertNull(perla.posicion(), "Aunque es más barata, no entra al orden: no dice que sea de acero");
    }

    @Test
    void laVarianteElegidaSeUsaEnLaListaYSiNoCuadraSaleDelOrden() throws Exception {
        paginas(List.of(aretes("1600000000001", "A", "2.00"), aretes("1600000000002", "B", "3.00")), List.of());
        when(cliente.producto(anyString())).thenReturn(JSON.readTree("""
                {"id":"1600000000001","title":"Stainless Steel Earrings model 1600000000001","url":"https://www.alibaba.com/product-detail/x_1600000000001.html",
                 "price":{"currency":"USD","unit":"pairs","tiers":[{"min_quantity":1,"max_quantity":null,"price":2.00}]},
                 "min_order":{"quantity":1,"unit":"pairs"},
                 "skus":[{"id":"sku-1","price":2.00,"options":["Dorado"]},{"id":"sku-2","price":6.00,"options":["Rosa"]}]}"""));
        ServicioMayoristas s = servicio(4);
        ServicioMayoristas.Busqueda conDorado = s.buscar("stainless steel earrings", 10, UnidadVenta.PAR, 1,
                Map.of("1600000000001", "sku-1"));
        assertEquals(1, conDorado.resultados().get(0).posicion());
        assertEquals("1600000000001", conDorado.resultados().get(0).oferta().id());
        ServicioMayoristas.Busqueda conRosa = s.buscar("stainless steel earrings", 10, UnidadVenta.PAR, 1,
                Map.of("1600000000001", "sku-2"));
        assertEquals("1600000000002", conRosa.resultados().get(0).oferta().id(),
                "Con la variante de otro precio sin escala, la oferta sale del orden");
        verify(cliente, times(1)).producto(anyString());
    }

    @Test
    void siLaFichaNoSePudoConsultarUnaContradiccionDeLaBusquedaLaDejaFuera() {
        // El rango (0,49 a 0,60) no coincide con su única escala (0,49): sin ficha que lo aclare, no entra al orden.
        OfertaMayorista rara = new OfertaMayorista("1600000000049", "Stainless Steel Huggie Earrings",
                "https://www.alibaba.com/product-detail/x_1600000000049.html", null, "Yiwu", "CN", 2, false, false, 12L,
                UnidadVenta.PAR, UnidadVenta.PAR, null, List.of(new EscalaPrecio(12, null, new BigDecimal("0.49"))),
                new BigDecimal("0.49"), new BigDecimal("0.60"), "USD", null, List.of(), null, "busqueda");
        paginas(List.of(rara, aretes("1600000000002", "B", "1.20")), List.of());
        ServicioMayoristas.Busqueda b = servicio(0).buscar("stainless steel earrings", 100, UnidadVenta.PAR, 1);
        assertEquals("1600000000002", b.resultados().get(0).oferta().id());
        assertNull(b.resultados().get(1).posicion());
        assertTrue(b.resultados().get(1).motivoFuera().contains("rango"), b.resultados().get(1).motivoFuera());
    }

    @Test
    void sinNecesidadNoPideFichas() {
        paginas(List.of(aretes("1600000000001", "A", "2.00")), List.of());
        servicio(4).buscar("stainless steel earrings", 10, UnidadVenta.PAR, 1);
        verify(cliente, never()).producto(anyString());
    }
}
