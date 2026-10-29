package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.Dao;
import co.edu.unicesar.marketradar.domain.Producto;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de productos (tabla {@code producto}).
 */
@Repository
public class ProductoDaoJdbc extends DaoJdbcBase implements Dao<Producto> {

    private static final String COLUMNAS = "id, nombre_normalizado, marca, creado_en";

    public ProductoDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Producto insertar(Producto producto) {
        long id = insertarYObtenerId("INSERT INTO producto (nombre_normalizado, marca) VALUES (?, ?)",
                sentencia -> {
                    sentencia.setString(1, producto.getNombreNormalizado());
                    sentencia.setString(2, producto.getMarca());
                });
        producto.setId(id);
        return producto;
    }

    @Override
    public Optional<Producto> buscarPorId(long id) {
        return consultarUno("SELECT " + COLUMNAS + " FROM producto WHERE id = ?",
                sentencia -> sentencia.setLong(1, id), this::mapear);
    }

    @Override
    public List<Producto> listarTodos() {
        return consultarLista("SELECT " + COLUMNAS + " FROM producto ORDER BY id", sentencia -> { }, this::mapear);
    }

    @Override
    public boolean actualizar(Producto producto) {
        return ejecutarActualizacion("UPDATE producto SET nombre_normalizado = ?, marca = ? WHERE id = ?",
                sentencia -> {
                    sentencia.setString(1, producto.getNombreNormalizado());
                    sentencia.setString(2, producto.getMarca());
                    sentencia.setLong(3, producto.getId());
                }) > 0;
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutarActualizacion("DELETE FROM producto WHERE id = ?", sentencia -> sentencia.setLong(1, id)) > 0;
    }

    private Producto mapear(ResultSet fila) throws SQLException {
        Producto producto = new Producto(fila.getString("nombre_normalizado"), fila.getString("marca"));
        producto.setId(fila.getLong("id"));
        producto.setCreadoEn(fila.getObject("creado_en", OffsetDateTime.class));
        return producto;
    }
}
