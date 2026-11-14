package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.MarketplaceDao;
import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.domain.Marketplace;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicioComparacionTest {

    private OfertaDao ofertaDao;
    private ServicioComparacion servicio;

    @BeforeEach
    void preparar() {
        ofertaDao = mock(OfertaDao.class);
        MarketplaceDao marketplaceDao = mock(MarketplaceDao.class);
        Marketplace google = new Marketplace();
        google.setCodigo("googleshopping");
        when(marketplaceDao.buscarPorId(anyShort())).thenReturn(Optional.of(google));
        when(ofertaDao.buscarPorId(anyLong())).thenReturn(Optional.empty());
        servicio = new ServicioComparacion(ofertaDao, marketplaceDao);
    }

    private Oferta oferta(long id, String titulo, String precio, String envio, String vendedor, String detalles) {
        Oferta o = new Oferta();
        o.setId(id);
        o.setMarketplaceId((short) 1);
        o.setIdExterno("g-" + id);
        o.setTitulo(titulo);
        o.setUrlDestino("https://www.tienda" + id + ".com/p");
        o.setPrecioActual(new BigDecimal(precio));
        o.setCostoEnvio(envio == null ? null : new BigDecimal(envio));
        o.setMoneda("COP");
        o.setVendedor(vendedor);
        o.setCondicion("nuevo");
        o.setDetalles(detalles);
        o.setConsultadoEn(OffsetDateTime.parse("2026-11-14T10:00:00-05:00"));
        when(ofertaDao.buscarPorId(id)).thenReturn(Optional.of(o));
        return o;
    }

    private static ServicioComparacion.Fila fila(ServicioComparacion.Comparacion c, String clave) {
        return c.filas().stream().filter(f -> f.clave().equals(clave)).findFirst().orElseThrow();
    }

    @Test
    void comparaLadoALadoYMarcaLasDiferencias() {
        oferta(1, "Samsung Galaxy A55 128GB Negro", "1500000", "0", "Éxito", "En stock | Color: Negro");
        oferta(2, "Samsung Galaxy A55 128 GB Azul", "1450000", "10000", "Alkosto", null);
        ServicioComparacion.Comparacion c = servicio.comparar(List.of(1L, 2L));

        assertEquals(2, c.productos().size());
        assertTrue(c.equivalentes());
        assertEquals(2L, c.masBaratoId());
        assertTrue(fila(c, "precio").difiere());
        assertFalse(fila(c, "almacenamiento").difiere(), "128GB y 128 GB son lo mismo");
        assertTrue(fila(c, "color").difiere());
    }

    @Test
    void loQueNoSeSabeDiceNoDisponible() {
        oferta(1, "Samsung Galaxy A55 128GB", "1500000", "0", "Éxito", null);
        oferta(2, "Samsung Galaxy A55 128GB", "1450000", "0", "Alkosto", null);
        ServicioComparacion.Comparacion c = servicio.comparar(List.of(1L, 2L));
        ServicioComparacion.Celda valoracion = fila(c, "valoracion").celdas().get(0);
        assertFalse(valoracion.disponible());
        assertEquals(ServicioComparacion.NO_DISPONIBLE, valoracion.valor());
        assertTrue(fila(c, "valoracion").nota().contains("no sobre la tienda"));
        assertEquals(ServicioComparacion.NO_DISPONIBLE, fila(c, "disponibilidad").celdas().get(1).valor());
    }

    @Test
    void sinEnvioInformadoNoSeMarcaElMasBarato() {
        oferta(1, "Samsung Galaxy A55 128GB", "1500000", "0", "Éxito", null);
        oferta(2, "Samsung Galaxy A55 128GB", "1400000", null, "Otra", null);
        ServicioComparacion.Comparacion c = servicio.comparar(List.of(1L, 2L));
        assertNull(c.masBaratoId());
        assertTrue(c.avisos().stream().anyMatch(a -> a.contains("envío")));
    }

    @Test
    void categoriasDistintasNoSePresentanComoEquivalentes() {
        oferta(1, "Celular Samsung Galaxy A55 128GB", "1500000", "0", "Éxito", null);
        oferta(2, "Forro para Samsung Galaxy A55", "30000", "0", "Éxito", null);
        ServicioComparacion.Comparacion c = servicio.comparar(List.of(1L, 2L));
        assertFalse(c.equivalentes());
        assertNull(c.masBaratoId());
        assertFalse(c.avisos().isEmpty());
    }

    @Test
    void otroModeloOVersionNoEsEquivalente() {
        oferta(1, "Apple iPhone 15 Pro 128GB", "5000000", "0", "Éxito", null);
        oferta(2, "Apple iPhone 15 128GB", "4000000", "0", "Éxito", null);
        ServicioComparacion.Comparacion c = servicio.comparar(List.of(1L, 2L));
        assertFalse(c.equivalentes());
        assertNull(c.masBaratoId());
    }

    @Test
    void exigeDeDosACuatroProductosDistintos() {
        assertThrows(ValidacionException.class, () -> servicio.comparar(List.of(1L)));
        assertThrows(ValidacionException.class, () -> servicio.comparar(List.of(1L, 1L)));
        assertThrows(ValidacionException.class, () -> servicio.comparar(List.of(1L, 2L, 3L, 4L, 5L)));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.comparar(List.of(98L, 99L)));
    }
}
