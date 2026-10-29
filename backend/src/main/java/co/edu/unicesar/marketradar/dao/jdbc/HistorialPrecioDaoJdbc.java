package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.HistorialPrecioDao;
import co.edu.unicesar.marketradar.domain.HistorialPrecio;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de historial de precios (tabla {@code historial_precio}).
 */
@Repository
public class HistorialPrecioDaoJdbc extends DaoJdbcBase implements HistorialPrecioDao {

    private static final String COLUMNAS = "id, oferta_id, precio, moneda, registrado_en";

    public HistorialPrecioDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public HistorialPrecio insertar(HistorialPrecio registro) {
        long id = insertarYObtenerId("INSERT INTO historial_precio (oferta_id, precio, moneda) VALUES (?, ?, ?)",
                sentencia -> {
                    sentencia.setLong(1, registro.getOfertaId());
                    sentencia.setBigDecimal(2, registro.getPrecio());
                    sentencia.setString(3, registro.getMoneda());
                });
        registro.setId(id);
        return registro;
    }

    @Override
    public Optional<HistorialPrecio> buscarPorId(long id) {
        return consultarUno("SELECT " + COLUMNAS + " FROM historial_precio WHERE id = ?",
                sentencia -> sentencia.setLong(1, id), this::mapear);
    }

    @Override
    public List<HistorialPrecio> listarPorOferta(long ofertaId) {
        return consultarLista("SELECT " + COLUMNAS + " FROM historial_precio WHERE oferta_id = ? ORDER BY registrado_en",
                sentencia -> sentencia.setLong(1, ofertaId), this::mapear);
    }

    @Override
    public List<HistorialPrecio> listarTodos() {
        return consultarLista("SELECT " + COLUMNAS + " FROM historial_precio ORDER BY registrado_en",
                sentencia -> { }, this::mapear);
    }

    @Override
    public boolean actualizar(HistorialPrecio registro) {
        return ejecutarActualizacion("UPDATE historial_precio SET precio = ?, moneda = ? WHERE id = ?",
                sentencia -> {
                    sentencia.setBigDecimal(1, registro.getPrecio());
                    sentencia.setString(2, registro.getMoneda());
                    sentencia.setLong(3, registro.getId());
                }) > 0;
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutarActualizacion("DELETE FROM historial_precio WHERE id = ?", sentencia -> sentencia.setLong(1, id)) > 0;
    }

    private HistorialPrecio mapear(ResultSet fila) throws SQLException {
        HistorialPrecio registro = new HistorialPrecio(fila.getLong("oferta_id"), fila.getBigDecimal("precio"),
                fila.getString("moneda"));
        registro.setId(fila.getLong("id"));
        registro.setRegistradoEn(fila.getObject("registrado_en", OffsetDateTime.class));
        return registro;
    }
}
