package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.CredencialDao;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de credenciales (tabla {@code credencial_marketplace}).
 */
@Repository
public class CredencialDaoJdbc extends DaoJdbcBase implements CredencialDao {

    public CredencialDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Optional<CredencialGuardada> buscar(String codigo) {
        return consultarUno("SELECT valor, valor_inicial_hash FROM credencial_marketplace WHERE codigo = ?",
                sentencia -> sentencia.setString(1, codigo),
                fila -> new CredencialGuardada(fila.getString("valor"), fila.getString("valor_inicial_hash")));
    }

    @Override
    public void guardar(String codigo, CredencialGuardada credencial) {
        ejecutarActualizacion("INSERT INTO credencial_marketplace (codigo, valor, valor_inicial_hash) VALUES (?, ?, ?) "
                        + "ON CONFLICT (codigo) DO UPDATE SET valor = EXCLUDED.valor, "
                        + "valor_inicial_hash = EXCLUDED.valor_inicial_hash, actualizado_en = now()",
                sentencia -> {
                    sentencia.setString(1, codigo);
                    sentencia.setString(2, credencial.valor());
                    sentencia.setString(3, credencial.valorInicialHash());
                });
    }
}
