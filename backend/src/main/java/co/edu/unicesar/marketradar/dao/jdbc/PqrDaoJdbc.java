package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.PqrDao;
import co.edu.unicesar.marketradar.domain.EstadoPqr;
import co.edu.unicesar.marketradar.domain.Pqr;
import co.edu.unicesar.marketradar.domain.TipoPqr;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de PQR (tabla {@code pqr}, unida con {@code usuario} para el nombre).
 */
@Repository
public class PqrDaoJdbc extends DaoJdbcBase implements PqrDao {

    private static final String CONSULTA = "SELECT p.id, p.usuario_id, p.tipo, p.asunto, p.descripcion, p.estado, "
            + "p.respuesta, p.respondida_por, p.creada_en, p.respondida_en, "
            + "u.nombres || ' ' || u.apellidos AS nombre_usuario, u.correo AS correo_usuario "
            + "FROM pqr p JOIN usuario u ON u.id = p.usuario_id ";

    public PqrDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Pqr insertar(Pqr pqr) {
        long id = insertarYObtenerId("INSERT INTO pqr (usuario_id, tipo, asunto, descripcion, estado) "
                        + "VALUES (?, ?, ?, ?, ?)",
                sentencia -> {
                    sentencia.setLong(1, pqr.getUsuarioId());
                    sentencia.setString(2, pqr.getTipo().name());
                    sentencia.setString(3, pqr.getAsunto());
                    sentencia.setString(4, pqr.getDescripcion());
                    sentencia.setString(5, pqr.getEstado().name());
                });
        pqr.setId(id);
        pqr.setCreadaEn(OffsetDateTime.now());
        return pqr;
    }

    @Override
    public Optional<Pqr> buscarPorId(long id) {
        return consultarUno(CONSULTA + "WHERE p.id = ?", sentencia -> sentencia.setLong(1, id), this::mapear);
    }

    @Override
    public List<Pqr> listarPorUsuario(long usuarioId) {
        return consultarLista(CONSULTA + "WHERE p.usuario_id = ? ORDER BY p.creada_en DESC",
                sentencia -> sentencia.setLong(1, usuarioId), this::mapear);
    }

    @Override
    public List<Pqr> listarPorEstado(EstadoPqr estado) {
        if (estado == null) {
            return listarTodos();
        }
        return consultarLista(CONSULTA + "WHERE p.estado = ? ORDER BY p.creada_en",
                sentencia -> sentencia.setString(1, estado.name()), this::mapear);
    }

    @Override
    public List<Pqr> listarTodos() {
        return consultarLista(CONSULTA + "ORDER BY (p.estado = 'RESPONDIDA'), p.creada_en",
                sentencia -> { }, this::mapear);
    }

    /**
     * Guarda el estado y la respuesta. El usuario, el tipo y el texto radicado no se modifican.
     */
    @Override
    public boolean actualizar(Pqr pqr) {
        return ejecutarActualizacion("UPDATE pqr SET estado = ?, respuesta = ?, respondida_por = ?, respondida_en = ? "
                        + "WHERE id = ?",
                sentencia -> {
                    sentencia.setString(1, pqr.getEstado().name());
                    sentencia.setString(2, pqr.getRespuesta());
                    asignarLongNulable(sentencia, 3, pqr.getRespondidaPor());
                    sentencia.setObject(4, pqr.getRespondidaEn());
                    sentencia.setLong(5, pqr.getId());
                }) > 0;
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutarActualizacion("DELETE FROM pqr WHERE id = ?", sentencia -> sentencia.setLong(1, id)) > 0;
    }

    private Pqr mapear(ResultSet fila) throws SQLException {
        Pqr pqr = new Pqr(fila.getLong("usuario_id"), TipoPqr.valueOf(fila.getString("tipo")),
                fila.getString("asunto"), fila.getString("descripcion"));
        pqr.setId(fila.getLong("id"));
        pqr.setEstado(EstadoPqr.valueOf(fila.getString("estado")));
        pqr.setRespuesta(fila.getString("respuesta"));
        pqr.setRespondidaPor(leerLongNulable(fila, "respondida_por"));
        pqr.setCreadaEn(fila.getObject("creada_en", OffsetDateTime.class));
        pqr.setRespondidaEn(fila.getObject("respondida_en", OffsetDateTime.class));
        pqr.setNombreUsuario(fila.getString("nombre_usuario"));
        pqr.setCorreoUsuario(fila.getString("correo_usuario"));
        return pqr;
    }
}
