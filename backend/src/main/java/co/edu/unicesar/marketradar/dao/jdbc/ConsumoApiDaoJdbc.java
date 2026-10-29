package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.Dao;
import co.edu.unicesar.marketradar.domain.ConsumoApi;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de consumo de APIs (tabla {@code consumo_api}).
 */
@Repository
public class ConsumoApiDaoJdbc extends DaoJdbcBase implements Dao<ConsumoApi> {

    private static final String COLUMNAS = "id, marketplace_id, exitoso, codigo_http, duracion_ms, registrado_en";

    public ConsumoApiDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public ConsumoApi insertar(ConsumoApi consumo) {
        long id = insertarYObtenerId(
                "INSERT INTO consumo_api (marketplace_id, exitoso, codigo_http, duracion_ms) VALUES (?, ?, ?, ?)",
                sentencia -> {
                    sentencia.setShort(1, consumo.getMarketplaceId());
                    sentencia.setBoolean(2, consumo.isExitoso());
                    asignarEnteroNulable(sentencia, 3, consumo.getCodigoHttp());
                    sentencia.setInt(4, consumo.getDuracionMs());
                });
        consumo.setId(id);
        return consumo;
    }

    @Override
    public Optional<ConsumoApi> buscarPorId(long id) {
        return consultarUno("SELECT " + COLUMNAS + " FROM consumo_api WHERE id = ?",
                sentencia -> sentencia.setLong(1, id), this::mapear);
    }

    @Override
    public List<ConsumoApi> listarTodos() {
        return consultarLista("SELECT " + COLUMNAS + " FROM consumo_api ORDER BY registrado_en DESC",
                sentencia -> { }, this::mapear);
    }

    @Override
    public boolean actualizar(ConsumoApi consumo) {
        return ejecutarActualizacion(
                "UPDATE consumo_api SET exitoso = ?, codigo_http = ?, duracion_ms = ? WHERE id = ?",
                sentencia -> {
                    sentencia.setBoolean(1, consumo.isExitoso());
                    asignarEnteroNulable(sentencia, 2, consumo.getCodigoHttp());
                    sentencia.setInt(3, consumo.getDuracionMs());
                    sentencia.setLong(4, consumo.getId());
                }) > 0;
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutarActualizacion("DELETE FROM consumo_api WHERE id = ?", sentencia -> sentencia.setLong(1, id)) > 0;
    }

    private ConsumoApi mapear(ResultSet fila) throws SQLException {
        int codigo = fila.getInt("codigo_http");
        Integer codigoHttp = fila.wasNull() ? null : codigo;
        ConsumoApi consumo = new ConsumoApi(fila.getShort("marketplace_id"), fila.getBoolean("exitoso"), codigoHttp,
                fila.getInt("duracion_ms"));
        consumo.setId(fila.getLong("id"));
        consumo.setRegistradoEn(fila.getObject("registrado_en", OffsetDateTime.class));
        return consumo;
    }
}
