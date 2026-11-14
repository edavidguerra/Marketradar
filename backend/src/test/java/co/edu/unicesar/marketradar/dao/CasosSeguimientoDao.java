package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.dao.jdbc.AvisoSeguimientoDaoJdbc;
import co.edu.unicesar.marketradar.dao.jdbc.MarketplaceDaoJdbc;
import co.edu.unicesar.marketradar.dao.jdbc.OfertaDaoJdbc;
import co.edu.unicesar.marketradar.dao.jdbc.SeguimientoDaoJdbc;
import co.edu.unicesar.marketradar.dao.jdbc.UsuarioDaoJdbc;
import co.edu.unicesar.marketradar.dao.jdbc.VerificacionCorreoDaoJdbc;
import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.Rol;
import co.edu.unicesar.marketradar.domain.Seguimiento;
import co.edu.unicesar.marketradar.domain.Usuario;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Casos de los DAO de seguimientos contra PostgreSQL 16 real. Cada subclase dice de dónde sale la base de datos (por
 * defecto, un contenedor de Testcontainers).
 */
abstract class CasosSeguimientoDao {

    static DataSource fuente;

    private Usuario usuario(String correo) {
        Usuario u = new Usuario();
        u.setCorreo(correo);
        u.setClaveHash("x");
        u.setRol(Rol.USUARIO);
        u.setNombres("Ana");
        u.setApellidos("Paz");
        return new UsuarioDaoJdbc(fuente).insertar(u);
    }

    private Oferta oferta() {
        Oferta o = new Oferta();
        o.setMarketplaceId(new MarketplaceDaoJdbc(fuente).buscarPorCodigo("googleshopping").orElseThrow().getId());
        o.setIdExterno("g-" + UUID.randomUUID());
        o.setTitulo("Apple iPhone 13 128GB");
        o.setUrlDestino("https://www.exito.com/iphone");
        o.setPrecioActual(new BigDecimal("3000000.00"));
        o.setMoneda("COP");
        o.setVendedor("Éxito");
        o.setDetalles("128 GB | Negro");
        return new OfertaDaoJdbc(fuente).insertar(o);
    }

    private static Seguimiento nuevo(long usuarioId, long ofertaId, String token) {
        Seguimiento s = new Seguimiento();
        s.setUsuarioId(usuarioId);
        s.setOfertaId(ofertaId);
        s.setTipoAviso(Seguimiento.TipoAviso.BAJA_PRECIO);
        s.setPrecioReferencia(new BigDecimal("3000000"));
        s.setMonedaReferencia("COP");
        s.setVendedorReferencia("Éxito");
        s.setTerminoBusqueda("iphone 13");
        s.setProximaComprobacion(OffsetDateTime.now().minusMinutes(1));
        s.setTokenCancelacion(token);
        return s;
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "") + "abcdefghijk";
    }

    @Test
    void activarListarPausarYCancelarPorToken() {
        SeguimientoDao dao = new SeguimientoDaoJdbc(fuente);
        Usuario ana = usuario("ana-" + UUID.randomUUID() + "@example.com");
        Usuario beto = usuario("beto-" + UUID.randomUUID() + "@example.com");
        Oferta oferta = oferta();
        Seguimiento activo = dao.activar(nuevo(ana.getId(), oferta.getId(), token()));
        assertEquals(Seguimiento.Estado.ACTIVO, activo.getEstado());
        assertEquals(1, dao.listarDelUsuario(ana.getId()).size());
        assertTrue(dao.listarDelUsuario(beto.getId()).isEmpty(), "Cada persona ve solo lo suyo");

        assertFalse(dao.actualizarPreferencias(beto.getId(), oferta.getId(), Seguimiento.TipoAviso.BAJA_PRECIO, null,
                Seguimiento.Estado.PAUSADO), "Otra persona no lo puede cambiar");
        assertTrue(dao.actualizarPreferencias(ana.getId(), oferta.getId(), Seguimiento.TipoAviso.PRECIO_OBJETIVO,
                new BigDecimal("2500000"), Seguimiento.Estado.PAUSADO));
        assertEquals(Seguimiento.Estado.PAUSADO, dao.buscar(ana.getId(), oferta.getId()).orElseThrow().getEstado());

        String tokenGuardado = dao.asegurarToken(ana.getId(), oferta.getId(), token());
        assertEquals(activo.getTokenCancelacion(), tokenGuardado, "No cambia la clave que ya tenía");
        assertTrue(dao.cancelarPorToken(tokenGuardado).isPresent());
        assertTrue(dao.cancelarPorToken(tokenGuardado).isEmpty(), "Un enlace usado ya no cancela nada");
        assertTrue(dao.listarDelUsuario(ana.getId()).isEmpty());

        Seguimiento otraVez = dao.activar(nuevo(ana.getId(), oferta.getId(), token()));
        assertEquals(Seguimiento.Estado.ACTIVO, otraVez.getEstado());
        assertTrue(otraVez.getVersion() > activo.getVersion(), "Reactivar sube la versión");
    }

    @Test
    void tomarPendientesApartaYGuardarComprobacionUsaLaVersion() {
        SeguimientoDao dao = new SeguimientoDaoJdbc(fuente);
        Usuario ana = usuario("ana-" + UUID.randomUUID() + "@example.com");
        Oferta oferta = oferta();
        dao.activar(nuevo(ana.getId(), oferta.getId(), token()));

        List<SeguimientoDao.Pendiente> tomados = dao.tomarPendientes(100, 10);
        SeguimientoDao.Pendiente mio = tomados.stream()
                .filter(p -> p.seguimiento().getOfertaId() == oferta.getId()).findFirst().orElseThrow();
        assertEquals(ana.getCorreo(), mio.correo());
        assertFalse(mio.correoVerificado());
        assertEquals("googleshopping", mio.codigoMarketplace());
        assertTrue(dao.tomarPendientes(100, 10).stream().noneMatch(p -> p.seguimiento().getOfertaId() == oferta.getId()),
                "Ya apartado, otra ronda no lo toma");

        Seguimiento s = mio.seguimiento();
        s.setResultado(Seguimiento.Resultado.SIN_CAMBIO);
        s.setUltimaComprobacion(OffsetDateTime.now());
        s.setProximaComprobacion(OffsetDateTime.now().plusHours(12));
        assertTrue(dao.guardarComprobacion(s));
        assertFalse(dao.guardarComprobacion(s), "Con la versión vieja ya no se guarda");
    }

    @Test
    void losAvisosNoSeRepitenYGuardanSuEstado() {
        SeguimientoDao seguimientos = new SeguimientoDaoJdbc(fuente);
        AvisoSeguimientoDao dao = new AvisoSeguimientoDaoJdbc(fuente);
        Usuario ana = usuario("ana-" + UUID.randomUUID() + "@example.com");
        Oferta oferta = oferta();
        seguimientos.activar(nuevo(ana.getId(), oferta.getId(), token()));

        AvisoSeguimiento aviso = new AvisoSeguimiento();
        aviso.setUsuarioId(ana.getId());
        aviso.setOfertaId(oferta.getId());
        aviso.setTipo(AvisoSeguimiento.Tipo.BAJA_PRECIO);
        aviso.setClaveEvento("baja:" + ana.getId() + ":" + oferta.getId() + ":1:2700000");
        aviso.setCorreoDestino(ana.getCorreo());
        aviso.setPrecioAnterior(new BigDecimal("3000000"));
        aviso.setPrecioActual(new BigDecimal("2700000"));
        aviso.setMoneda("COP");
        aviso.setComprobadoEn(OffsetDateTime.now());
        long id = dao.insertarSiEsNuevo(aviso).orElseThrow();
        assertTrue(dao.insertarSiEsNuevo(aviso).isEmpty(), "La misma clave de evento no se guarda dos veces");

        AvisoSeguimientoDao.Pendiente tomado = dao.tomar(id, 10).orElseThrow();
        assertEquals("ACTIVO", tomado.estadoSeguimiento());
        assertEquals("Apple iPhone 13 128GB", tomado.tituloOferta());
        assertTrue(dao.tomar(id, 10).isEmpty(), "Apartado mientras se manda");

        dao.registrarFallo(id, "Fallo simulado", true, OffsetDateTime.now().minusMinutes(1));
        assertTrue(dao.tomarPendientes(100, 10).stream().anyMatch(p -> p.aviso().getId() == id));
        dao.marcarEnviado(id);
        AvisoSeguimiento enviado = dao.recientes(ana.getId(), oferta.getId(), 3).get(0);
        assertEquals(AvisoSeguimiento.Estado.ENVIADO, enviado.getEstado());
        assertEquals(2, enviado.getIntentos(), "Un intento fallido y uno que salió");
        assertTrue(dao.tomar(id, 10).isEmpty(), "Un aviso enviado no se vuelve a tomar");

        AvisoSeguimiento otro = new AvisoSeguimiento();
        otro.setUsuarioId(ana.getId());
        otro.setOfertaId(oferta.getId());
        otro.setTipo(AvisoSeguimiento.Tipo.BAJA_PRECIO);
        otro.setClaveEvento("baja:" + ana.getId() + ":" + oferta.getId() + ":2:2600000");
        otro.setCorreoDestino(ana.getCorreo());
        otro.setPrecioActual(new BigDecimal("2600000"));
        otro.setMoneda("COP");
        long idOtro = dao.insertarSiEsNuevo(otro).orElseThrow();
        dao.registrarFallo(idOtro, "Sin más intentos", true, null);
        assertEquals(AvisoSeguimiento.Estado.FALLIDO, dao.recientes(ana.getId(), oferta.getId(), 3).stream()
                .filter(a -> a.getId() == idOtro).findFirst().orElseThrow().getEstado());
        assertEquals(0, dao.descartarPendientes(ana.getId(), oferta.getId(), "cancelado"));
    }

    @Test
    void verificarElCorreoSoloSiNoCambio() {
        VerificacionCorreoDao dao = new VerificacionCorreoDaoJdbc(fuente);
        Usuario ana = usuario("ana-" + UUID.randomUUID() + "@example.com");
        String hash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        dao.guardarToken(hash, ana.getId(), ana.getCorreo(), OffsetDateTime.now().plusHours(24));
        assertEquals(1, dao.contarEnviados(ana.getId(), OffsetDateTime.now().minusHours(1)));
        assertTrue(dao.verificadoEn(ana.getId()).isEmpty());
        assertEquals(ana.getId(), dao.usarToken(hash).orElseThrow());
        assertTrue(dao.verificadoEn(ana.getId()).isPresent());
        assertTrue(dao.usarToken(hash).isEmpty(), "Un enlace se usa una sola vez");

        String vencido = hash.substring(0, 63) + (hash.endsWith("0") ? "1" : "0");
        assertNotEquals(hash, vencido);
        dao.guardarToken(vencido, ana.getId(), ana.getCorreo(), OffsetDateTime.now().plusHours(24));
        envejecer(vencido);
        assertTrue(dao.usarToken(vencido).isEmpty(), "Un enlace vencido no sirve");
    }

    /** Pasa el enlace al pasado, como si hubieran pasado dos días. */
    private static void envejecer(String hash) {
        try (var conexion = fuente.getConnection(); var sentencia = conexion.prepareStatement(
                "UPDATE token_correo SET creado_en = now() - interval '2 days', vence_en = now() - interval '1 day' "
                        + "WHERE token_hash = ?")) {
            sentencia.setString(1, hash);
            sentencia.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
