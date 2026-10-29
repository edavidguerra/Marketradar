package co.edu.unicesar.marketradar.dao.jdbc;

import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.domain.Oferta;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
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
