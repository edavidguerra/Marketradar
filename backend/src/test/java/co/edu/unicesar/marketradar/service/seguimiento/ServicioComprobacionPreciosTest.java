package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.dao.SeguimientoDao;
import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioComprobacionPreciosTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.ofInstant(ServicioEnvioAvisosTest.AHORA, ZoneOffset.UTC);
    private static final String TITULO = "Apple iPhone 13 128GB";

    private SeguimientoDao seguimientoDao;
    private AvisosEnMemoria avisos;
    private ComprobadorOfertas comprobador;
    private TransporteCorreoEnMemoria transporte;
    private ServicioComprobacionPrecios servicio;

    @BeforeEach
    void preparar() {
        seguimientoDao = mock(SeguimientoDao.class);
        when(seguimientoDao.guardarComprobacion(any())).thenReturn(true);
        when(seguimientoDao.asegurarToken(anyLong(), anyLong(), anyString())).thenReturn(ServicioEnvioAvisosTest.TOKEN);
        avisos = new AvisosEnMemoria();
        comprobador = mock(ComprobadorOfertas.class);
        transporte = new TransporteCorreoEnMemoria();
        Clock reloj = Clock.fixed(ServicioEnvioAvisosTest.AHORA, ZoneOffset.UTC);
        ServicioEnvioAvisos envio = new ServicioEnvioAvisos(avisos, seguimientoDao, transporte,
                ServicioEnvioAvisosTest.correo(), ServicioEnvioAvisosTest.propiedades(), reloj);
        servicio = new ServicioComprobacionPrecios(seguimientoDao, avisos, comprobador, envio,
                ServicioEnvioAvisosTest.propiedades(), reloj);
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

    @Test
    void unaBajadaCreaUnSoloAvisoAunqueLaRondaSeRepita() {
        // La primera ronda crea el aviso pero no logra guardar la comprobación (como si se reiniciara el servidor).
        when(seguimientoDao.guardarComprobacion(any())).thenReturn(false);
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt()))
                .thenReturn(List.of(pendiente(10, "iphone 13")))
                .thenReturn(List.of(pendiente(10, "iphone 13")));
        when(comprobador.consultar("googleshopping", "iphone 13")).thenReturn(List.of(encontrada(10, "2700000")));

        ServicioComprobacionPrecios.Resumen primera = servicio.ejecutarRonda();
        ServicioComprobacionPrecios.Resumen segunda = servicio.ejecutarRonda();

        assertEquals(1, primera.avisosNuevos());
        assertEquals(0, segunda.avisosNuevos());
        assertEquals(1, avisos.avisos.size());
        assertEquals(1, transporte.enviados.size(), "Se manda un solo correo");
        assertEquals(AvisoSeguimiento.Tipo.BAJA_PRECIO, avisos.avisos.get(0).getTipo());
    }

    @Test
    void unaFallaDeLaTiendaNoEsDescuentoYSeReintentaMasTarde() {
        SeguimientoDao.Pendiente p = pendiente(10, "iphone 13");
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt())).thenReturn(List.of(p));
        when(comprobador.consultar(anyString(), anyString())).thenThrow(new MarketplaceException("googleshopping", "caída"));

        servicio.ejecutarRonda();

        assertTrue(avisos.avisos.isEmpty());
        assertEquals(Seguimiento.Resultado.ERROR_TIENDA, p.seguimiento().getResultado());
        assertEquals(1, p.seguimiento().getFallosConsecutivos());
        assertEquals(AHORA.plusMinutes(30), p.seguimiento().getProximaComprobacion());
        assertEquals(new BigDecimal("3000000"), p.seguimiento().getPrecioReferencia(), "La referencia no cambia");
    }

    @Test
    void siLaOfertaYaNoApareceNoSeAvisa() {
        SeguimientoDao.Pendiente p = pendiente(10, "iphone 13");
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt())).thenReturn(List.of(p));
        when(comprobador.consultar(anyString(), anyString())).thenReturn(List.of(encontrada(99, "100000")));
        servicio.ejecutarRonda();
        assertTrue(avisos.avisos.isEmpty());
        assertEquals(Seguimiento.Resultado.NO_ENCONTRADA, p.seguimiento().getResultado());
    }

    @Test
    void unaBusquedaSirveParaVariosSeguimientosYRespetaElCupo() {
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt())).thenReturn(List.of(
                pendiente(1, "iphone 13"), pendiente(2, "iphone 13"), pendiente(3, "tv 1"), pendiente(4, "tv 2"),
                pendiente(5, "tv 3"), pendiente(6, "tv 4"), pendiente(7, "tv 5"), pendiente(8, "tv 6")));
        when(comprobador.consultar(anyString(), anyString())).thenReturn(List.of());

        ServicioComprobacionPrecios.Resumen r = servicio.ejecutarRonda();

        assertEquals(5, r.consultas(), "Máximo 5 consultas por ronda");
        assertEquals(6, r.revisados());
        assertEquals(2, r.pospuestos());
        verify(comprobador, times(1)).consultar("googleshopping", "iphone 13");
        verify(seguimientoDao).posponer(eq(1L), eq(8L), eq(AHORA.plus(ServicioComprobacionPrecios.ESPERA_SIN_CUPO)));
    }

    @Test
    void sinCorreoVerificadoNoSeCreaAviso() {
        SeguimientoDao.Pendiente p = pendiente(10, "iphone 13");
        SeguimientoDao.Pendiente sinVerificar = new SeguimientoDao.Pendiente(p.seguimiento(), p.oferta(),
                p.codigoMarketplace(), p.correo(), false);
        when(seguimientoDao.tomarPendientes(anyInt(), anyInt())).thenReturn(List.of(sinVerificar));
        when(comprobador.consultar(anyString(), anyString())).thenReturn(List.of(encontrada(10, "2000000")));
        servicio.ejecutarRonda();
        assertTrue(avisos.avisos.isEmpty());
    }

    @Test
    void siOtraCopiaTieneElBloqueoLaRondaSeOmiteYQuedaRegistrada() {
        co.edu.unicesar.marketradar.dao.EjecucionTareaDao ocupado = mock(co.edu.unicesar.marketradar.dao.EjecucionTareaDao.class);
        when(ocupado.conBloqueo(anyString(), any())).thenReturn(java.util.Optional.empty());
        Clock reloj = Clock.fixed(ServicioEnvioAvisosTest.AHORA, ZoneOffset.UTC);
        ServicioEnvioAvisos envio = new ServicioEnvioAvisos(avisos, seguimientoDao, transporte,
                ServicioEnvioAvisosTest.correo(), ServicioEnvioAvisosTest.propiedades(), reloj);
        ServicioComprobacionPrecios s = new ServicioComprobacionPrecios(seguimientoDao, avisos, comprobador, envio,
                ServicioEnvioAvisosTest.propiedades(), reloj, ocupado);
        ServicioComprobacionPrecios.Resumen r = s.ejecutarRonda(co.edu.unicesar.marketradar.dao.EjecucionTareaDao.Origen.CRON);
        org.junit.jupiter.api.Assertions.assertFalse(r.ejecutada());
        verify(ocupado).omitida(eq(ServicioComprobacionPrecios.TAREA),
                eq(co.edu.unicesar.marketradar.dao.EjecucionTareaDao.Origen.CRON), anyString());
        verify(seguimientoDao, org.mockito.Mockito.never()).tomarPendientes(anyInt(), anyInt());
    }
}
