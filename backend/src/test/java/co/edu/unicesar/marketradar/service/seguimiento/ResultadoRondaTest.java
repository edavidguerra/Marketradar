package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.controller.ControladorTareas;
import co.edu.unicesar.marketradar.dao.EjecucionTareaDao;
import co.edu.unicesar.marketradar.dao.EjecucionTareaDao.ResultadoConsultas;
import co.edu.unicesar.marketradar.dao.SeguimientoDao;
import co.edu.unicesar.marketradar.domain.NormalizadorEspecificaciones;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.Seguimiento;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Una ronda terminada no es lo mismo que una comprobación de precios exitosa. Corre rondas reales del servicio con
 * tiendas y correo simulados (sin red, sin base de datos y sin el cron de producción), las registra en memoria como lo
 * haría {@code ejecucion_tarea} y lee el estado con el mismo cálculo que {@code GET /api/v1/tareas/seguimientos}.
 */
class ResultadoRondaTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.ofInstant(ServicioEnvioAvisosTest.AHORA, ZoneOffset.UTC);
    private static final String TITULO = "Apple iPhone 13 128GB";

    /** Registro de rondas en memoria, de la más reciente a la más antigua. */
    static final class RegistroEnMemoria implements EjecucionTareaDao {
        final List<Ejecucion> filas = new ArrayList<>();
        private OffsetDateTime inicio = AHORA;

        @Override
        public long iniciar(String tarea, Origen origen) {
            return filas.size() + 1;
        }

        @Override
        public void terminar(long id, Estado estado, Conteo c, String detalle) {
            filas.add(0, new Ejecucion(id, ServicioComprobacionPrecios.TAREA, Origen.CRON, estado, inicio,
                    inicio.plusSeconds(3), c.revisados(), c.consultas(), c.pospuestos(), c.avisosNuevos(),
                    c.correosAceptados(), detalle, c.consultasExitosas(), c.consultasFallidas(), c.noEncontradas(),
                    c.correosRechazados(), c.resultado()));
        }

        @Override
        public void omitida(String tarea, Origen origen, String motivo) {
        }

        @Override
        public List<Ejecucion> recientes(String tarea, int limite) {
            return filas;
        }

        @Override
        public <T> Optional<T> conBloqueo(String tarea, Supplier<T> trabajo) {
            return Optional.ofNullable(trabajo.get());
        }
    }

    private SeguimientoDao seguimientoDao;
    private ComprobadorOfertas comprobador;
    private TransporteCorreoEnMemoria transporte;
    private AvisosEnMemoria avisos;
    private RegistroEnMemoria registro;
    private ServicioComprobacionPrecios servicio;

    @BeforeEach
    void preparar() {
        seguimientoDao = mock(SeguimientoDao.class);
        when(seguimientoDao.guardarComprobacion(any())).thenReturn(true);
        when(seguimientoDao.asegurarToken(anyLong(), anyLong(), anyString())).thenReturn(ServicioEnvioAvisosTest.TOKEN);
        comprobador = mock(ComprobadorOfertas.class);
        transporte = new TransporteCorreoEnMemoria();
        avisos = new AvisosEnMemoria();
        registro = new RegistroEnMemoria();
        Clock reloj = Clock.fixed(ServicioEnvioAvisosTest.AHORA, ZoneOffset.UTC);
        ServicioEnvioAvisos envio = new ServicioEnvioAvisos(avisos, seguimientoDao, transporte,
                ServicioEnvioAvisosTest.correo(), ServicioEnvioAvisosTest.propiedades(), reloj);
        servicio = new ServicioComprobacionPrecios(seguimientoDao, avisos, comprobador, envio,
                ServicioEnvioAvisosTest.propiedades(), reloj, registro);
    }

    private static SeguimientoDao.Pendiente pendiente(long ofertaId, String termino) {
        Seguimiento s = new Seguimiento();
        s.setUsuarioId(1);
        s.setOfertaId(ofertaId);
        s.setTipoAviso(Seguimiento.TipoAviso.BAJA_PRECIO);
        s.setEstado(Seguimiento.Estado.ACTIVO);
        s.setPrecioReferencia(new BigDecimal("3000000"));
        s.setMonedaReferencia("COP");
        s.setVendedorReferencia("Éxito");
        s.setCondicionReferencia("nuevo");
        s.setVarianteReferencia(NormalizadorEspecificaciones.claveVariante(TITULO, List.of()));
        s.setReferenciaDesde(AHORA.minusDays(1));
        s.setTerminoBusqueda(termino);
        Oferta o = new Oferta();
        o.setId(ofertaId);
        o.setIdExterno("g-" + ofertaId);
        o.setTitulo(TITULO);
        return new SeguimientoDao.Pendiente(s, o, "googleshopping", "ana@example.com", true);
    }

    private static OfertaNormalizada encontrada(long ofertaId, String precio) {
        return OfertaNormalizada.builder().codigoMarketplace("googleshopping").idExterno("g-" + ofertaId)
                .titulo(TITULO).precio(new BigDecimal(precio)).moneda("COP").vendedor("Éxito").condicion("nuevo")
                .urlDestino("https://www.exito.com/iphone").consultadoEn(AHORA).build();
    }

    private Map<String, Object> estado() {
        return ControladorTareas.resumen(registro.recientes(ServicioComprobacionPrecios.TAREA, 50), 15,
                AHORA.plusMinutes(1));
    }

    private Map<?, ?> ultima(Map<String, Object> estado) {
        return (Map<?, ?>) estado.get("ultima_ejecucion");
    }

    @Test
    void siTodasLasConsultasFallanLaRondaTerminaPeroNoEsUnaComprobacionCorrecta() {
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt()))
                .thenReturn(List.of(pendiente(1, "iphone 13"), pendiente(2, "televisor")));
        when(comprobador.consultar(anyString(), anyString())).thenThrow(new MarketplaceException("googleshopping", "caída"));

        ServicioComprobacionPrecios.Resumen r = servicio.ejecutarRonda(EjecucionTareaDao.Origen.CRON);

        assertEquals(2, r.consultas());
        assertEquals(0, r.consultasExitosas());
        assertEquals(2, r.consultasFallidas());
        assertEquals(ResultadoConsultas.FALLIDAS, r.resultadoConsultas());
        Map<String, Object> estado = estado();
        assertEquals("terminada", ultima(estado).get("estado"), "Técnicamente la ronda terminó");
        assertEquals(Boolean.FALSE, estado.get("comprobacion_correcta"), "Antes de la corrección era true");
        assertEquals("fallidas", estado.get("resultado_ultima_comprobacion"));
        assertNull(estado.get("ultima_ejecucion_exitosa"), "Ninguna consulta respondió");
        assertEquals("consultas_fallidas", estado.get("estado_general"));
    }

    @Test
    void unaConsultaFuncionaYOtraFallaQuedaComoParcialSinPerderLaQueFunciono() {
        SeguimientoDao.Pendiente bien = pendiente(1, "iphone 13");
        SeguimientoDao.Pendiente mal = pendiente(2, "televisor");
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt())).thenReturn(List.of(bien, mal));
        when(comprobador.consultar("googleshopping", "iphone 13")).thenReturn(List.of(encontrada(1, "2990000")));
        when(comprobador.consultar("googleshopping", "televisor"))
                .thenThrow(new MarketplaceException("googleshopping", "tiempo agotado"));

        ServicioComprobacionPrecios.Resumen r = servicio.ejecutarRonda(EjecucionTareaDao.Origen.CRON);

        assertEquals(1, r.consultasExitosas());
        assertEquals(1, r.consultasFallidas());
        assertEquals(ResultadoConsultas.PARCIALES, r.resultadoConsultas());
        assertNotEquals(Seguimiento.Resultado.ERROR_TIENDA, bien.seguimiento().getResultado(),
                "La comprobación que sí funcionó se conserva");
        assertEquals(Seguimiento.Resultado.ERROR_TIENDA, mal.seguimiento().getResultado());
        Map<String, Object> estado = estado();
        assertEquals(Boolean.FALSE, estado.get("comprobacion_correcta"), "Parcial no es correcta");
        assertEquals("consultas_parciales", estado.get("estado_general"));
        Map<?, ?> exitosa = (Map<?, ?>) estado.get("ultima_ejecucion_exitosa");
        assertNotNull(exitosa, "Sí hubo una consulta que respondió");
        assertEquals(1, exitosa.get("consultas_exitosas"));
        assertEquals(1, exitosa.get("consultas_fallidas"));
    }

    @Test
    void sinSeguimientosPendientesNoEsUnFalloNiUnaComprobacion() {
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt())).thenReturn(List.of());

        ServicioComprobacionPrecios.Resumen r = servicio.ejecutarRonda(EjecucionTareaDao.Origen.CRON);

        assertEquals(ResultadoConsultas.SIN_TRABAJO, r.resultadoConsultas());
        Map<String, Object> estado = estado();
        assertNull(estado.get("comprobacion_correcta"), "No se consultó ningún precio: no se afirma nada");
        assertNull(estado.get("ultima_comprobacion_de_precios"));
        assertEquals(0, estado.get("fallidas_seguidas"));
        assertEquals("sin_trabajo", ((Map<?, ?>) estado.get("ultima_ronda_terminada")).get("resultado_consultas"));
        assertNotEquals("con_errores", estado.get("estado_general"));
        assertNotEquals("al_dia", estado.get("estado_general"));
    }

    @Test
    void laConsultaFuncionaPeroLaOfertaNoApareceNoEsErrorDeTransporte() {
        SeguimientoDao.Pendiente p = pendiente(10, "iphone 13");
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt())).thenReturn(List.of(p));
        when(comprobador.consultar(anyString(), anyString())).thenReturn(List.of(encontrada(99, "100000")));

        ServicioComprobacionPrecios.Resumen r = servicio.ejecutarRonda(EjecucionTareaDao.Origen.CRON);

        assertEquals(1, r.consultasExitosas());
        assertEquals(0, r.consultasFallidas());
        assertEquals(1, r.noEncontradas());
        assertEquals(ResultadoConsultas.CORRECTAS, r.resultadoConsultas());
        assertEquals(Seguimiento.Resultado.NO_ENCONTRADA, p.seguimiento().getResultado());
        Map<String, Object> estado = estado();
        assertEquals(Boolean.TRUE, estado.get("comprobacion_correcta"));
        assertEquals(1, ultima(estado).get("ofertas_no_encontradas"));
        assertEquals(0, ultima(estado).get("consultas_fallidas"));
    }

    @Test
    void lasConsultasFuncionanYElProveedorDeCorreoRechazaUnEnvio() {
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt())).thenReturn(List.of(pendiente(10, "iphone 13")));
        when(comprobador.consultar(anyString(), anyString())).thenReturn(List.of(encontrada(10, "2700000")));
        transporte.fallosPendientes = 1;
        transporte.fallosDefinitivos = true;

        ServicioComprobacionPrecios.Resumen r = servicio.ejecutarRonda(EjecucionTareaDao.Origen.CRON);

        assertEquals(1, r.avisosNuevos());
        assertEquals(0, r.correosEnviados());
        assertEquals(1, r.correosRechazados());
        assertEquals(ResultadoConsultas.CORRECTAS, r.resultadoConsultas(), "El correo no cambia el resultado de las consultas");
        Map<String, Object> estado = estado();
        assertEquals(Boolean.TRUE, estado.get("comprobacion_correcta"));
        assertEquals("rechazados", estado.get("envio_correo_ultima_ronda"));
        assertEquals(1, ultima(estado).get("correos_rechazados"));
        assertEquals(0, ultima(estado).get("correos_aceptados"));
    }

    @Test
    void laRespuestaDelCronSigueSiendoCorta() {
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt())).thenReturn(List.of(pendiente(1, "iphone 13")));
        when(comprobador.consultar(anyString(), anyString())).thenReturn(List.of(encontrada(1, "2990000")));
        ServicioComprobacionPrecios.Resumen r = servicio.ejecutarRonda(EjecucionTareaDao.Origen.CRON);
        assertEquals(true, String.valueOf(r).length() < 300, String.valueOf(r));
    }
}
