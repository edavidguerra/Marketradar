package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.EjecucionTareaDao;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.CRC32;

/**
 * Implementación JDBC del registro de rondas. El bloqueo usa {@code pg_try_advisory_lock} en una conexión propia que se
 * mantiene abierta mientras dura la ronda; al cerrarse la conexión, PostgreSQL suelta el bloqueo aunque el proceso se
 * haya caído.
 */
@Repository
public class EjecucionTareaDaoJdbc extends DaoJdbcBase implements EjecucionTareaDao {

    /** Las rondas se guardan este tiempo; las más viejas se borran al registrar una nueva. */
    static final int DIAS_GUARDADAS = 30;

    public EjecucionTareaDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public long iniciar(String tarea, Origen origen) {
        ejecutarActualizacion("DELETE FROM ejecucion_tarea WHERE iniciada_en < now() - make_interval(days => ?)",
                sentencia -> sentencia.setInt(1, DIAS_GUARDADAS));
        return insertarYObtenerId("INSERT INTO ejecucion_tarea (tarea, origen) VALUES (?, ?)", sentencia -> {
            sentencia.setString(1, tarea);
            sentencia.setString(2, origen.name());
        });
    }

    @Override
    public void terminar(long id, Estado estado, int revisados, int consultas, int pospuestos, int avisos, int correos,
                         String detalle) {
        ejecutarActualizacion("UPDATE ejecucion_tarea SET estado = ?, terminada_en = now(), revisados = ?, consultas = ?, "
                + "pospuestos = ?, avisos_nuevos = ?, correos_aceptados = ?, detalle = ? WHERE id = ?", sentencia -> {
                    sentencia.setString(1, estado.name());
                    sentencia.setInt(2, revisados);
                    sentencia.setInt(3, consultas);
                    sentencia.setInt(4, pospuestos);
                    sentencia.setInt(5, avisos);
                    sentencia.setInt(6, correos);
                    sentencia.setString(7, recortar(detalle));
                    sentencia.setLong(8, id);
                });
    }

    @Override
    public void omitida(String tarea, Origen origen, String motivo) {
        ejecutarActualizacion("INSERT INTO ejecucion_tarea (tarea, origen, estado, terminada_en, detalle) "
                + "VALUES (?, ?, 'OMITIDA', now(), ?)", sentencia -> {
                    sentencia.setString(1, tarea);
                    sentencia.setString(2, origen.name());
                    sentencia.setString(3, recortar(motivo));
                });
    }

    @Override
    public List<Ejecucion> recientes(String tarea, int limite) {
        return consultarLista("SELECT * FROM ejecucion_tarea WHERE tarea = ? ORDER BY iniciada_en DESC, id DESC LIMIT ?",
                sentencia -> {
                    sentencia.setString(1, tarea);
                    sentencia.setInt(2, limite);
                }, EjecucionTareaDaoJdbc::mapear);
    }

    @Override
    public <T> Optional<T> conBloqueo(String tarea, Supplier<T> trabajo) {
        long clave = clave(tarea);
        try (Connection conexion = fuenteDatos().getConnection()) {
            if (!intentar(conexion, "SELECT pg_try_advisory_lock(?)", clave)) {
                return Optional.empty();
            }
            try {
                return Optional.ofNullable(trabajo.get());
            } finally {
                intentar(conexion, "SELECT pg_advisory_unlock(?)", clave);
            }
        } catch (SQLException e) {
            throw new AccesoDatosException("No se pudo tomar el bloqueo de la tarea " + tarea, e);
        }
    }

    private static boolean intentar(Connection conexion, String sql, long clave) throws SQLException {
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setLong(1, clave);
            try (ResultSet fila = sentencia.executeQuery()) {
                return fila.next() && fila.getBoolean(1);
            }
        }
    }

    /** Número fijo por tarea para el bloqueo asesor. */
    static long clave(String tarea) {
        CRC32 crc = new CRC32();
        crc.update(("marketradar:" + tarea).getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    private static String recortar(String texto) {
        return texto == null ? null : texto.length() > 300 ? texto.substring(0, 300) : texto;
    }

    private static Ejecucion mapear(ResultSet fila) throws SQLException {
        return new Ejecucion(fila.getLong("id"), fila.getString("tarea"), Origen.valueOf(fila.getString("origen")),
                Estado.valueOf(fila.getString("estado")), fila.getObject("iniciada_en", OffsetDateTime.class),
                fila.getObject("terminada_en", OffsetDateTime.class), fila.getInt("revisados"), fila.getInt("consultas"),
                fila.getInt("pospuestos"), fila.getInt("avisos_nuevos"), fila.getInt("correos_aceptados"),
                fila.getString("detalle"));
    }
}
