package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.OfertaDestacada;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementación JDBC del DAO de ofertas (tabla {@code oferta}).
 */
@Repository
public class OfertaDaoJdbc extends DaoJdbcBase implements OfertaDao {

    private static final String COLUMNAS = "id, producto_id, marketplace_id, id_externo, titulo, url_destino, "
            + "url_imagen, precio_actual, moneda, costo_envio, condicion, vendedor, consultado_en, calificacion, total_resenas";

    public OfertaDaoJdbc(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Oferta insertar(Oferta oferta) {
        long id = insertarYObtenerId("INSERT INTO oferta (producto_id, marketplace_id, id_externo, titulo, url_destino, "
                        + "url_imagen, precio_actual, moneda, costo_envio, condicion, vendedor, consultado_en, calificacion, "
                        + "total_resenas) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                sentencia -> asignarCampos(sentencia, oferta));
        oferta.setId(id);
        return oferta;
    }

    @Override
    public Optional<Oferta> buscarPorId(long id) {
        return consultarUno("SELECT " + COLUMNAS + " FROM oferta WHERE id = ?",
                sentencia -> sentencia.setLong(1, id), this::mapear);
    }

    @Override
    public Optional<Oferta> buscarPorMarketplaceYIdExterno(short marketplaceId, String idExterno) {
        return consultarUno("SELECT " + COLUMNAS + " FROM oferta WHERE marketplace_id = ? AND id_externo = ?",
                sentencia -> {
                    sentencia.setShort(1, marketplaceId);
                    sentencia.setString(2, idExterno);
                }, this::mapear);
    }

    @Override
    public Optional<OfertaDestacada> buscarMasBarataPorTitulo(String texto) {
        return consultarUno("SELECT " + columnasCon("o") + ", m.codigo AS codigo_marketplace, NULL AS precio_anterior "
                        + "FROM oferta o JOIN marketplace m ON m.id = o.marketplace_id "
                        + "WHERE to_tsvector('spanish', o.titulo) @@ plainto_tsquery('spanish', ?) "
                        + "ORDER BY o.precio_actual LIMIT 1",
                sentencia -> sentencia.setString(1, texto), this::mapearDestacada);
    }

    @Override
    public List<OfertaDestacada> listarMasBaratasPorTitulo(String texto, int limite) {
        return consultarLista("SELECT " + columnasCon("o") + ", m.codigo AS codigo_marketplace, NULL AS precio_anterior "
                        + "FROM oferta o JOIN marketplace m ON m.id = o.marketplace_id "
                        + "WHERE to_tsvector('spanish', o.titulo) @@ plainto_tsquery('spanish', ?) "
                        + "ORDER BY o.precio_actual LIMIT ?",
                sentencia -> {
                    sentencia.setString(1, texto);
                    sentencia.setInt(2, limite);
                }, this::mapearDestacada);
    }

    /**
     * Solo compara mediciones equivalentes: la misma oferta, con el mismo vendedor, la misma condición y la misma
     * moneda que tiene ahora. Las mediciones viejas sin vendedor no cuentan, porque no se sabe de quién eran.
     */
    @Override
    public Optional<OfertaDestacada> buscarMayorBajaDePrecio(int dias) {
        return consultarUno("SELECT " + columnasCon("o") + ", m.codigo AS codigo_marketplace, "
                        + "h.precio_maximo AS precio_anterior "
                        + "FROM oferta o JOIN marketplace m ON m.id = o.marketplace_id "
                        + "JOIN LATERAL (SELECT MAX(hp.precio) AS precio_maximo FROM historial_precio hp "
                        + "      WHERE hp.oferta_id = o.id AND hp.registrado_en > now() - make_interval(days => ?) "
                        + "        AND hp.moneda = o.moneda AND hp.vendedor IS NOT NULL AND hp.vendedor = o.vendedor "
                        + "        AND hp.condicion IS NOT DISTINCT FROM o.condicion) h ON TRUE "
                        + "WHERE h.precio_maximo > o.precio_actual "
                        + "ORDER BY (h.precio_maximo - o.precio_actual) / h.precio_maximo DESC LIMIT 1",
                sentencia -> sentencia.setInt(1, dias), this::mapearDestacada);
    }

    @Override
    public List<Oferta> listarTodos() {
        return consultarLista("SELECT " + COLUMNAS + " FROM oferta ORDER BY precio_actual", sentencia -> { }, this::mapear);
    }

    @Override
    public boolean actualizar(Oferta oferta) {
        return ejecutarActualizacion("UPDATE oferta SET producto_id = ?, marketplace_id = ?, id_externo = ?, titulo = ?, "
                        + "url_destino = ?, url_imagen = ?, precio_actual = ?, moneda = ?, costo_envio = ?, "
                        + "condicion = ?, vendedor = ?, consultado_en = ?, calificacion = ?, total_resenas = ? WHERE id = ?",
                sentencia -> {
                    asignarCampos(sentencia, oferta);
                    sentencia.setLong(15, oferta.getId());
                }) > 0;
    }

    /** Filas por consulta: 13 parámetros por fila, muy por debajo del límite de PostgreSQL. */
    private static final int FILAS_POR_LOTE = 100;

    /**
     * Una sola consulta por lote. Todas las partes del WITH ven la base como estaba antes de empezar, así que
     * {@code previos} trae el precio anterior y el historial solo se escribe si el precio es nuevo o cambió.
     */
    private static final String SQL_LOTE = """
            WITH datos (marketplace_id, id_externo, titulo, url_destino, url_imagen, precio_actual, moneda,
                        costo_envio, condicion, vendedor, consultado_en, calificacion, total_resenas) AS (
                VALUES %s
            ),
            previos AS (
                SELECT o.marketplace_id, o.id_externo, o.precio_actual, o.moneda, o.vendedor, o.condicion
                FROM oferta o JOIN datos d ON d.marketplace_id = o.marketplace_id AND d.id_externo = o.id_externo
            ),
            guardadas AS (
                INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, url_imagen, precio_actual, moneda,
                                    costo_envio, condicion, vendedor, consultado_en, calificacion, total_resenas)
                SELECT * FROM datos
                ON CONFLICT (marketplace_id, id_externo) DO UPDATE SET
                    titulo = EXCLUDED.titulo, url_destino = EXCLUDED.url_destino, url_imagen = EXCLUDED.url_imagen,
                    precio_actual = EXCLUDED.precio_actual, moneda = EXCLUDED.moneda,
                    costo_envio = EXCLUDED.costo_envio, condicion = EXCLUDED.condicion, vendedor = EXCLUDED.vendedor,
                    consultado_en = EXCLUDED.consultado_en, calificacion = EXCLUDED.calificacion,
                    total_resenas = EXCLUDED.total_resenas
                RETURNING id, marketplace_id, id_externo, precio_actual, moneda, vendedor, condicion
            ),
            historial AS (
                -- Se registra una medición nueva si cambió el precio, o si cambió el vendedor, la condición o la
                -- moneda: así cada fila dice de qué oferta exacta era el precio.
                INSERT INTO historial_precio (oferta_id, precio, moneda, vendedor, condicion)
                SELECT g.id, g.precio_actual, g.moneda, g.vendedor, g.condicion
                FROM guardadas g
                LEFT JOIN previos p ON p.marketplace_id = g.marketplace_id AND p.id_externo = g.id_externo
                WHERE p.precio_actual IS NULL OR p.precio_actual <> g.precio_actual
                   OR p.moneda IS DISTINCT FROM g.moneda OR p.vendedor IS DISTINCT FROM g.vendedor
                   OR p.condicion IS DISTINCT FROM g.condicion
            )
            SELECT id, marketplace_id, id_externo FROM guardadas
            """;

    private static final String FILA_LOTE = "(CAST(? AS SMALLINT), CAST(? AS VARCHAR), CAST(? AS VARCHAR), "
            + "CAST(? AS TEXT), CAST(? AS TEXT), CAST(? AS NUMERIC), CAST(? AS CHAR(3)), CAST(? AS NUMERIC), "
            + "CAST(? AS VARCHAR), CAST(? AS VARCHAR), CAST(? AS TIMESTAMPTZ), CAST(? AS NUMERIC), CAST(? AS INTEGER))";

    @Override
    public Map<String, Long> guardarLote(List<Oferta> ofertas) {
        Map<String, Oferta> unicas = new LinkedHashMap<>();
        for (Oferta oferta : ofertas) {
            unicas.putIfAbsent(OfertaDao.clave(oferta.getMarketplaceId(), oferta.getIdExterno()), oferta);
        }
        List<Oferta> lista = new ArrayList<>(unicas.values());
        Map<String, Long> ids = new HashMap<>();
        for (int desde = 0; desde < lista.size(); desde += FILAS_POR_LOTE) {
            List<Oferta> lote = lista.subList(desde, Math.min(desde + FILAS_POR_LOTE, lista.size()));
            String sql = SQL_LOTE.formatted(String.join(", ", Collections.nCopies(lote.size(), FILA_LOTE)));
            consultarLista(sql, sentencia -> {
                int i = 1;
                for (Oferta oferta : lote) {
                    sentencia.setShort(i++, oferta.getMarketplaceId());
                    sentencia.setString(i++, oferta.getIdExterno());
                    sentencia.setString(i++, oferta.getTitulo());
                    sentencia.setString(i++, oferta.getUrlDestino());
                    sentencia.setString(i++, oferta.getUrlImagen());
                    sentencia.setBigDecimal(i++, oferta.getPrecioActual());
                    sentencia.setString(i++, oferta.getMoneda());
                    sentencia.setBigDecimal(i++, oferta.getCostoEnvio());
                    sentencia.setString(i++, oferta.getCondicion());
                    sentencia.setString(i++, oferta.getVendedor());
                    sentencia.setObject(i++, oferta.getConsultadoEn() == null
                            ? OffsetDateTime.now() : oferta.getConsultadoEn());
                    sentencia.setBigDecimal(i++, oferta.getCalificacion());
                    asignarEnteroNulable(sentencia, i++, oferta.getTotalResenas());
                }
            }, fila -> {
                ids.put(OfertaDao.clave(fila.getShort("marketplace_id"), fila.getString("id_externo")), fila.getLong("id"));
                return null;
            });
        }
        return ids;
    }

    @Override
    public boolean eliminar(long id) {
        return ejecutarActualizacion("DELETE FROM oferta WHERE id = ?", sentencia -> sentencia.setLong(1, id)) > 0;
    }

    private void asignarCampos(PreparedStatement sentencia, Oferta oferta) throws SQLException {
        asignarLongNulable(sentencia, 1, oferta.getProductoId());
        sentencia.setShort(2, oferta.getMarketplaceId());
        sentencia.setString(3, oferta.getIdExterno());
        sentencia.setString(4, oferta.getTitulo());
        sentencia.setString(5, oferta.getUrlDestino());
        sentencia.setString(6, oferta.getUrlImagen());
        sentencia.setBigDecimal(7, oferta.getPrecioActual());
        sentencia.setString(8, oferta.getMoneda());
        sentencia.setBigDecimal(9, oferta.getCostoEnvio());
        sentencia.setString(10, oferta.getCondicion());
        sentencia.setString(11, oferta.getVendedor());
        OffsetDateTime consultadoEn = oferta.getConsultadoEn() == null ? OffsetDateTime.now() : oferta.getConsultadoEn();
        sentencia.setObject(12, consultadoEn);
        sentencia.setBigDecimal(13, oferta.getCalificacion());
        asignarEnteroNulable(sentencia, 14, oferta.getTotalResenas());
    }

    private static String columnasCon(String alias) {
        return alias + "." + COLUMNAS.replace(", ", ", " + alias + ".");
    }

    private OfertaDestacada mapearDestacada(ResultSet fila) throws SQLException {
        return new OfertaDestacada(mapear(fila), fila.getString("codigo_marketplace"),
                fila.getBigDecimal("precio_anterior"));
    }

    private Oferta mapear(ResultSet fila) throws SQLException {
        Oferta oferta = new Oferta();
        oferta.setId(fila.getLong("id"));
        oferta.setProductoId(leerLongNulable(fila, "producto_id"));
        oferta.setMarketplaceId(fila.getShort("marketplace_id"));
        oferta.setIdExterno(fila.getString("id_externo"));
        oferta.setTitulo(fila.getString("titulo"));
        oferta.setUrlDestino(fila.getString("url_destino"));
        oferta.setUrlImagen(fila.getString("url_imagen"));
        oferta.setPrecioActual(fila.getBigDecimal("precio_actual"));
        oferta.setMoneda(fila.getString("moneda"));
        oferta.setCostoEnvio(fila.getBigDecimal("costo_envio"));
        oferta.setCondicion(fila.getString("condicion"));
        oferta.setVendedor(fila.getString("vendedor"));
        oferta.setConsultadoEn(fila.getObject("consultado_en", OffsetDateTime.class));
        oferta.setCalificacion(fila.getBigDecimal("calificacion"));
        int totalResenas = fila.getInt("total_resenas");
        oferta.setTotalResenas(fila.wasNull() ? null : totalResenas);
        return oferta;
    }
}
