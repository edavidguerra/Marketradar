package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.dao.jdbc.HistorialPrecioDaoJdbc;
import co.edu.unicesar.marketradar.dao.jdbc.MarketplaceDaoJdbc;
import co.edu.unicesar.marketradar.dao.jdbc.OfertaDaoJdbc;
import co.edu.unicesar.marketradar.domain.HistorialPrecio;
import co.edu.unicesar.marketradar.domain.Marketplace;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.OfertaDestacada;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de integración de los DAO contra PostgreSQL 16 real (requiere Docker).
 */
@Testcontainers(disabledWithoutDocker = true)
class OfertaDaoJdbcTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static OfertaDaoJdbc ofertaDao;
    private static HistorialPrecioDaoJdbc historialDao;
    private static MarketplaceDaoJdbc marketplaceDao;

    @BeforeAll
    static void preparar() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        ofertaDao = new OfertaDaoJdbc(dataSource);
        historialDao = new HistorialPrecioDaoJdbc(dataSource);
        marketplaceDao = new MarketplaceDaoJdbc(dataSource);
    }

    @Test
    void crudCompletoDeOfertaConHistorial() {
        Marketplace aliexpress = marketplaceDao.buscarPorCodigo("aliexpress").orElseThrow();

        Oferta oferta = new Oferta();
        oferta.setMarketplaceId(aliexpress.getId());
        oferta.setIdExterno("100");
        oferta.setTitulo("Licuadora 600 W");
        oferta.setUrlDestino("https://ejemplo.com/100");
        oferta.setPrecioActual(new BigDecimal("150000.00"));
        ofertaDao.insertar(oferta);
        historialDao.insertar(new HistorialPrecio(oferta.getId(), oferta.getPrecioActual(), "COP"));

        Oferta leida = ofertaDao.buscarPorMarketplaceYIdExterno(aliexpress.getId(), "100").orElseThrow();
        assertEquals(0, new BigDecimal("150000").compareTo(leida.getPrecioActual()));

        leida.setPrecioActual(new BigDecimal("140000.00"));
        assertTrue(ofertaDao.actualizar(leida));
        assertEquals(1, historialDao.listarPorOferta(leida.getId()).size());

        assertTrue(ofertaDao.eliminar(leida.getId()));
        assertFalse(ofertaDao.buscarPorId(leida.getId()).isPresent());
        assertTrue(historialDao.listarPorOferta(leida.getId()).isEmpty());
    }

    @Test
    void guardarLoteInsertaActualizaYSoloRegistraLosCambiosDePrecio() {
        short google = marketplaceDao.buscarPorCodigo("googleshopping").orElseThrow().getId();

        Map<String, Long> primera = ofertaDao.guardarLote(List.of(
                ofertaDeLote(google, "L-1", "100000.00"),
                ofertaDeLote(google, "L-2", "200000.00"),
                ofertaDeLote(google, "L-1", "999999.00")));
        assertEquals(2, primera.size(), "las ofertas repetidas se guardan una sola vez");
        long idUno = primera.get(OfertaDao.clave(google, "L-1"));
        long idDos = primera.get(OfertaDao.clave(google, "L-2"));
        assertEquals(0, new BigDecimal("100000").compareTo(ofertaDao.buscarPorId(idUno).orElseThrow().getPrecioActual()),
                "gana la primera aparición de la oferta");
        assertEquals(1, historialDao.listarPorOferta(idUno).size());
        assertEquals(1, historialDao.listarPorOferta(idDos).size());

        Map<String, Long> segunda = ofertaDao.guardarLote(List.of(
                ofertaDeLote(google, "L-1", "90000.00"),
                ofertaDeLote(google, "L-2", "200000.00"),
                ofertaDeLote(google, "L-3", "50000.00")));
        assertEquals(idUno, segunda.get(OfertaDao.clave(google, "L-1")), "una oferta existente conserva su id");
        assertEquals(idDos, segunda.get(OfertaDao.clave(google, "L-2")));
        assertNotEquals(idUno, segunda.get(OfertaDao.clave(google, "L-3")));
        assertEquals(0, new BigDecimal("90000").compareTo(ofertaDao.buscarPorId(idUno).orElseThrow().getPrecioActual()));
        assertEquals(2, historialDao.listarPorOferta(idUno).size(), "el precio bajó: se registra en el historial");
        assertEquals(1, historialDao.listarPorOferta(idDos).size(), "el precio no cambió: no se repite en el historial");
        assertEquals(1, historialDao.listarPorOferta(segunda.get(OfertaDao.clave(google, "L-3"))).size());
        assertTrue(ofertaDao.guardarLote(List.of()).isEmpty());
    }

    private static Oferta ofertaDeLote(short marketplaceId, String idExterno, String precio) {
        Oferta oferta = new Oferta();
        oferta.setMarketplaceId(marketplaceId);
        oferta.setIdExterno(idExterno);
        oferta.setTitulo("Olla arrocera " + idExterno);
        oferta.setUrlDestino("https://ejemplo.com/" + idExterno);
        oferta.setPrecioActual(new BigDecimal(precio));
        oferta.setMoneda("COP");
        return oferta;
    }

    @Test
    void encuentraOfertasDestacadasParaElInicio() {
        short mercadoLibre = marketplaceDao.buscarPorCodigo("mercadolibre").orElseThrow().getId();
        ofertaDao.guardarLote(List.of(tensiometro(mercadoLibre, "T-1", "120000.00", "Salud Total")));
        ofertaDao.guardarLote(List.of(tensiometro(mercadoLibre, "T-1", "90000.00", "Salud Total")));

        OfertaDestacada porTitulo = ofertaDao.buscarMasBarataPorTitulo("tensiómetros").orElseThrow();
        assertEquals("mercadolibre", porTitulo.codigoMarketplace());
        assertEquals("T-1", porTitulo.oferta().getIdExterno());
        assertEquals("T-1", ofertaDao.listarMasBaratasPorTitulo("tensiómetros", 10).get(0).oferta().getIdExterno());

        OfertaDestacada baja = ofertaDao.buscarMayorBajaDePrecio(30).orElseThrow();
        assertEquals("T-1", baja.oferta().getIdExterno());
        assertEquals(0, new BigDecimal("30000").compareTo(baja.baja()));
        assertTrue(ofertaDao.buscarMasBarataPorTitulo("bicicleta").isEmpty());
    }

    @Test
    void unaBajaDePrecioSoloCuentaEnLaMismaTiendaYCondicion() {
        short google = marketplaceDao.buscarPorCodigo("googleshopping").orElseThrow().getId();
        // La misma ficha de Google pasa de una tienda a otra más barata: no es una baja de precio.
        ofertaDao.guardarLote(List.of(tensiometro(google, "B-1", "5000000.00", "Tienda Cara")));
        ofertaDao.guardarLote(List.of(tensiometro(google, "B-1", "100000.00", "Tienda Barata")));
        // Otra pasa de nueva a reacondicionada: tampoco.
        Oferta nueva = tensiometro(google, "B-2", "4000000.00", "Tienda Fija");
        nueva.setCondicion("nuevo");
        Oferta reacondicionada = tensiometro(google, "B-2", "150000.00", "Tienda Fija");
        reacondicionada.setCondicion("reacondicionado");
        ofertaDao.guardarLote(List.of(nueva));
        ofertaDao.guardarLote(List.of(reacondicionada));

        assertTrue(ofertaDao.buscarMayorBajaDePrecio(30).stream()
                .noneMatch(baja -> baja.oferta().getIdExterno().startsWith("B-")),
                "ninguna de las dos es la misma oferta más barata");
    }

    private static Oferta tensiometro(short marketplaceId, String idExterno, String precio, String vendedor) {
        Oferta oferta = ofertaDeLote(marketplaceId, idExterno, precio);
        oferta.setTitulo("Tensiómetro digital de brazo " + idExterno);
        oferta.setVendedor(vendedor);
        return oferta;
    }
}
