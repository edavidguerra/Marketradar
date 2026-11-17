package co.edu.unicesar.marketradar.service.mayorista;

import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.adapter.alibaba.ClienteAlibabaApiSh;
import co.edu.unicesar.marketradar.adapter.alibaba.LectorRespuestasAlibaba;
import co.edu.unicesar.marketradar.config.PropiedadesMayoristas;
import co.edu.unicesar.marketradar.domain.mayorista.EscalaPrecio;
import co.edu.unicesar.marketradar.domain.mayorista.OfertaMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.UnidadVenta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Una oferta que la búsqueda muestra como la más barata no se anuncia como ganadora hasta contrastarla con su ficha
 * (caso observado: US$44 por 100 pares en la búsqueda; en la ficha, variantes de US$8). Datos simulados: no se gasta
 * ningún crédito de API.SH.
 */
class ServicioMayoristasVerificacionTest {

    private ClienteAlibabaApiSh cliente;
    private FichasSimuladas fichas;

    @BeforeEach
    void preparar() {
        cliente = mock(ClienteAlibabaApiSh.class);
        when(cliente.estaConfigurado()).thenReturn(true);
        fichas = FichasSimuladas.en(cliente);
    }

    private ServicioMayoristas servicio(int detalles) {
        ConversorMoneda conversor = mock(ConversorMoneda.class);
        when(conversor.getMonedaDestino()).thenReturn("COP");
        when(conversor.tasaPara(anyString())).thenReturn(Optional.empty());
        return new ServicioMayoristas(cliente, new PropiedadesMayoristas("clave", null, 6, 3, detalles, 200, 60),
                conversor, Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC));
    }

    private static OfertaMayorista medias(String id, String precio, long minimo) {
        return new OfertaMayorista(id, "Cotton socks model " + id, "https://www.alibaba.com/product-detail/x_" + id
                + ".html", null, "Prov " + id, "CN", 3, false, false, minimo, UnidadVenta.PAR, UnidadVenta.PAR, null,
                List.of(new EscalaPrecio(minimo, null, new BigDecimal(precio))), new BigDecimal(precio),
                new BigDecimal(precio), "USD", null, List.of(), null, "busqueda");
    }

    private void pagina(List<OfertaMayorista> ofertas) {
        fichas.iguales(ofertas);
        when(cliente.buscar(anyString(), eq(1))).thenReturn(new LectorRespuestasAlibaba.Pagina(ofertas, null, 0, null,
                false));
    }

    private static ServicioMayoristas.Resultado de(ServicioMayoristas.Busqueda b, String id) {
        return b.resultados().stream().filter(r -> r.oferta().id().equals(id)).findFirst().orElseThrow();
    }

    /** Ficha sin escalas cuyas variantes cuestan US$8: no confirma los US$0,44 de la búsqueda. */
    private static String fichaDeOchoDolares(OfertaMayorista o) {
        return """
                {"id":"%s","title":"%s","url":"%s",
                 "price":{"currency":"USD","unit":"pairs","range":"Precio por determinar"},
                 "skus":[{"id":"sku-1","price":8.00,"options":["Negro"]},{"id":"sku-2","price":8.50,"options":["Blanco"]}],
                 "min_order":{"quantity":100,"unit":"pairs"}}""".formatted(o.id(), o.titulo(), o.url());
    }

    @Test
    void laDeUs044QueLaFichaContradiceNoSeAnunciaComoGanadora() {
        OfertaMayorista barata = medias("1600000000044", "0.44", 100);
        OfertaMayorista b = medias("1600000000060", "0.60", 100);
        OfertaMayorista c = medias("1600000000070", "0.70", 100);
        pagina(List.of(c, barata, b));
        fichas.ficha(barata, fichaDeOchoDolares(barata));

        ServicioMayoristas.Busqueda r = servicio(4).buscar("cotton socks", 100, UnidadVenta.PAR, 1);
        ServicioMayoristas.Resultado contradicha = de(r, barata.id());
        assertNull(contradicha.posicion(), "Sale del orden validado");
        assertNull(contradicha.calculo().subtotal(), "No se muestra el subtotal de US$44 que la ficha contradice");
        assertTrue(contradicha.motivoFuera().contains("US$0,44") && contradicha.motivoFuera().contains("US$8,00"),
                contradicha.motivoFuera());
        assertNotEquals(barata.id(), r.ganadoraId());
        assertEquals(b.id(), r.ganadoraId(), "Se evaluó la siguiente más barata y esa sí se verificó");
        assertEquals(1, de(r, b.id()).posicion());
        assertTrue(de(r, b.id()).verificada());
        verify(cliente).producto(barata.url());
        verify(cliente).producto(b.url());
    }

    @Test
    void siLaFichaFallaLaOfertaQuedaComoPrecioPreliminarYNoGana() {
        OfertaMayorista barata = medias("1600000000044", "0.44", 100);
        OfertaMayorista b = medias("1600000000060", "0.60", 100);
        when(cliente.buscar(anyString(), eq(1))).thenReturn(new LectorRespuestasAlibaba.Pagina(List.of(barata, b),
                null, 0, null, false));
        fichas.iguales(List.of(b)); // La de US$0,44 no tiene ficha: Alibaba no responde.

        ServicioMayoristas.Busqueda r = servicio(4).buscar("cotton socks", 100, UnidadVenta.PAR, 1);
        ServicioMayoristas.Resultado sinFicha = de(r, barata.id());
        assertEquals(1, sinFicha.posicion(), "Sigue en el orden global por subtotal…");
        assertTrue(sinFicha.preliminar(), "…pero marcada como precio preliminar, pendiente de verificar");
        assertFalse(sinFicha.verificada());
        assertEquals(b.id(), r.ganadoraId());
        assertEquals(1, r.verificadas());
        assertTrue(r.parcial());
        assertTrue(r.avisos().stream().anyMatch(a -> a.contains("preliminar")), r.avisos().toString());
    }

    @Test
    void conElPresupuestoAgotadoNingunaSeAnunciaComoGanadora() {
        pagina(List.of(medias("1600000000044", "0.44", 100), medias("1600000000060", "0.60", 100)));
        ServicioMayoristas.Busqueda r = servicio(0).buscar("cotton socks", 100, UnidadVenta.PAR, 1);
        assertNull(r.ganadoraId());
        assertEquals(0, r.verificadas());
        assertEquals(2, r.comparables(), "Siguen ordenadas por subtotal, con la marca de preliminar");
        assertTrue(r.resultados().stream().allMatch(ServicioMayoristas.Resultado::preliminar));
        assertTrue(r.avisos().stream().anyMatch(a -> a.contains("2 ofertas tienen precio preliminar")), r.avisos().toString());
        verify(cliente, never()).producto(anyString());
    }

    @Test
    void unaFichaEnOtraMonedaNoSeConvierteEnPrecioValido() {
        OfertaMayorista barata = medias("1600000000044", "0.44", 100);
        OfertaMayorista b = medias("1600000000060", "0.60", 100);
        pagina(List.of(barata, b));
        fichas.ficha(barata, """
                {"id":"1600000000044","title":"Cotton socks model 1600000000044","url":"%s",
                 "price":{"currency":"EUR","unit":"pairs","tiers":[{"min_quantity":100,"max_quantity":null,"price":0.44}]},
                 "min_order":{"quantity":100,"unit":"pairs"}}""".formatted(barata.url()));
        ServicioMayoristas.Busqueda r = servicio(4).buscar("cotton socks", 100, UnidadVenta.PAR, 1);
        assertNull(de(r, barata.id()).posicion());
        assertTrue(de(r, barata.id()).motivoFuera().contains("EUR"), de(r, barata.id()).motivoFuera());
        assertEquals(b.id(), r.ganadoraId());
    }

    @Test
    void unaFichaConOtraUnidadQuedaFuera() {
        OfertaMayorista barata = medias("1600000000044", "0.44", 100);
        OfertaMayorista b = medias("1600000000060", "0.60", 100);
        pagina(List.of(barata, b));
        fichas.ficha(barata, """
                {"id":"1600000000044","title":"Cotton socks model 1600000000044","url":"%s",
                 "price":{"currency":"USD","unit":"pieces","tiers":[{"min_quantity":100,"max_quantity":null,"price":0.44}]},
                 "min_order":{"quantity":100,"unit":"pieces"}}""".formatted(barata.url()));
        ServicioMayoristas.Busqueda r = servicio(4).buscar("cotton socks", 100, UnidadVenta.PAR, 1);
        assertNull(de(r, barata.id()).posicion());
        assertTrue(de(r, barata.id()).motivoFuera().contains("pares"), de(r, barata.id()).motivoFuera());
        assertEquals(b.id(), r.ganadoraId());
    }

    @Test
    void unaCantidadMenorQueElMinimoNoEntraNiGastaFicha() {
        OfertaMayorista minimoAlto = medias("1600000000044", "0.10", 500);
        OfertaMayorista b = medias("1600000000060", "0.60", 10);
        pagina(List.of(minimoAlto, b));
        ServicioMayoristas.Busqueda r = servicio(4).buscar("cotton socks", 100, UnidadVenta.PAR, 1);
        assertNull(de(r, minimoAlto.id()).posicion());
        assertTrue(de(r, minimoAlto.id()).motivoFuera() != null);
        verify(cliente, never()).producto(minimoAlto.url());
        assertEquals(b.id(), r.ganadoraId());
    }

    @Test
    void dosPaginasSeAcumulanSinRepetidosYConUnSoloOrdenGlobal() {
        OfertaMayorista a = medias("1600000000080", "0.80", 100);
        OfertaMayorista b = medias("1600000000090", "0.90", 100);
        OfertaMayorista nueva = medias("1600000000050", "0.50", 100);
        fichas.iguales(List.of(a, b, nueva));
        when(cliente.buscar(anyString(), eq(1))).thenReturn(new LectorRespuestasAlibaba.Pagina(List.of(a, b), 2, 0,
                null, false));
        when(cliente.buscar(anyString(), eq(2))).thenReturn(new LectorRespuestasAlibaba.Pagina(List.of(b, nueva), null,
                0, null, false));
        ServicioMayoristas s = servicio(4);
        assertEquals(a.id(), s.buscar("cotton socks", 100, UnidadVenta.PAR, 1).ganadoraId());
        ServicioMayoristas.Busqueda ambas = s.buscar("cotton socks", 100, UnidadVenta.PAR, 2, Map.of());
        Set<String> ids = new HashSet<>();
        ambas.resultados().forEach(r -> assertTrue(ids.add(r.oferta().id()), "Repetido: " + r.oferta().id()));
        assertEquals(3, ambas.resultados().size());
        assertEquals(List.of(nueva.id(), a.id(), b.id()), ambas.resultados().stream().map(r -> r.oferta().id()).toList());
        assertEquals(List.of(1, 2, 3), ambas.resultados().stream().map(ServicioMayoristas.Resultado::posicion).toList());
        assertEquals(nueva.id(), ambas.ganadoraId(), "La de la página 2 se verificó y pasa a ser la ganadora");
    }

    // --- Preliminar de US$40 y verificada de US$60 (100 pares) ---

    @Test
    void unaPreliminarMasBarataNoLeQuitaLaRecomendacionALaVerificadaNiSeVuelveGanadora() {
        OfertaMayorista preliminar = medias("1600000000040", "0.40", 100);
        OfertaMayorista verificada = medias("1600000000060", "0.60", 100);
        when(cliente.buscar(anyString(), eq(1))).thenReturn(new LectorRespuestasAlibaba.Pagina(
                List.of(verificada, preliminar), null, 0, null, false));
        fichas.iguales(List.of(verificada)); // La de US$40 no tiene ficha: queda preliminar.

        ServicioMayoristas.Busqueda r = servicio(4).buscar("cotton socks", 100, UnidadVenta.PAR, 1);

        ServicioMayoristas.Resultado p = de(r, preliminar.id());
        ServicioMayoristas.Resultado v = de(r, verificada.id());
        assertEquals(0, new BigDecimal("40.00").compareTo(p.calculo().subtotal()));
        assertEquals(0, new BigDecimal("60.00").compareTo(v.calculo().subtotal()));
        assertEquals(1, p.posicion(), "El orden global sigue siendo por subtotal");
        assertTrue(p.preliminar());
        assertFalse(p.verificada());
        assertEquals(2, v.posicion());
        assertTrue(v.verificada());
        assertEquals(verificada.id(), r.ganadoraId(), "La menor entre las verificadas, no la menor de todas");
        assertEquals(2, r.comparables());
        assertEquals(1, r.verificadas());
        assertTrue(r.avisos().stream().anyMatch(a -> a.contains("1 oferta tiene precio preliminar")), r.avisos().toString());
        assertTrue(r.avisos().stream().noneMatch(a -> a.contains("más bajo") || a.contains("más barata")),
                "Ningún aviso dice que US$60 sea el precio más bajo de todas: " + r.avisos());
    }

    @Test
    void sinVerificadasNoHayGanadora() {
        OfertaMayorista a = medias("1600000000040", "0.40", 100);
        OfertaMayorista b = medias("1600000000060", "0.60", 100);
        when(cliente.buscar(anyString(), eq(1))).thenReturn(new LectorRespuestasAlibaba.Pagina(List.of(a, b), null, 0,
                null, false));
        fichas.iguales(List.of()); // Ninguna ficha responde.

        ServicioMayoristas.Busqueda r = servicio(4).buscar("cotton socks", 100, UnidadVenta.PAR, 1);

        assertNull(r.ganadoraId());
        assertEquals(0, r.verificadas());
        assertTrue(r.resultados().stream().allMatch(ServicioMayoristas.Resultado::preliminar));
    }

    @Test
    void siLaFichaContradiceElPrecioCambianJuntosElegibilidadOrdenGanadoraYContadores() {
        OfertaMayorista barata = medias("1600000000040", "0.40", 100);
        OfertaMayorista verificada = medias("1600000000060", "0.60", 100);
        pagina(List.of(verificada, barata));
        fichas.ficha(barata, fichaDeOchoDolares(barata));

        ServicioMayoristas.Busqueda r = servicio(4).buscar("cotton socks", 100, UnidadVenta.PAR, 1);

        ServicioMayoristas.Resultado contradicha = de(r, barata.id());
        assertNull(contradicha.posicion(), "Sale del orden");
        assertFalse(contradicha.preliminar(), "Ya no es preliminar: la ficha la contradijo");
        assertFalse(contradicha.verificada());
        assertNull(contradicha.calculo().subtotal(), "No se muestra el subtotal de US$40 contradicho");
        assertEquals(1, de(r, verificada.id()).posicion(), "La verificada sube al primer lugar");
        assertEquals(verificada.id(), r.ganadoraId());
        assertEquals(1, r.comparables(), "Los contadores ya no cuentan la contradicha");
        assertEquals(1, r.verificadas());
    }
}
