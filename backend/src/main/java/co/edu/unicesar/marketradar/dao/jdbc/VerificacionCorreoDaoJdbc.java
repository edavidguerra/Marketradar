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
    public boolean guardarToken(String tokenHash, long usuarioId, String correo, OffsetDateTime venceEn) {
        // ON CONFLICT: el índice único parcial deja un solo envío PENDIENTE por persona (dos clics seguidos).
        return ejecutarActualizacion("INSERT INTO token_correo (token_hash, usuario_id, proposito, correo, vence_en, "
                        + "estado_envio) VALUES (?, ?, 'VERIFICAR_CORREO', ?, ?, 'PENDIENTE') ON CONFLICT DO NOTHING",
                sentencia -> {
                    sentencia.setString(1, tokenHash);
                    sentencia.setLong(2, usuarioId);
                    sentencia.setString(3, correo);
                    sentencia.setObject(4, venceEn);
                }) == 1;
    }

    @Override
    public void liberarEnviosColgados(long usuarioId, OffsetDateTime antesDe) {
        ejecutarActualizacion("UPDATE token_correo SET estado_envio = 'FALLIDO', ultimo_error = 'El envío se "
                        + "interrumpió' WHERE usuario_id = ? AND estado_envio = 'PENDIENTE' AND creado_en < ?",
                sentencia -> {
                    sentencia.setLong(1, usuarioId);
                    sentencia.setObject(2, antesDe);
                });
    }

    @Override
    public void marcarAceptado(String tokenHash, int intentos, String proveedor, String idMensaje) {
        ejecutarActualizacion("UPDATE token_correo SET estado_envio = 'ACEPTADO', aceptado_en = now(), intentos = ?, "
                        + "ultimo_error = NULL, proveedor_correo = ?, id_mensaje_proveedor = ? WHERE token_hash = ?",
                sentencia -> {
                    sentencia.setInt(1, intentos);
                    sentencia.setString(2, proveedor);
                    sentencia.setString(3, idMensaje);
                    sentencia.setString(4, tokenHash);
                });
    }

    @Override
    public void marcarFallido(String tokenHash, int intentos, String error) {
        ejecutarActualizacion("UPDATE token_correo SET estado_envio = 'FALLIDO', intentos = ?, ultimo_error = ? "
                        + "WHERE token_hash = ?",
                sentencia -> {
                    sentencia.setInt(1, intentos);
                    sentencia.setString(2, error == null ? null : error.length() > 300 ? error.substring(0, 300) : error);
                    sentencia.setString(3, tokenHash);
                });
    }

    @Override
    public int contarEnviados(long usuarioId, OffsetDateTime desde) {
        return consultarUno("SELECT count(*) FROM token_correo WHERE usuario_id = ? AND creado_en >= ? "
                        + "AND estado_envio <> 'FALLIDO'",
                sentencia -> {
                    sentencia.setLong(1, usuarioId);
                    sentencia.setObject(2, desde);
                }, fila -> fila.getInt(1)).orElse(0);
    }

    @Override
    public int contarSolicitudes(long usuarioId, OffsetDateTime desde) {
        return consultarUno("SELECT count(*) FROM token_correo WHERE usuario_id = ? AND creado_en >= ?",
                sentencia -> {
                    sentencia.setLong(1, usuarioId);
                    sentencia.setObject(2, desde);
                }, fila -> fila.getInt(1)).orElse(0);
    }

    @Override
    public Optional<Envio> ultimoEnvio(long usuarioId) {
        return consultarUno("SELECT estado_envio, creado_en, aceptado_en, vence_en, usado_en, ultimo_error "
                        + "FROM token_correo WHERE usuario_id = ? AND proposito = 'VERIFICAR_CORREO' "
                        + "ORDER BY creado_en DESC LIMIT 1",
                sentencia -> sentencia.setLong(1, usuarioId),
                fila -> new Envio(EstadoEnvio.valueOf(fila.getString("estado_envio")),
                        fila.getObject("creado_en", OffsetDateTime.class),
                        fila.getObject("aceptado_en", OffsetDateTime.class),
                        fila.getObject("vence_en", OffsetDateTime.class),
                        fila.getObject("usado_en", OffsetDateTime.class) != null, fila.getString("ultimo_error")));
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
