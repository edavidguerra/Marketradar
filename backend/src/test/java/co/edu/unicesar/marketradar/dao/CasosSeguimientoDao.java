package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.correo.ResultadoEnvio;
import co.edu.unicesar.marketradar.dao.jdbc.AvisoSeguimientoDaoJdbc;
import co.edu.unicesar.marketradar.dao.jdbc.EjecucionTareaDaoJdbc;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // --- Ciclo completo en un entorno controlado: seguimiento activo → revisión → bajada u objetivo → aviso →
    // entrega al servicio de correo (un Brevo simulado en 127.0.0.1). No toca precios ni correos reales.

    /** Brevo simulado: guarda a quién se mandó cada correo y responde con el estado que se le diga. */
    private static final class BrevoSimulado implements AutoCloseable {
        final com.sun.net.httpserver.HttpServer servidor;
        final List<String> cuerpos = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile int estado = 201;

        BrevoSimulado() throws java.io.IOException {
            servidor = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", intercambio -> {
                cuerpos.add(new String(intercambio.getRequestBody().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8));
                byte[] respuesta = (estado == 201 ? "{\"messageId\":\"<prueba@smtp-relay.brevo.com>\"}"
                        : "{\"code\":\"unauthorized\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                intercambio.sendResponseHeaders(estado, respuesta.length);
                intercambio.getResponseBody().write(respuesta);
                intercambio.close();
            });
            servidor.start();
        }

        long paraCorreo(String correo) {
            return cuerpos.stream().filter(c -> c.contains(correo)).count();
        }

        @Override
        public void close() {
            servidor.stop(0);
        }
    }

    private co.edu.unicesar.marketradar.service.seguimiento.ServicioComprobacionPrecios ronda(BrevoSimulado brevo,
            co.edu.unicesar.marketradar.service.seguimiento.ComprobadorOfertas comprobador) {
        var correo = new co.edu.unicesar.marketradar.correo.PropiedadesCorreo(null, 0, null, null,
                "avisos@marketradar.test", "Marketradar", false, false, 5, "https://marketradar.example.com",
                "brevo", "clave-de-prueba", "http://127.0.0.1:" + brevo.servidor.getAddress().getPort());
        var seguimientos = new co.edu.unicesar.marketradar.config.PropiedadesSeguimientos(true, 720, 15, 25, 5,
                BigDecimal.ONE, 3, 48, 20, "");
        var transporte = new co.edu.unicesar.marketradar.correo.TransporteCorreoHttp(correo,
                java.net.http.HttpClient.newHttpClient());
        var avisos = new AvisoSeguimientoDaoJdbc(fuente);
        var seguimientoDao = new SeguimientoDaoJdbc(fuente);
        var envio = new co.edu.unicesar.marketradar.service.seguimiento.ServicioEnvioAvisos(avisos, seguimientoDao,
                transporte, correo, seguimientos);
        return new co.edu.unicesar.marketradar.service.seguimiento.ServicioComprobacionPrecios(seguimientoDao, avisos,
                comprobador, envio, seguimientos, new EjecucionTareaDaoJdbc(fuente));
    }

    /** Persona con el correo ya verificado y un seguimiento activo que toca revisar primero. */
    private Seguimiento seguimientoListo(Usuario persona, Oferta oferta, Seguimiento.TipoAviso tipo, String objetivo) {
        VerificacionCorreoDao verificacion = new VerificacionCorreoDaoJdbc(fuente);
        String hash = hash();
        verificacion.guardarToken(hash, persona.getId(), persona.getCorreo(), OffsetDateTime.now().plusHours(1));
        verificacion.usarToken(hash);
        Seguimiento s = nuevo(persona.getId(), oferta.getId(), token());
        s.setTipoAviso(tipo);
        s.setPrecioObjetivo(objetivo == null ? null : new BigDecimal(objetivo));
        s.setCondicionReferencia("nuevo");
        s.setVarianteReferencia(co.edu.unicesar.marketradar.domain.NormalizadorEspecificaciones
                .claveVariante("Apple iPhone 13 128GB", List.of()));
        Seguimiento activo = new SeguimientoDaoJdbc(fuente).activar(s);
        hacerQueToque(persona.getId(), oferta.getId());
        return activo;
    }

    private static void hacerQueToque(long usuarioId, long ofertaId) {
        try (var conexion = fuente.getConnection(); var sentencia = conexion.prepareStatement(
                "UPDATE seguimiento_oferta SET proxima_comprobacion = now() - interval '30 days' "
                        + "WHERE usuario_id = ? AND oferta_id = ?")) {
            sentencia.setLong(1, usuarioId);
            sentencia.setLong(2, ofertaId);
            sentencia.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static co.edu.unicesar.marketradar.service.seguimiento.ComprobadorOfertas tiendaQueMuestra(Oferta oferta,
                                                                                                      String precio) {
        var comprobador = org.mockito.Mockito.mock(co.edu.unicesar.marketradar.service.seguimiento.ComprobadorOfertas.class);
        org.mockito.Mockito.when(comprobador.consultar(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of(
                co.edu.unicesar.marketradar.domain.OfertaNormalizada.builder().codigoMarketplace("googleshopping")
                        .idExterno(oferta.getIdExterno()).titulo("Apple iPhone 13 128GB").precio(new BigDecimal(precio))
                        .moneda("COP").vendedor("Éxito").condicion("nuevo").urlDestino("https://www.exito.com/iphone")
                        .consultadoEn(OffsetDateTime.now()).build()));
        return comprobador;
    }

    private List<AvisoSeguimiento> avisosDePrecio(Usuario persona, Oferta oferta) {
        return new AvisoSeguimientoDaoJdbc(fuente).recientes(persona.getId(), oferta.getId(), 20).stream()
                .filter(a -> a.getTipo() != AvisoSeguimiento.Tipo.CONFIRMACION).toList();
    }

    @Test
    void cicloCompletoUnaBajadaRealLlegaUnaSolaVezAlServicioDeCorreo() throws Exception {
        Usuario ana = usuario("ana-" + UUID.randomUUID() + "@example.com");
        Oferta oferta = oferta();
        seguimientoListo(ana, oferta, Seguimiento.TipoAviso.BAJA_PRECIO, null);
        try (BrevoSimulado brevo = new BrevoSimulado()) {
            ronda(brevo, tiendaQueMuestra(oferta, "2500000")).ejecutarRonda(EjecucionTareaDao.Origen.CRON);

            List<AvisoSeguimiento> avisos = avisosDePrecio(ana, oferta);
            assertEquals(1, avisos.size(), "Una bajada de 3.000.000 a 2.500.000 crea un aviso");
            assertEquals(AvisoSeguimiento.Tipo.BAJA_PRECIO, avisos.get(0).getTipo());
            assertEquals(AvisoSeguimiento.Estado.ENVIADO, avisos.get(0).getEstado(),
                    "ENVIADO = aceptado por el servicio de correo (no quiere decir que la persona ya lo leyó)");
            assertNotNull(avisos.get(0).getEnviadoEn());
            assertEquals(1, brevo.paraCorreo(ana.getCorreo()), "El servicio de correo recibió un solo pedido");

            // Otra ronda con el mismo precio: no hay bajada nueva y no se repite el correo.
            hacerQueToque(ana.getId(), oferta.getId());
            ronda(brevo, tiendaQueMuestra(oferta, "2500000")).ejecutarRonda(EjecucionTareaDao.Origen.CRON);
            assertEquals(1, avisosDePrecio(ana, oferta).size(), "Sin avisos duplicados");
            assertEquals(1, brevo.paraCorreo(ana.getCorreo()));
        }
    }

    @Test
    void cicloCompletoObjetivoAlcanzadoConElProveedorRechazandoNoSeDaPorEnviado() throws Exception {
        Usuario beto = usuario("beto-" + UUID.randomUUID() + "@example.com");
        Oferta oferta = oferta();
        seguimientoListo(beto, oferta, Seguimiento.TipoAviso.PRECIO_OBJETIVO, "2600000");
        try (BrevoSimulado brevo = new BrevoSimulado()) {
            brevo.estado = 401;
            ronda(brevo, tiendaQueMuestra(oferta, "2550000")).ejecutarRonda(EjecucionTareaDao.Origen.CRON);

            List<AvisoSeguimiento> avisos = avisosDePrecio(beto, oferta);
            assertEquals(1, avisos.size(), "Llegó al objetivo: se crea el aviso");
            assertEquals(AvisoSeguimiento.Tipo.PRECIO_OBJETIVO, avisos.get(0).getTipo());
            assertNotEquals(AvisoSeguimiento.Estado.ENVIADO, avisos.get(0).getEstado(),
                    "Si el proveedor rechaza el correo, no se presenta como enviado");
            assertNull(avisos.get(0).getEnviadoEn());
            assertNotNull(avisos.get(0).getUltimoError());
            assertEquals(1, brevo.paraCorreo(beto.getCorreo()), "Se intentó una vez");
        }
    }

    @Test
    void unSeguimientoPausadoNoEntraEnLaRondaNiGuardaComprobacion() {
        SeguimientoDao dao = new SeguimientoDaoJdbc(fuente);
        Usuario ana = usuario("ana-" + UUID.randomUUID() + "@example.com");
        Oferta oferta = oferta();
        dao.activar(nuevo(ana.getId(), oferta.getId(), token()));
        assertTrue(dao.actualizarPreferencias(ana.getId(), oferta.getId(), Seguimiento.TipoAviso.BAJA_PRECIO, null,
                Seguimiento.Estado.PAUSADO));

        assertTrue(dao.tomarPendientes(100, 10).stream().noneMatch(p -> p.seguimiento().getOfertaId() == oferta.getId()),
                "Pausado, la ronda no lo revisa y por eso no genera avisos");
        Seguimiento viejo = dao.buscar(ana.getId(), oferta.getId()).orElseThrow();
        viejo.setResultado(Seguimiento.Resultado.BAJO);
        viejo.setUltimaComprobacion(OffsetDateTime.now());
        viejo.setProximaComprobacion(OffsetDateTime.now().plusHours(12));
        assertFalse(dao.guardarComprobacion(viejo), "Una ronda que lo tomó antes de pausarlo no guarda su resultado");

        assertTrue(dao.actualizarPreferencias(ana.getId(), oferta.getId(), Seguimiento.TipoAviso.BAJA_PRECIO, null,
                Seguimiento.Estado.ACTIVO));
        assertEquals(Seguimiento.Estado.ACTIVO, dao.buscar(ana.getId(), oferta.getId()).orElseThrow().getEstado(),
                "Al volver a leerlo sigue activo");
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
        assertTrue(dao.guardarToken(hash, ana.getId(), ana.getCorreo(), OffsetDateTime.now().plusHours(24)));
        assertEquals(1, dao.contarEnviados(ana.getId(), OffsetDateTime.now().minusHours(1)));
        dao.marcarAceptado(hash, 1, "brevo", "<id@brevo>");
        assertTrue(dao.verificadoEn(ana.getId()).isEmpty());
        assertEquals(ana.getId(), dao.usarToken(hash).orElseThrow());
        assertTrue(dao.verificadoEn(ana.getId()).isPresent());
        assertTrue(dao.usarToken(hash).isEmpty(), "Un enlace se usa una sola vez");

        String vencido = hash.substring(0, 63) + (hash.endsWith("0") ? "1" : "0");
        assertNotEquals(hash, vencido);
        assertTrue(dao.guardarToken(vencido, ana.getId(), ana.getCorreo(), OffsetDateTime.now().plusHours(24)));
        envejecer(vencido);
        assertTrue(dao.usarToken(vencido).isEmpty(), "Un enlace vencido no sirve");
    }

    @Test
    void elEnvioDelEnlaceGuardaSuEstadoYNoHayDosEnCurso() {
        VerificacionCorreoDao dao = new VerificacionCorreoDaoJdbc(fuente);
        Usuario ana = usuario("ana-" + UUID.randomUUID() + "@example.com");
        String primero = hash();
        String segundo = hash();
        assertTrue(dao.guardarToken(primero, ana.getId(), ana.getCorreo(), OffsetDateTime.now().plusHours(24)));
        assertEquals(VerificacionCorreoDao.EstadoEnvio.PENDIENTE, dao.ultimoEnvio(ana.getId()).orElseThrow().estado());
        assertFalse(dao.guardarToken(segundo, ana.getId(), ana.getCorreo(), OffsetDateTime.now().plusHours(24)),
                "Mientras uno se está mandando no se crea otro (dos clics seguidos)");

        dao.marcarFallido(primero, 2, "brevo rechazó el correo (HTTP 401)");
        VerificacionCorreoDao.Envio fallido = dao.ultimoEnvio(ana.getId()).orElseThrow();
        assertEquals(VerificacionCorreoDao.EstadoEnvio.FALLIDO, fallido.estado());
        assertNull(fallido.aceptadoEn());
        assertEquals(0, dao.contarEnviados(ana.getId(), OffsetDateTime.now().minusHours(1)), "Un fallo no cuenta");
        assertEquals(1, dao.contarSolicitudes(ana.getId(), OffsetDateTime.now().minusHours(1)), "Pero sí como solicitud");

        assertTrue(dao.guardarToken(segundo, ana.getId(), ana.getCorreo(), OffsetDateTime.now().plusHours(24)));
        dao.marcarAceptado(segundo, 1, "brevo", "<abc@smtp-relay.brevo.com>");
        VerificacionCorreoDao.Envio aceptado = dao.ultimoEnvio(ana.getId()).orElseThrow();
        assertEquals(VerificacionCorreoDao.EstadoEnvio.ACEPTADO, aceptado.estado());
        assertNotNull(aceptado.aceptadoEn());
        assertFalse(aceptado.usado());
    }

    @Test
    void unEnvioColgadoSeLiberaParaPedirOtro() {
        VerificacionCorreoDao dao = new VerificacionCorreoDaoJdbc(fuente);
        Usuario ana = usuario("ana-" + UUID.randomUUID() + "@example.com");
        String colgado = hash();
        assertTrue(dao.guardarToken(colgado, ana.getId(), ana.getCorreo(), OffsetDateTime.now().plusHours(24)));
        envejecer(colgado);
        dao.liberarEnviosColgados(ana.getId(), OffsetDateTime.now().minusMinutes(2));
        assertTrue(dao.guardarToken(hash(), ana.getId(), ana.getCorreo(), OffsetDateTime.now().plusHours(24)));
    }

    @Test
    void elAvisoAceptadoGuardaElProveedor() {
        Usuario ana = usuario("ana-" + UUID.randomUUID() + "@example.com");
        Oferta oferta = oferta();
        new SeguimientoDaoJdbc(fuente).activar(nuevo(ana.getId(), oferta.getId(), token()));
        AvisoSeguimientoDao dao = new AvisoSeguimientoDaoJdbc(fuente);
        AvisoSeguimiento aviso = new AvisoSeguimiento();
        aviso.setUsuarioId(ana.getId());
        aviso.setOfertaId(oferta.getId());
        aviso.setTipo(AvisoSeguimiento.Tipo.BAJA_PRECIO);
        aviso.setClaveEvento("prueba:" + UUID.randomUUID());
        aviso.setCorreoDestino(ana.getCorreo());
        long id = dao.insertarSiEsNuevo(aviso).orElseThrow();
        dao.marcarAceptado(id, new ResultadoEnvio("resend", "re_123"));
        try (var conexion = fuente.getConnection(); var sentencia = conexion.prepareStatement(
                "SELECT estado, proveedor_correo, id_mensaje_proveedor, enviado_en FROM aviso_seguimiento WHERE id = ?")) {
            sentencia.setLong(1, id);
            try (var fila = sentencia.executeQuery()) {
                assertTrue(fila.next());
                assertEquals("ENVIADO", fila.getString(1));
                assertEquals("resend", fila.getString(2));
                assertEquals("re_123", fila.getString(3));
                assertNotNull(fila.getObject(4));
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void lasRondasSeRegistranYNoCorrenDosALaVez() throws Exception {
        EjecucionTareaDao dao = new EjecucionTareaDaoJdbc(fuente);
        String tarea = "p-" + UUID.randomUUID().toString().substring(0, 8);
        long id = dao.iniciar(tarea, EjecucionTareaDao.Origen.CRON);
        dao.terminar(id, EjecucionTareaDao.Estado.TERMINADA, 3, 1, 0, 1, 1, null);
        dao.omitida(tarea, EjecucionTareaDao.Origen.INTERNA, "Ya había otra ronda en curso");
        List<EjecucionTareaDao.Ejecucion> recientes = dao.recientes(tarea, 10);
        assertEquals(2, recientes.size());
        assertTrue(recientes.stream().anyMatch(e -> e.estado() == EjecucionTareaDao.Estado.TERMINADA
                && e.revisados() == 3 && e.origen() == EjecucionTareaDao.Origen.CRON && e.terminadaEn() != null));

        // Mientras una ronda tiene el bloqueo, otra (desde otro hilo, como otra copia del servidor) no entra.
        java.util.concurrent.CountDownLatch dentro = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch salir = new java.util.concurrent.CountDownLatch(1);
        Thread primera = new Thread(() -> dao.conBloqueo(tarea, () -> {
            dentro.countDown();
            try {
                salir.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "primera";
        }));
        primera.start();
        assertTrue(dentro.await(10, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(dao.conBloqueo(tarea, () -> "segunda").isEmpty(), "La segunda no corre mientras la primera sigue");
        salir.countDown();
        primera.join(10_000);
        assertEquals("tercera", dao.conBloqueo(tarea, () -> "tercera").orElseThrow(), "Al terminar se suelta");
    }

    private static String hash() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
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
