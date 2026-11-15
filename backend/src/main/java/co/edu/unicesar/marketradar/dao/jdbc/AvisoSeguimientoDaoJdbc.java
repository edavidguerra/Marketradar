package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.AvisoSeguimientoDao;
import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de avisos de seguimiento (tabla {@code aviso_seguimiento}).
 */
@Repository
public class AvisoSeguimientoDaoJdbc extends DaoJdbcBase implements AvisoSeguimientoDao {

    private static final String COLUMNAS = "a.id, a.usuario_id, a.oferta_id, a.tipo, a.clave_evento, a.correo_destino, "
            + "a.precio_anterior, a.precio_actual, a.moneda, a.comprobado_en, a.estado, a.intentos, a.ultimo_error, "
            + "a.creado_en, a.enviado_en";

    private static final String DATOS_CORREO = "s.estado AS estado_seguimiento, s.tipo_aviso, s.precio_objetivo, "
            + "u.correo AS correo_actual, u.correo_verificado_en, u.nombres, o.titulo, o.url_destino, o.vendedor, "
            + "m.codigo AS codigo_marketplace";

    private static final String TABLAS_CORREO = "seguimiento_oferta s, usuario u, oferta o, marketplace m";

    private static final String UNIONES_CORREO = "s.usuario_id = a.usuario_id AND s.oferta_id = a.oferta_id "
            + "AND u.id = a.usuario_id AND o.id = a.oferta_id AND m.id = o.marketplace_id";

    public AvisoSeguimientoDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Optional<Long> insertarSiEsNuevo(AvisoSeguimiento aviso) {
        return consultarUno("INSERT INTO aviso_seguimiento (usuario_id, oferta_id, tipo, clave_evento, correo_destino, "
                        + "precio_anterior, precio_actual, moneda, comprobado_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (clave_evento) DO NOTHING RETURNING id",
                sentencia -> {
                    sentencia.setLong(1, aviso.getUsuarioId());
                    sentencia.setLong(2, aviso.getOfertaId());
                    sentencia.setString(3, aviso.getTipo().name());
                    sentencia.setString(4, aviso.getClaveEvento());
                    sentencia.setString(5, aviso.getCorreoDestino());
                    sentencia.setBigDecimal(6, aviso.getPrecioAnterior());
                    sentencia.setBigDecimal(7, aviso.getPrecioActual());
                    sentencia.setString(8, aviso.getMoneda());
                    sentencia.setObject(9, aviso.getComprobadoEn());
                }, fila -> fila.getLong("id"));
    }

    @Override
    public List<Pendiente> tomarPendientes(int limite, int minutosApartado) {
        String sql = """
                WITH elegidos AS (
                    SELECT id FROM aviso_seguimiento WHERE estado = 'PENDIENTE' AND proximo_intento <= now()
                    ORDER BY proximo_intento LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE aviso_seguimiento a SET proximo_intento = now() + make_interval(mins => ?)
                FROM elegidos e, %s
                WHERE a.id = e.id AND %s
                RETURNING %s, %s
                """.formatted(TABLAS_CORREO, UNIONES_CORREO, COLUMNAS, DATOS_CORREO);
        return consultarLista(sql, sentencia -> {
            sentencia.setInt(1, limite);
            sentencia.setInt(2, minutosApartado);
        }, AvisoSeguimientoDaoJdbc::mapearPendiente);
    }

    @Override
    public Optional<Pendiente> tomar(long id, int minutosApartado) {
        String sql = """
                UPDATE aviso_seguimiento a SET proximo_intento = now() + make_interval(mins => ?)
                FROM %s
                WHERE a.id = ? AND a.estado = 'PENDIENTE' AND a.proximo_intento <= now() AND %s
                RETURNING %s, %s
                """.formatted(TABLAS_CORREO, UNIONES_CORREO, COLUMNAS, DATOS_CORREO);
        return consultarUno(sql, sentencia -> {
            sentencia.setInt(1, minutosApartado);
            sentencia.setLong(2, id);
        }, AvisoSeguimientoDaoJdbc::mapearPendiente);
    }

    @Override
    public void marcarEnviado(long id) {
        ejecutarActualizacion("UPDATE aviso_seguimiento SET estado = 'ENVIADO', enviado_en = now(), "
                + "intentos = intentos + 1, ultimo_error = NULL WHERE id = ? AND estado = 'PENDIENTE'",
                sentencia -> sentencia.setLong(1, id));
    }

    @Override
    public void marcarAceptado(long id, co.edu.unicesar.marketradar.correo.ResultadoEnvio resultado) {
        ejecutarActualizacion("UPDATE aviso_seguimiento SET estado = 'ENVIADO', enviado_en = now(), "
                        + "intentos = intentos + 1, ultimo_error = NULL, proveedor_correo = ?, id_mensaje_proveedor = ? "
                        + "WHERE id = ? AND estado = 'PENDIENTE'",
                sentencia -> {
                    sentencia.setString(1, resultado.proveedor());
                    sentencia.setString(2, resultado.idMensaje());
                    sentencia.setLong(3, id);
                });
    }

    @Override
    public void registrarFallo(long id, String error, boolean contarIntento, OffsetDateTime proximoIntento) {
        ejecutarActualizacion("UPDATE aviso_seguimiento SET ultimo_error = ?, "
                        + "intentos = intentos + CASE WHEN ? THEN 1 ELSE 0 END, "
                        + "estado = CASE WHEN CAST(? AS TIMESTAMPTZ) IS NULL THEN 'FALLIDO' ELSE 'PENDIENTE' END, "
                        + "proximo_intento = COALESCE(CAST(? AS TIMESTAMPTZ), proximo_intento) WHERE id = ? AND estado = 'PENDIENTE'",
                sentencia -> {
                    sentencia.setString(1, recortar(error));
                    sentencia.setBoolean(2, contarIntento);
                    sentencia.setObject(3, proximoIntento, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
                    sentencia.setObject(4, proximoIntento, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
                    sentencia.setLong(5, id);
                });
    }

    @Override
    public void descartar(long id, String motivo) {
        ejecutarActualizacion("UPDATE aviso_seguimiento SET estado = 'DESCARTADO', ultimo_error = ? "
                        + "WHERE id = ? AND estado = 'PENDIENTE'",
                sentencia -> {
                    sentencia.setString(1, recortar(motivo));
                    sentencia.setLong(2, id);
                });
    }

    @Override
    public int descartarPendientes(long usuarioId, long ofertaId, String motivo) {
        return ejecutarActualizacion("UPDATE aviso_seguimiento SET estado = 'DESCARTADO', ultimo_error = ? "
                        + "WHERE usuario_id = ? AND oferta_id = ? AND estado = 'PENDIENTE'",
                sentencia -> {
                    sentencia.setString(1, recortar(motivo));
                    sentencia.setLong(2, usuarioId);
                    sentencia.setLong(3, ofertaId);
                });
    }

    @Override
    public List<AvisoSeguimiento> recientes(long usuarioId, long ofertaId, int limite) {
        return consultarLista("SELECT " + COLUMNAS + " FROM aviso_seguimiento a WHERE a.usuario_id = ? "
                        + "AND a.oferta_id = ? ORDER BY a.creado_en DESC LIMIT ?",
                sentencia -> {
                    sentencia.setLong(1, usuarioId);
                    sentencia.setLong(2, ofertaId);
                    sentencia.setInt(3, limite);
                }, AvisoSeguimientoDaoJdbc::mapear);
    }

    private static String recortar(String texto) {
        return texto == null || texto.length() <= 300 ? texto : texto.substring(0, 300);
    }

    private static Pendiente mapearPendiente(ResultSet fila) throws SQLException {
        return new Pendiente(mapear(fila), fila.getString("estado_seguimiento"), fila.getString("correo_actual"),
                fila.getObject("correo_verificado_en") != null, fila.getString("nombres"), fila.getString("titulo"),
                fila.getString("url_destino"), fila.getString("vendedor"), fila.getString("codigo_marketplace"),
                fila.getString("tipo_aviso"), fila.getBigDecimal("precio_objetivo"));
    }

    private static AvisoSeguimiento mapear(ResultSet fila) throws SQLException {
        AvisoSeguimiento aviso = new AvisoSeguimiento();
        aviso.setId(fila.getLong("id"));
        aviso.setUsuarioId(fila.getLong("usuario_id"));
        aviso.setOfertaId(fila.getLong("oferta_id"));
        aviso.setTipo(AvisoSeguimiento.Tipo.valueOf(fila.getString("tipo")));
        aviso.setClaveEvento(fila.getString("clave_evento"));
        aviso.setCorreoDestino(fila.getString("correo_destino"));
        aviso.setPrecioAnterior(fila.getBigDecimal("precio_anterior"));
        aviso.setPrecioActual(fila.getBigDecimal("precio_actual"));
        aviso.setMoneda(fila.getString("moneda"));
        aviso.setComprobadoEn(fila.getObject("comprobado_en", OffsetDateTime.class));
        aviso.setEstado(AvisoSeguimiento.Estado.valueOf(fila.getString("estado")));
        aviso.setIntentos(fila.getShort("intentos"));
        aviso.setUltimoError(fila.getString("ultimo_error"));
        aviso.setCreadoEn(fila.getObject("creado_en", OffsetDateTime.class));
        aviso.setEnviadoEn(fila.getObject("enviado_en", OffsetDateTime.class));
        return aviso;
    }
}
