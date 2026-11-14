package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.SeguimientoDao;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.Seguimiento;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de seguimientos (tabla {@code seguimiento_oferta}).
 */
@Repository
public class SeguimientoDaoJdbc extends DaoJdbcBase implements SeguimientoDao {

    private static final String COLUMNAS = "s.usuario_id, s.oferta_id, s.tipo_aviso, s.precio_objetivo, s.estado, "
            + "s.precio_referencia, s.moneda_referencia, s.precio_original_referencia, s.moneda_original_referencia, "
            + "s.vendedor_referencia, s.condicion_referencia, s.variante_referencia, s.referencia_desde, "
            + "s.termino_busqueda, s.ultimo_precio, s.ultima_comprobacion, s.resultado_comprobacion, "
            + "s.fallos_consecutivos, s.proxima_comprobacion, s.objetivo_avisado, s.token_cancelacion, s.version, "
            + "s.creado_en";

    private static final String COLUMNAS_OFERTA = "o.id AS o_id, o.marketplace_id AS o_marketplace_id, "
            + "o.id_externo AS o_id_externo, o.titulo AS o_titulo, o.url_destino AS o_url_destino, "
            + "o.url_imagen AS o_url_imagen, o.precio_actual AS o_precio_actual, o.moneda AS o_moneda, "
            + "o.costo_envio AS o_costo_envio, o.condicion AS o_condicion, o.vendedor AS o_vendedor, "
            + "o.consultado_en AS o_consultado_en, o.detalles AS o_detalles, m.codigo AS codigo_marketplace";

    public SeguimientoDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Optional<Seguimiento> buscar(long usuarioId, long ofertaId) {
        return consultarUno("SELECT " + COLUMNAS + " FROM seguimiento_oferta s WHERE s.usuario_id = ? AND s.oferta_id = ?",
                sentencia -> {
                    sentencia.setLong(1, usuarioId);
                    sentencia.setLong(2, ofertaId);
                }, SeguimientoDaoJdbc::mapear);
    }

    @Override
    public Seguimiento activar(Seguimiento seguimiento) {
        String sql = """
                INSERT INTO seguimiento_oferta AS s (usuario_id, oferta_id, tipo_aviso, precio_objetivo, estado,
                    precio_referencia, moneda_referencia, precio_original_referencia, moneda_original_referencia,
                    vendedor_referencia, condicion_referencia, variante_referencia, referencia_desde, termino_busqueda,
                    ultimo_precio, resultado_comprobacion, fallos_consecutivos, proxima_comprobacion, objetivo_avisado,
                    token_cancelacion, actualizado_en)
                VALUES (?, ?, ?, ?, 'ACTIVO', ?, ?, ?, ?, ?, ?, ?, now(), ?, ?, 'SIN_COMPROBAR', 0, ?, FALSE, ?, now())
                ON CONFLICT (usuario_id, oferta_id) DO UPDATE SET
                    tipo_aviso = EXCLUDED.tipo_aviso, precio_objetivo = EXCLUDED.precio_objetivo, estado = 'ACTIVO',
                    precio_referencia = EXCLUDED.precio_referencia, moneda_referencia = EXCLUDED.moneda_referencia,
                    precio_original_referencia = EXCLUDED.precio_original_referencia,
                    moneda_original_referencia = EXCLUDED.moneda_original_referencia,
                    vendedor_referencia = EXCLUDED.vendedor_referencia,
                    condicion_referencia = EXCLUDED.condicion_referencia,
                    variante_referencia = EXCLUDED.variante_referencia, referencia_desde = now(),
                    termino_busqueda = COALESCE(EXCLUDED.termino_busqueda, s.termino_busqueda),
                    ultimo_precio = EXCLUDED.ultimo_precio, resultado_comprobacion = 'SIN_COMPROBAR',
                    fallos_consecutivos = 0, proxima_comprobacion = EXCLUDED.proxima_comprobacion,
                    objetivo_avisado = FALSE,
                    token_cancelacion = COALESCE(s.token_cancelacion, EXCLUDED.token_cancelacion),
                    version = s.version + 1, actualizado_en = now()
                RETURNING\s""" + COLUMNAS;
        return consultarUno(sql, sentencia -> {
            int i = 1;
            sentencia.setLong(i++, seguimiento.getUsuarioId());
            sentencia.setLong(i++, seguimiento.getOfertaId());
            sentencia.setString(i++, seguimiento.getTipoAviso().name());
            sentencia.setBigDecimal(i++, seguimiento.getPrecioObjetivo());
            sentencia.setBigDecimal(i++, seguimiento.getPrecioReferencia());
            sentencia.setString(i++, seguimiento.getMonedaReferencia());
            sentencia.setBigDecimal(i++, seguimiento.getPrecioOriginalReferencia());
            sentencia.setString(i++, seguimiento.getMonedaOriginalReferencia());
            sentencia.setString(i++, seguimiento.getVendedorReferencia());
            sentencia.setString(i++, seguimiento.getCondicionReferencia());
            sentencia.setString(i++, seguimiento.getVarianteReferencia());
            sentencia.setString(i++, seguimiento.getTerminoBusqueda());
            sentencia.setBigDecimal(i++, seguimiento.getPrecioReferencia());
            sentencia.setObject(i++, seguimiento.getProximaComprobacion() == null ? OffsetDateTime.now()
                    : seguimiento.getProximaComprobacion());
            sentencia.setString(i, seguimiento.getTokenCancelacion());
        }, SeguimientoDaoJdbc::mapear).orElseThrow();
    }

    @Override
    public List<Detalle> listarDelUsuario(long usuarioId) {
        return consultarLista("SELECT " + COLUMNAS + ", " + COLUMNAS_OFERTA + " FROM seguimiento_oferta s "
                        + "JOIN oferta o ON o.id = s.oferta_id JOIN marketplace m ON m.id = o.marketplace_id "
                        + "WHERE s.usuario_id = ? AND s.estado <> 'CANCELADO' ORDER BY s.actualizado_en DESC",
                sentencia -> sentencia.setLong(1, usuarioId),
                fila -> new Detalle(mapear(fila), mapearOferta(fila), fila.getString("codigo_marketplace")));
    }

    @Override
    public boolean actualizarPreferencias(long usuarioId, long ofertaId, Seguimiento.TipoAviso tipo,
                                          BigDecimal precioObjetivo, Seguimiento.Estado estado) {
        // Al reanudar se comprueba pronto; cambiar el objetivo permite volver a avisar cuando se alcance.
        return ejecutarActualizacion("UPDATE seguimiento_oferta SET tipo_aviso = ?, precio_objetivo = ?, estado = ?, "
                        + "objetivo_avisado = CASE WHEN precio_objetivo IS DISTINCT FROM ? THEN FALSE "
                        + "ELSE objetivo_avisado END, "
                        + "proxima_comprobacion = CASE WHEN estado = 'PAUSADO' AND ? = 'ACTIVO' THEN now() "
                        + "ELSE proxima_comprobacion END, "
                        + "version = version + 1, actualizado_en = now() "
                        + "WHERE usuario_id = ? AND oferta_id = ? AND estado <> 'CANCELADO'",
                sentencia -> {
                    sentencia.setString(1, tipo.name());
                    sentencia.setBigDecimal(2, precioObjetivo);
                    sentencia.setString(3, estado.name());
                    sentencia.setBigDecimal(4, precioObjetivo);
                    sentencia.setString(5, estado.name());
                    sentencia.setLong(6, usuarioId);
                    sentencia.setLong(7, ofertaId);
                }) > 0;
    }

    @Override
    public boolean cancelar(long usuarioId, long ofertaId) {
        return ejecutarActualizacion("UPDATE seguimiento_oferta SET estado = 'CANCELADO', version = version + 1, "
                        + "actualizado_en = now() WHERE usuario_id = ? AND oferta_id = ? AND estado <> 'CANCELADO'",
                sentencia -> {
                    sentencia.setLong(1, usuarioId);
                    sentencia.setLong(2, ofertaId);
                }) > 0;
    }

    @Override
    public Optional<Seguimiento> cancelarPorToken(String token) {
        return consultarUno("UPDATE seguimiento_oferta s SET estado = 'CANCELADO', version = version + 1, "
                        + "actualizado_en = now() WHERE token_cancelacion = ? AND estado <> 'CANCELADO' RETURNING "
                        + COLUMNAS,
                sentencia -> sentencia.setString(1, token), SeguimientoDaoJdbc::mapear);
    }

    @Override
    public List<Pendiente> tomarPendientes(int limite, int minutosApartado) {
        String sql = """
                WITH elegidos AS (
                    SELECT usuario_id, oferta_id FROM seguimiento_oferta
                    WHERE estado = 'ACTIVO' AND proxima_comprobacion <= now()
                    ORDER BY proxima_comprobacion LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE seguimiento_oferta s SET proxima_comprobacion = now() + make_interval(mins => ?)
                FROM elegidos e, oferta o, marketplace m, usuario u
                WHERE s.usuario_id = e.usuario_id AND s.oferta_id = e.oferta_id AND o.id = s.oferta_id
                  AND m.id = o.marketplace_id AND u.id = s.usuario_id
                RETURNING %s, %s, u.correo AS correo_usuario, u.correo_verificado_en
                """.formatted(COLUMNAS, COLUMNAS_OFERTA);
        return consultarLista(sql, sentencia -> {
            sentencia.setInt(1, limite);
            sentencia.setInt(2, minutosApartado);
        }, fila -> new Pendiente(mapear(fila), mapearOferta(fila), fila.getString("codigo_marketplace"),
                fila.getString("correo_usuario"), fila.getObject("correo_verificado_en") != null));
    }

    @Override
    public boolean guardarComprobacion(Seguimiento s) {
        return ejecutarActualizacion("UPDATE seguimiento_oferta SET precio_referencia = ?, moneda_referencia = ?, "
                        + "precio_original_referencia = ?, moneda_original_referencia = ?, vendedor_referencia = ?, "
                        + "condicion_referencia = ?, variante_referencia = ?, referencia_desde = ?, ultimo_precio = ?, "
                        + "ultima_comprobacion = ?, resultado_comprobacion = ?, fallos_consecutivos = ?, "
                        + "proxima_comprobacion = ?, objetivo_avisado = ?, version = version + 1, actualizado_en = now() "
                        + "WHERE usuario_id = ? AND oferta_id = ? AND version = ? AND estado = 'ACTIVO'",
                sentencia -> {
                    int i = 1;
                    sentencia.setBigDecimal(i++, s.getPrecioReferencia());
                    sentencia.setString(i++, s.getMonedaReferencia());
                    sentencia.setBigDecimal(i++, s.getPrecioOriginalReferencia());
                    sentencia.setString(i++, s.getMonedaOriginalReferencia());
                    sentencia.setString(i++, s.getVendedorReferencia());
                    sentencia.setString(i++, s.getCondicionReferencia());
                    sentencia.setString(i++, s.getVarianteReferencia());
                    sentencia.setObject(i++, s.getReferenciaDesde());
                    sentencia.setBigDecimal(i++, s.getUltimoPrecio());
                    sentencia.setObject(i++, s.getUltimaComprobacion());
                    sentencia.setString(i++, s.getResultado().name());
                    sentencia.setShort(i++, (short) Math.min(Short.MAX_VALUE, s.getFallosConsecutivos()));
                    sentencia.setObject(i++, s.getProximaComprobacion());
                    sentencia.setBoolean(i++, s.isObjetivoAvisado());
                    sentencia.setLong(i++, s.getUsuarioId());
                    sentencia.setLong(i++, s.getOfertaId());
                    sentencia.setInt(i, s.getVersion());
                }) > 0;
    }

    @Override
    public void posponer(long usuarioId, long ofertaId, OffsetDateTime cuando) {
        ejecutarActualizacion("UPDATE seguimiento_oferta SET proxima_comprobacion = ? WHERE usuario_id = ? AND oferta_id = ?",
                sentencia -> {
                    sentencia.setObject(1, cuando);
                    sentencia.setLong(2, usuarioId);
                    sentencia.setLong(3, ofertaId);
                });
    }

    @Override
    public String asegurarToken(long usuarioId, long ofertaId, String token) {
        return consultarUno("UPDATE seguimiento_oferta SET token_cancelacion = COALESCE(token_cancelacion, ?) "
                        + "WHERE usuario_id = ? AND oferta_id = ? RETURNING token_cancelacion",
                sentencia -> {
                    sentencia.setString(1, token);
                    sentencia.setLong(2, usuarioId);
                    sentencia.setLong(3, ofertaId);
                }, fila -> fila.getString(1)).orElse(token);
    }

    private static Seguimiento mapear(ResultSet fila) throws SQLException {
        Seguimiento s = new Seguimiento();
        s.setUsuarioId(fila.getLong("usuario_id"));
        s.setOfertaId(fila.getLong("oferta_id"));
        s.setTipoAviso(Seguimiento.TipoAviso.valueOf(fila.getString("tipo_aviso")));
        s.setPrecioObjetivo(fila.getBigDecimal("precio_objetivo"));
        s.setEstado(Seguimiento.Estado.valueOf(fila.getString("estado")));
        s.setPrecioReferencia(fila.getBigDecimal("precio_referencia"));
        s.setMonedaReferencia(fila.getString("moneda_referencia"));
        s.setPrecioOriginalReferencia(fila.getBigDecimal("precio_original_referencia"));
        s.setMonedaOriginalReferencia(fila.getString("moneda_original_referencia"));
        s.setVendedorReferencia(fila.getString("vendedor_referencia"));
        s.setCondicionReferencia(fila.getString("condicion_referencia"));
        s.setVarianteReferencia(fila.getString("variante_referencia"));
        s.setReferenciaDesde(fila.getObject("referencia_desde", OffsetDateTime.class));
        s.setTerminoBusqueda(fila.getString("termino_busqueda"));
        s.setUltimoPrecio(fila.getBigDecimal("ultimo_precio"));
        s.setUltimaComprobacion(fila.getObject("ultima_comprobacion", OffsetDateTime.class));
        s.setResultado(Seguimiento.Resultado.valueOf(fila.getString("resultado_comprobacion")));
        s.setFallosConsecutivos(fila.getShort("fallos_consecutivos"));
        s.setProximaComprobacion(fila.getObject("proxima_comprobacion", OffsetDateTime.class));
        s.setObjetivoAvisado(fila.getBoolean("objetivo_avisado"));
        s.setTokenCancelacion(fila.getString("token_cancelacion"));
        s.setVersion(fila.getInt("version"));
        s.setCreadoEn(fila.getObject("creado_en", OffsetDateTime.class));
        return s;
    }

    private static Oferta mapearOferta(ResultSet fila) throws SQLException {
        Oferta oferta = new Oferta();
        oferta.setId(fila.getLong("o_id"));
        oferta.setMarketplaceId(fila.getShort("o_marketplace_id"));
        oferta.setIdExterno(fila.getString("o_id_externo"));
        oferta.setTitulo(fila.getString("o_titulo"));
        oferta.setUrlDestino(fila.getString("o_url_destino"));
        oferta.setUrlImagen(fila.getString("o_url_imagen"));
        oferta.setPrecioActual(fila.getBigDecimal("o_precio_actual"));
        oferta.setMoneda(fila.getString("o_moneda"));
        oferta.setCostoEnvio(fila.getBigDecimal("o_costo_envio"));
        oferta.setCondicion(fila.getString("o_condicion"));
        oferta.setVendedor(fila.getString("o_vendedor"));
        oferta.setConsultadoEn(fila.getObject("o_consultado_en", OffsetDateTime.class));
        oferta.setDetalles(fila.getString("o_detalles"));
        return oferta;
    }
}
