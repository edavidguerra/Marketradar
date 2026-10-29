package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.Dao;
import co.edu.unicesar.marketradar.domain.Busqueda;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de búsquedas realizadas (tabla {@code busqueda}).
 */
@Repository
public class BusquedaDaoJdbc extends DaoJdbcBase implements Dao<Busqueda> {

    private static final String COLUMNAS = "id, termino, total_resultados, duracion_ms, creada_en";

    public BusquedaDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Busqueda insertar(Busqueda busqueda) {
        long id = insertarYObtenerId("INSERT INTO busqueda (termino, total_resultados, duracion_ms) VALUES (?, ?, ?)",
                sentencia -> {
                    sentencia.setString(1, busqueda.getTermino());
                    sentencia.setInt(2, busqueda.getTotalResultados());
                    sentencia.setInt(3, busqueda.getDuracionMs());
                });
        busqueda.setId(id);
        return busqueda;
    }

    @Override
    public Optional<Busqueda> buscarPorId(long id) {
        return consultarUno("SELECT " + COLUMNAS + " FROM busqueda WHERE id = ?",
                sentencia -> sentencia.setLong(1, id), this::mapear);
    }

    @Override
    public List<Busqueda> listarTodos() {
        return consultarLista("SELECT " + COLUMNAS + " FROM busqueda ORDER BY creada_en DESC", sentencia -> { }, this::mapear);
    }

    @Override
    public boolean actualizar(Busqueda busqueda) {
        return ejecutarActualizacion("UPDATE busqueda SET termino = ?, total_resultados = ?, duracion_ms = ? WHERE id = ?",
                sentencia -> {
                    sentencia.setString(1, busqueda.getTermino());
                    sentencia.setInt(2, busqueda.getTotalResultados());
                    sentencia.setInt(3, busqueda.getDuracionMs());
                    sentencia.setLong(4, busqueda.getId());
                }) > 0;
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutarActualizacion("DELETE FROM busqueda WHERE id = ?", sentencia -> sentencia.setLong(1, id)) > 0;
    }

    private Busqueda mapear(ResultSet fila) throws SQLException {
        Busqueda busqueda = new Busqueda(fila.getString("termino"), fila.getInt("total_resultados"),
                fila.getInt("duracion_ms"));
        busqueda.setId(fila.getLong("id"));
        busqueda.setCreadaEn(fila.getObject("creada_en", OffsetDateTime.class));
        return busqueda;
    }
}
