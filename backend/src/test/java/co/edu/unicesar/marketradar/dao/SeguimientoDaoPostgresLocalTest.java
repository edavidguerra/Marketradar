package co.edu.unicesar.marketradar.dao;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Los mismos casos de {@link SeguimientoDaoJdbcTest}, pero contra un PostgreSQL ya instalado, para equipos sin Docker.
 * Solo corre si existe la variable {@code MARKETRADAR_PRUEBAS_PG_URL} (por ejemplo
 * {@code jdbc:postgresql://localhost:5432/marketradar_pruebas?user=postgres}); la base debe estar vacía, porque se le
 * aplican todas las migraciones.
 */
@EnabledIfEnvironmentVariable(named = "MARKETRADAR_PRUEBAS_PG_URL", matches = "jdbc:postgresql:.+")
class SeguimientoDaoPostgresLocalTest extends CasosSeguimientoDao {

    @BeforeAll
    static void preparar() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(System.getenv("MARKETRADAR_PRUEBAS_PG_URL"));
        Flyway.configure().dataSource(dataSource).load().migrate();
        fuente = dataSource;
    }
}
