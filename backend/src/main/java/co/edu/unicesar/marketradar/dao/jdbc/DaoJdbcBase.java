package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.exception.AccesoDatosException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lógica común de los DAO JDBC: consultas parametrizadas, cierre de recursos con
 * try-with-resources y conversión de {@link SQLException} en {@link AccesoDatosException}.
 */
public abstract class DaoJdbcBase {

    private final DataSource dataSource;

    protected DaoJdbcBase(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Asigna los parámetros de una sentencia preparada. */
    @FunctionalInterface
    protected interface Parametros {
        void asignar(PreparedStatement sentencia) throws SQLException;
    }

    /** Convierte una fila del resultado en un objeto de dominio. */
    @FunctionalInterface
    protected interface MapeadorFila<T> {
        T mapear(ResultSet fila) throws SQLException;
    }

    protected <T> List<T> consultarLista(String sql, Parametros parametros, MapeadorFila<T> mapeador) {
        try (Connection conexion = dataSource.getConnection();
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            parametros.asignar(sentencia);
            try (ResultSet filas = sentencia.executeQuery()) {
                List<T> resultado = new ArrayList<>();
                while (filas.next()) {
                    resultado.add(mapeador.mapear(filas));
                }
                return resultado;
            }
        } catch (SQLException e) {
            throw new AccesoDatosException("Error al consultar: " + sql, e);
        }
    }

    protected <T> Optional<T> consultarUno(String sql, Parametros parametros, MapeadorFila<T> mapeador) {
        return consultarLista(sql, parametros, mapeador).stream().findFirst();
    }

    protected int ejecutarActualizacion(String sql, Parametros parametros) {
        try (Connection conexion = dataSource.getConnection();
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            parametros.asignar(sentencia);
            return sentencia.executeUpdate();
        } catch (SQLException e) {
            throw new AccesoDatosException("Error al actualizar: " + sql, e);
        }
    }

    protected long insertarYObtenerId(String sql, Parametros parametros) {
        try (Connection conexion = dataSource.getConnection();
             PreparedStatement sentencia = conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            parametros.asignar(sentencia);
            sentencia.executeUpdate();
            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new AccesoDatosException("No se generó el identificador", null);
                }
                return claves.getLong(1);
            }
        } catch (SQLException e) {
            throw new AccesoDatosException("Error al insertar: " + sql, e);
        }
    }

    protected static void asignarLongNulable(PreparedStatement sentencia, int indice, Long valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.BIGINT);
        } else {
            sentencia.setLong(indice, valor);
        }
    }

    protected static void asignarEnteroNulable(PreparedStatement sentencia, int indice, Integer valor) throws SQLException {
        if (valor == null) {
            sentencia.setNull(indice, Types.INTEGER);
        } else {
            sentencia.setInt(indice, valor);
        }
    }

    protected static Long leerLongNulable(ResultSet fila, String columna) throws SQLException {
        long valor = fila.getLong(columna);
        return fila.wasNull() ? null : valor;
    }
}
