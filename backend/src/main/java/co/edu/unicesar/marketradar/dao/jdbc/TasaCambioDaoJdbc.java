package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.TasaCambioDao;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de tasas de cambio (tabla {@code tasa_cambio}).
 */
@Repository
public class TasaCambioDaoJdbc extends DaoJdbcBase implements TasaCambioDao {

    public TasaCambioDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public void guardar(TasaCambio tasa) {
        ejecutarActualizacion("INSERT INTO tasa_cambio (moneda_origen, valor, vigente_desde, fuente) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (moneda_origen, moneda_destino, vigente_desde) "
                        + "DO UPDATE SET valor = EXCLUDED.valor, fuente = EXCLUDED.fuente, consultada_en = now()",
                sentencia -> {
                    sentencia.setString(1, tasa.monedaOrigen());
                    sentencia.setBigDecimal(2, tasa.valor());
                    sentencia.setObject(3, tasa.vigenteDesde());
                    sentencia.setString(4, tasa.fuente());
                });
    }

    @Override
    public Optional<TasaCambio> buscarVigente(String moneda) {
        return consultarUno("SELECT moneda_origen, valor, vigente_desde, fuente FROM tasa_cambio "
                        + "WHERE moneda_origen = ? ORDER BY vigente_desde DESC, consultada_en DESC LIMIT 1",
                sentencia -> sentencia.setString(1, moneda), this::mapear);
    }

    private TasaCambio mapear(ResultSet fila) throws SQLException {
        return new TasaCambio(fila.getString("moneda_origen").strip(), fila.getBigDecimal("valor"),
                fila.getObject("vigente_desde", LocalDate.class), fila.getString("fuente"));
    }
}
