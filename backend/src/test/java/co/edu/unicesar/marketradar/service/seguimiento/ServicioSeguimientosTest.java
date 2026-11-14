package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.dao.SeguimientoDao;
import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.Seguimiento;
import co.edu.unicesar.marketradar.domain.Usuario;
import co.edu.unicesar.marketradar.exception.CorreoSinVerificarException;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioSeguimientosTest {

    private SeguimientoDao seguimientoDao;
    private AvisosEnMemoria avisos;
    private OfertaDao ofertaDao;
    private ServicioVerificacionCorreo verificacion;
    private TransporteCorreoEnMemoria transporte;
    private ServicioSeguimientos servicio;
    private Oferta oferta;

    @BeforeEach
    void preparar() {
        seguimientoDao = mock(SeguimientoDao.class);
        avisos = new AvisosEnMemoria();
        ofertaDao = mock(OfertaDao.class);
        UsuarioDao usuarioDao = mock(UsuarioDao.class);
        verificacion = mock(ServicioVerificacionCorreo.class);
        transporte = new TransporteCorreoEnMemoria();
        Usuario ana = new Usuario();
        ana.setId(1L);
        ana.setCorreo("ana@example.com");
        ana.setNombres("Ana");
        when(usuarioDao.buscarPorId(1L)).thenReturn(Optional.of(ana));
        oferta = new Oferta();
        oferta.setId(10L);
        oferta.setIdExterno("g-10");
        oferta.setTitulo("Apple iPhone 13 128GB");
        oferta.setPrecioActual(new BigDecimal("3000000"));
        oferta.setMoneda("COP");
        oferta.setVendedor("Éxito");
        oferta.setCondicion("nuevo");
        oferta.setConsultadoEn(OffsetDateTime.now());
        when(ofertaDao.buscarPorId(10L)).thenReturn(Optional.of(oferta));
        when(seguimientoDao.activar(any())).thenAnswer(inv -> {
            Seguimiento s = inv.getArgument(0);
            s.setVersion(1);
            s.setEstado(Seguimiento.Estado.ACTIVO);
            return s;
        });
        when(seguimientoDao.asegurarToken(anyLong(), anyLong(), anyString())).thenReturn(ServicioEnvioAvisosTest.TOKEN);
        ServicioEnvioAvisos envio = new ServicioEnvioAvisos(avisos, seguimientoDao, transporte,
                ServicioEnvioAvisosTest.correo(), ServicioEnvioAvisosTest.propiedades());
        servicio = new ServicioSeguimientos(seguimientoDao, avisos, ofertaDao, usuarioDao, verificacion, envio,
                ServicioEnvioAvisosTest.propiedades());
    }

    private void listaConLaOferta() {
        Seguimiento s = new Seguimiento();
        s.setUsuarioId(1);
        s.setOfertaId(10);
        s.setEstado(Seguimiento.Estado.ACTIVO);
        when(seguimientoDao.listarDelUsuario(1L))
                .thenReturn(List.of())
                .thenReturn(List.of(new SeguimientoDao.Detalle(s, oferta, "googleshopping")));
    }

    @Test
    void sinCorreoVerificadoNoSePuedeSeguir() {
        when(verificacion.estaVerificado(1L)).thenReturn(false);
        assertThrows(CorreoSinVerificarException.class,
                () -> servicio.seguir(1L, 10L, Seguimiento.TipoAviso.BAJA_PRECIO, null, "iphone 13"));
        verify(seguimientoDao, never()).activar(any());
    }

    @Test
    void alSeguirGuardaLaReferenciaYMandaUnaConfirmacion() {
        when(verificacion.estaVerificado(1L)).thenReturn(true);
        listaConLaOferta();
        ServicioSeguimientos.Activacion a = servicio.seguir(1L, 10L, Seguimiento.TipoAviso.BAJA_PRECIO, null, " iphone  13 ");

        ArgumentCaptor<Seguimiento> guardado = ArgumentCaptor.forClass(Seguimiento.class);
        verify(seguimientoDao).activar(guardado.capture());
        assertEquals(1L, guardado.getValue().getUsuarioId(), "Queda a nombre de quien tiene la sesión");
        assertEquals(new BigDecimal("3000000"), guardado.getValue().getPrecioReferencia());
        assertEquals("Éxito", guardado.getValue().getVendedorReferencia());
        assertEquals("iphone 13", guardado.getValue().getTerminoBusqueda());
        assertTrue(guardado.getValue().getVarianteReferencia().contains("128"));
        assertEquals(AvisoSeguimiento.Estado.ENVIADO, a.confirmacion());
        assertEquals(1, transporte.enviados.size());
        assertTrue(transporte.enviados.get(0).asunto().toLowerCase().contains("seguimiento")
                || transporte.enviados.get(0).texto().contains("iPhone 13"));
    }

    @Test
    void sinServidorDeCorreoLaConfirmacionQuedaPendienteYSeDice() {
        when(verificacion.estaVerificado(1L)).thenReturn(true);
        transporte.configurado = false;
        listaConLaOferta();
        ServicioSeguimientos.Activacion a = servicio.seguir(1L, 10L, Seguimiento.TipoAviso.BAJA_PRECIO, null, null);
        assertEquals(AvisoSeguimiento.Estado.PENDIENTE, a.confirmacion());
        assertTrue(transporte.enviados.isEmpty());
    }

    @Test
    void elPrecioObjetivoDebeSerMenorQueElDeHoy() {
        assertThrows(ValidacionException.class, () -> ServicioSeguimientos.validarObjetivo(
                Seguimiento.TipoAviso.PRECIO_OBJETIVO, new BigDecimal("3000000"), new BigDecimal("3000000")));
        assertThrows(ValidacionException.class, () -> ServicioSeguimientos.validarObjetivo(
                Seguimiento.TipoAviso.PRECIO_OBJETIVO, null, new BigDecimal("3000000")));
        assertEquals(new BigDecimal("2500000.00"), ServicioSeguimientos.validarObjetivo(
                Seguimiento.TipoAviso.PRECIO_OBJETIVO, new BigDecimal("2500000"), new BigDecimal("3000000")));
    }

    @Test
    void seguirOtraVezNoReiniciaLaReferenciaNiMandaOtraConfirmacion() {
        when(verificacion.estaVerificado(1L)).thenReturn(true);
        Seguimiento existente = new Seguimiento();
        existente.setUsuarioId(1);
        existente.setOfertaId(10);
        existente.setEstado(Seguimiento.Estado.ACTIVO);
        existente.setTipoAviso(Seguimiento.TipoAviso.BAJA_PRECIO);
        when(seguimientoDao.buscar(1L, 10L)).thenReturn(Optional.of(existente));
        when(seguimientoDao.actualizarPreferencias(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
        when(seguimientoDao.listarDelUsuario(1L))
                .thenReturn(List.of(new SeguimientoDao.Detalle(existente, oferta, "googleshopping")));

        ServicioSeguimientos.Activacion a = servicio.seguir(1L, 10L, Seguimiento.TipoAviso.PRECIO_OBJETIVO,
                new BigDecimal("2500000"), null);

        assertEquals(AvisoSeguimiento.Estado.DESCARTADO, a.confirmacion());
        verify(seguimientoDao, never()).activar(any());
        verify(seguimientoDao).actualizarPreferencias(1L, 10L, Seguimiento.TipoAviso.PRECIO_OBJETIVO,
                new BigDecimal("2500000.00"), Seguimiento.Estado.ACTIVO);
        assertTrue(transporte.enviados.isEmpty());
    }

    @Test
    void nadieCambiaUnSeguimientoAjeno() {
        // La persona 2 no sigue la oferta 10: el DAO solo busca por (usuario, oferta).
        when(seguimientoDao.buscar(2L, 10L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.cambiar(2L, 10L, null, null, Seguimiento.Estado.PAUSADO));
        verify(seguimientoDao, never()).actualizarPreferencias(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void cancelarPorEnlaceDescartaLosAvisosPendientes() {
        AvisoSeguimiento pendiente = new AvisoSeguimiento();
        pendiente.setUsuarioId(1);
        pendiente.setOfertaId(10);
        pendiente.setTipo(AvisoSeguimiento.Tipo.BAJA_PRECIO);
        pendiente.setClaveEvento("baja:1:10:1:2700000");
        avisos.insertarSiEsNuevo(pendiente);
        Seguimiento cancelado = new Seguimiento();
        cancelado.setUsuarioId(1);
        cancelado.setOfertaId(10);
        when(seguimientoDao.cancelarPorToken(ServicioEnvioAvisosTest.TOKEN)).thenReturn(Optional.of(cancelado));

        assertEquals("Apple iPhone 13 128GB", servicio.cancelarPorEnlace(ServicioEnvioAvisosTest.TOKEN));
        assertEquals(AvisoSeguimiento.Estado.DESCARTADO, avisos.avisos.get(0).getEstado());
    }

    @Test
    void unEnlaceInvalidoNoCancelaNada() {
        assertThrows(ValidacionException.class, () -> servicio.cancelarPorEnlace("corto"));
        when(seguimientoDao.cancelarPorToken(anyString())).thenReturn(Optional.empty());
        assertThrows(ValidacionException.class, () -> servicio.cancelarPorEnlace(ServicioEnvioAvisosTest.TOKEN));
    }
}
