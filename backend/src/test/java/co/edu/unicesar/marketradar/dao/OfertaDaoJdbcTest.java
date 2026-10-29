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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void encuentraOfertasDestacadasParaElInicio() {
        Marketplace mercadoLibre = marketplaceDao.buscarPorCodigo("mercadolibre").orElseThrow();
        Oferta tensiometro = new Oferta();
        tensiometro.setMarketplaceId(mercadoLibre.getId());
        tensiometro.setIdExterno("T-1");
        tensiometro.setTitulo("Tensiómetro digital de brazo");
        tensiometro.setUrlDestino("https://ejemplo.com/t-1");
        tensiometro.setPrecioActual(new BigDecimal("90000.00"));
        ofertaDao.insertar(tensiometro);
        historialDao.insertar(new HistorialPrecio(tensiometro.getId(), new BigDecimal("120000.00"), "COP"));
        historialDao.insertar(new HistorialPrecio(tensiometro.getId(), new BigDecimal("90000.00"), "COP"));

        OfertaDestacada porTitulo = ofertaDao.buscarMasBarataPorTitulo("tensiómetros").orElseThrow();
        assertEquals("mercadolibre", porTitulo.codigoMarketplace());
        assertEquals("T-1", porTitulo.oferta().getIdExterno());

        OfertaDestacada baja = ofertaDao.buscarMayorBajaDePrecio(30).orElseThrow();
        assertEquals(0, new BigDecimal("30000").compareTo(baja.baja()));
        assertTrue(ofertaDao.buscarMasBarataPorTitulo("bicicleta").isEmpty());
    }
}
