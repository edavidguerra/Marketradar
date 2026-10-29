package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.MarketplaceDao;
import co.edu.unicesar.marketradar.domain.Marketplace;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de marketplaces (tabla {@code marketplace}).
 */
@Repository
public class MarketplaceDaoJdbc extends DaoJdbcBase implements MarketplaceDao {

    private static final String COLUMNAS = "id, codigo, nombre, sitio_web, activo";

    public MarketplaceDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Marketplace insertar(Marketplace marketplace) {
        long id = insertarYObtenerId(
                "INSERT INTO marketplace (codigo, nombre, sitio_web, activo) VALUES (?, ?, ?, ?)",
                sentencia -> {
                    sentencia.setString(1, marketplace.getCodigo());
                    sentencia.setString(2, marketplace.getNombre());
                    sentencia.setString(3, marketplace.getSitioWeb());
                    sentencia.setBoolean(4, marketplace.isActivo());
                });
        marketplace.setId((short) id);
        return marketplace;
    }

    @Override
    public Optional<Marketplace> buscarPorId(long id) {
        return consultarUno("SELECT " + COLUMNAS + " FROM marketplace WHERE id = ?",
                sentencia -> sentencia.setShort(1, (short) id), this::mapear);
    }

    @Override
    public Optional<Marketplace> buscarPorCodigo(String codigo) {
        return consultarUno("SELECT " + COLUMNAS + " FROM marketplace WHERE codigo = ?",
                sentencia -> sentencia.setString(1, codigo), this::mapear);
    }

    @Override
    public List<Marketplace> listarTodos() {
        return consultarLista("SELECT " + COLUMNAS + " FROM marketplace ORDER BY nombre", sentencia -> { }, this::mapear);
    }

    @Override
    public boolean actualizar(Marketplace marketplace) {
        return ejecutarActualizacion(
                "UPDATE marketplace SET codigo = ?, nombre = ?, sitio_web = ?, activo = ? WHERE id = ?",
                sentencia -> {
                    sentencia.setString(1, marketplace.getCodigo());
                    sentencia.setString(2, marketplace.getNombre());
                    sentencia.setString(3, marketplace.getSitioWeb());
                    sentencia.setBoolean(4, marketplace.isActivo());
                    sentencia.setShort(5, marketplace.getId());
                }) > 0;
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutarActualizacion("DELETE FROM marketplace WHERE id = ?",
                sentencia -> sentencia.setShort(1, (short) id)) > 0;
    }

    private Marketplace mapear(ResultSet fila) throws SQLException {
        return new Marketplace(fila.getShort("id"), fila.getString("codigo"), fila.getString("nombre"),
                fila.getString("sitio_web"), fila.getBoolean("activo"));
    }
}
