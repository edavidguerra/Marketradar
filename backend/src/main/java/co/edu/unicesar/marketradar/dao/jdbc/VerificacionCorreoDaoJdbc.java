package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.VerificacionCorreoDao;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Implementación JDBC de la verificación de correo.
 */
@Repository
public class VerificacionCorreoDaoJdbc extends DaoJdbcBase implements VerificacionCorreoDao {

    public VerificacionCorreoDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Optional<OffsetDateTime> verificadoEn(long usuarioId) {
        return consultarUno("SELECT correo_verificado_en FROM usuario WHERE id = ? AND correo_verificado_en IS NOT NULL",
                sentencia -> sentencia.setLong(1, usuarioId),
                fila -> fila.getObject("correo_verificado_en", OffsetDateTime.class));
    }

    @Override
    public void guardarToken(String tokenHash, long usuarioId, String correo, OffsetDateTime venceEn) {
        ejecutarActualizacion("INSERT INTO token_correo (token_hash, usuario_id, proposito, correo, vence_en) "
                        + "VALUES (?, ?, 'VERIFICAR_CORREO', ?, ?)",
                sentencia -> {
                    sentencia.setString(1, tokenHash);
                    sentencia.setLong(2, usuarioId);
                    sentencia.setString(3, correo);
                    sentencia.setObject(4, venceEn);
                });
    }

    @Override
    public int contarEnviados(long usuarioId, OffsetDateTime desde) {
        return consultarUno("SELECT count(*) FROM token_correo WHERE usuario_id = ? AND creado_en >= ?",
                sentencia -> {
                    sentencia.setLong(1, usuarioId);
                    sentencia.setObject(2, desde);
                }, fila -> fila.getInt(1)).orElse(0);
    }

    /** Una sola consulta: usa el enlace y marca el correo, solo si el correo de la cuenta no cambió. */
    @Override
    public Optional<Long> usarToken(String tokenHash) {
        return consultarUno("""
                WITH usado AS (
                    UPDATE token_correo SET usado_en = now()
                    WHERE token_hash = ? AND proposito = 'VERIFICAR_CORREO' AND usado_en IS NULL AND vence_en > now()
                    RETURNING usuario_id, correo
                )
                UPDATE usuario u SET correo_verificado_en = now(), actualizado_en = now()
                FROM usado WHERE u.id = usado.usuario_id AND u.correo = usado.correo
                RETURNING u.id
                """, sentencia -> sentencia.setString(1, tokenHash), fila -> fila.getLong(1));
    }
}
