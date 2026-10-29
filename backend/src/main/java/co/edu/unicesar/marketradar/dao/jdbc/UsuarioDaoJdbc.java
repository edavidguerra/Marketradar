package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.domain.Rol;
import co.edu.unicesar.marketradar.domain.TipoDocumento;
import co.edu.unicesar.marketradar.domain.Usuario;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de usuarios (tabla {@code usuario}).
 */
@Repository
public class UsuarioDaoJdbc extends DaoJdbcBase implements UsuarioDao {

    private static final String COLUMNAS = "id, correo, clave_hash, rol, nombres, apellidos, tipo_documento, "
            + "numero_documento, fecha_nacimiento, celular, ciudad, activo, creado_en";

    public UsuarioDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Usuario insertar(Usuario usuario) {
        long id = insertarYObtenerId("INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, tipo_documento, "
                        + "numero_documento, fecha_nacimiento, celular, ciudad, activo) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                sentencia -> {
                    sentencia.setString(1, usuario.getCorreo());
                    sentencia.setString(2, usuario.getClaveHash());
                    sentencia.setString(3, usuario.getRol().name());
                    asignarPerfil(sentencia, 4, usuario);
                    sentencia.setBoolean(11, usuario.isActivo());
                });
        usuario.setId(id);
        return usuario;
    }

    @Override
    public Optional<Usuario> buscarPorId(long id) {
        return consultarUno("SELECT " + COLUMNAS + " FROM usuario WHERE id = ?",
                sentencia -> sentencia.setLong(1, id), this::mapear);
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return consultarUno("SELECT " + COLUMNAS + " FROM usuario WHERE correo = ?",
                sentencia -> sentencia.setString(1, correo), this::mapear);
    }

    @Override
    public boolean existeDocumento(TipoDocumento tipo, String numero, Long excluirId) {
        return consultarUno("SELECT 1 FROM usuario WHERE tipo_documento = ? AND numero_documento = ? "
                        + "AND (?::BIGINT IS NULL OR id <> ?::BIGINT)",
                sentencia -> {
                    sentencia.setString(1, tipo.name());
                    sentencia.setString(2, numero);
                    asignarLongNulable(sentencia, 3, excluirId);
                    asignarLongNulable(sentencia, 4, excluirId);
                }, fila -> Boolean.TRUE).isPresent();
    }

    @Override
    public List<Usuario> listarTodos() {
        return consultarLista("SELECT " + COLUMNAS + " FROM usuario ORDER BY apellidos, nombres",
                sentencia -> { }, this::mapear);
    }

    /**
     * Actualiza los datos de perfil y el estado. El correo, el rol y la contraseña no cambian aquí.
     */
    @Override
    public boolean actualizar(Usuario usuario) {
        return ejecutarActualizacion("UPDATE usuario SET nombres = ?, apellidos = ?, tipo_documento = ?, "
                        + "numero_documento = ?, fecha_nacimiento = ?, celular = ?, ciudad = ?, activo = ?, "
                        + "actualizado_en = now() WHERE id = ?",
                sentencia -> {
                    asignarPerfil(sentencia, 1, usuario);
                    sentencia.setBoolean(8, usuario.isActivo());
                    sentencia.setLong(9, usuario.getId());
                }) > 0;
    }

    @Override
    public boolean actualizarClave(long id, String claveHash) {
        return ejecutarActualizacion("UPDATE usuario SET clave_hash = ?, actualizado_en = now() WHERE id = ?",
                sentencia -> {
                    sentencia.setString(1, claveHash);
                    sentencia.setLong(2, id);
                }) > 0;
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutarActualizacion("DELETE FROM usuario WHERE id = ?", sentencia -> sentencia.setLong(1, id)) > 0;
    }

    /** Asigna los siete campos de perfil a partir de la posición indicada. */
    private static void asignarPerfil(PreparedStatement sentencia, int desde, Usuario usuario) throws SQLException {
        sentencia.setString(desde, usuario.getNombres());
        sentencia.setString(desde + 1, usuario.getApellidos());
        sentencia.setString(desde + 2, usuario.getTipoDocumento() == null ? null : usuario.getTipoDocumento().name());
        sentencia.setString(desde + 3, usuario.getNumeroDocumento());
        if (usuario.getFechaNacimiento() == null) {
            sentencia.setNull(desde + 4, Types.DATE);
        } else {
            sentencia.setObject(desde + 4, usuario.getFechaNacimiento());
        }
        sentencia.setString(desde + 5, usuario.getCelular());
        sentencia.setString(desde + 6, usuario.getCiudad());
    }

    private Usuario mapear(ResultSet fila) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.setId(fila.getLong("id"));
        usuario.setCorreo(fila.getString("correo"));
        usuario.setClaveHash(fila.getString("clave_hash"));
        usuario.setRol(Rol.valueOf(fila.getString("rol")));
        usuario.setNombres(fila.getString("nombres"));
        usuario.setApellidos(fila.getString("apellidos"));
        String tipoDocumento = fila.getString("tipo_documento");
        usuario.setTipoDocumento(tipoDocumento == null ? null : TipoDocumento.valueOf(tipoDocumento));
        usuario.setNumeroDocumento(fila.getString("numero_documento"));
        usuario.setFechaNacimiento(fila.getObject("fecha_nacimiento", LocalDate.class));
        usuario.setCelular(fila.getString("celular"));
        usuario.setCiudad(fila.getString("ciudad"));
        usuario.setActivo(fila.getBoolean("activo"));
        usuario.setCreadoEn(fila.getObject("creado_en", OffsetDateTime.class));
        return usuario;
    }
}
