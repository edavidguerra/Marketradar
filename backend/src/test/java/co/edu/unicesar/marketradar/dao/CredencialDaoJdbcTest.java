package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.dao.CredencialDao.CredencialGuardada;
import co.edu.unicesar.marketradar.dao.jdbc.CredencialDaoJdbc;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de integración del DAO de credenciales contra PostgreSQL 16 real (requiere Docker).
 */
@Testcontainers(disabledWithoutDocker = true)
class CredencialDaoJdbcTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private static CredencialDaoJdbc dao;

    @BeforeAll
    static void preparar() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        dao = new CredencialDaoJdbc(dataSource);
    }

    @Test
    void guardaYReemplazaLaCredencial() {
        assertTrue(dao.buscar("mercadolibre.refresh_token").isEmpty());

        dao.guardar("mercadolibre.refresh_token", new CredencialGuardada("TG-1", HASH_A));
        dao.guardar("mercadolibre.refresh_token", new CredencialGuardada("TG-2", HASH_B));

        assertEquals(Optional.of(new CredencialGuardada("TG-2", HASH_B)), dao.buscar("mercadolibre.refresh_token"));
    }
}
