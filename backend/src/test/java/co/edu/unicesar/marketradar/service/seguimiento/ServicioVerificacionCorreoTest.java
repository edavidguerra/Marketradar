package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.correo.MensajeCorreo;
import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.dao.VerificacionCorreoDao;
import co.edu.unicesar.marketradar.domain.Usuario;
import co.edu.unicesar.marketradar.exception.ServicioNoDisponibleException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El enlace de verificación: aceptado, fallido, reintento, límites y duplicados. Nada sale a internet. */
class ServicioVerificacionCorreoTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-11-14T15:00:00Z");

    private VerificacionCorreoDao dao;
    private TransporteCorreoEnMemoria transporte;
    private final List<java.time.Duration> pausas = new ArrayList<>();
    private ServicioVerificacionCorreo servicio;

    @BeforeEach
    void preparar() {
        dao = mock(VerificacionCorreoDao.class);
        UsuarioDao usuarios = mock(UsuarioDao.class);
        Usuario ana = new Usuario();
        ana.setId(7L);
        ana.setCorreo("ana@example.com");
        ana.setNombres("Ana");
        when(usuarios.buscarPorId(7L)).thenReturn(Optional.of(ana));
        when(dao.verificadoEn(7L)).thenReturn(Optional.empty());
        when(dao.guardarToken(anyString(), eq(7L), anyString(), any())).thenReturn(true);
        transporte = new TransporteCorreoEnMemoria();
        servicio = new ServicioVerificacionCorreo(dao, usuarios, transporte, ServicioEnvioAvisosTest.correo(),
                Clock.fixed(AHORA.toInstant(), ZoneOffset.UTC), pausas::add);
    }

    @Test
    void siElServicioLoAceptaSeGuardaComoAceptadoYNoSeDiceEntregado() {
        ServicioVerificacionCorreo.Solicitud solicitud = servicio.solicitar(7L);
        assertEquals(1, transporte.enviados.size());
        MensajeCorreo m = transporte.enviados.get(0);
        assertTrue(m.texto().contains("https://marketradar.example.com/verificar-correo.html#token="));
        assertTrue(m.texto().contains("Seguimientos"));
        verify(dao).marcarAceptado(anyString(), eq(1), eq("memoria"), eq("id-1"));
        assertTrue(solicitud.mensaje().contains("aceptó"));
        assertTrue(solicitud.mensaje().contains("spam"));
        assertTrue(!solicitud.mensaje().contains("entreg"), "Aceptar no es entregar");
        assertEquals(AHORA.plusHours(24), solicitud.venceEn());
    }

    @Test
    void unFalloPasajeroSeReintentaUnaVez() {
        transporte.fallosPendientes = 1;
        servicio.solicitar(7L);
        assertEquals(1, transporte.enviados.size());
        assertEquals(1, pausas.size());
        verify(dao).marcarAceptado(anyString(), eq(2), eq("memoria"), anyString());
    }

    @Test
    void siFallaDosVecesQuedaFallidoYSeDiceSinSecretos() {
        transporte.fallosPendientes = 2;
        ServicioNoDisponibleException e = assertThrows(ServicioNoDisponibleException.class, () -> servicio.solicitar(7L));
        assertTrue(e.getMessage().contains("Intente de nuevo"));
        verify(dao).marcarFallido(anyString(), eq(2), eq("Fallo simulado"));
        verify(dao, never()).marcarAceptado(anyString(), anyInt(), anyString(), any());
    }

    @Test
    void unRechazoDefinitivoNoSeReintenta() {
        transporte.fallosPendientes = 1;
        transporte.fallosDefinitivos = true;
        ServicioNoDisponibleException e = assertThrows(ServicioNoDisponibleException.class, () -> servicio.solicitar(7L));
        assertTrue(e.getMessage().contains("administrador"));
        assertTrue(pausas.isEmpty());
        verify(dao).marcarFallido(anyString(), eq(1), anyString());
    }

    @Test
    void sinCorreoConfiguradoNoGuardaNiManda() {
        transporte.configurado = false;
        assertThrows(ServicioNoDisponibleException.class, () -> servicio.solicitar(7L));
        verify(dao, never()).guardarToken(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void respetaElLimiteDeEnlacesYDeSolicitudes() {
        when(dao.contarEnviados(eq(7L), any())).thenReturn(3);
        assertThrows(ValidacionException.class, () -> servicio.solicitar(7L));
        when(dao.contarEnviados(eq(7L), any())).thenReturn(0);
        when(dao.contarSolicitudes(eq(7L), any())).thenReturn(6);
        assertThrows(ValidacionException.class, () -> servicio.solicitar(7L), "Los fallos también tienen tope");
        assertTrue(transporte.enviados.isEmpty());
    }

    @Test
    void noMandaOtroEnlaceSiElAnteriorSeAceptoHaceMenosDeUnMinuto() {
        when(dao.ultimoEnvio(7L)).thenReturn(Optional.of(new VerificacionCorreoDao.Envio(
                VerificacionCorreoDao.EstadoEnvio.ACEPTADO, AHORA.minusSeconds(30), AHORA.minusSeconds(20),
                AHORA.plusHours(23), false, null)));
        assertThrows(ValidacionException.class, () -> servicio.solicitar(7L));
        assertTrue(transporte.enviados.isEmpty());
    }

    @Test
    void dosClicsSeguidosNoMandanDosCorreos() {
        when(dao.guardarToken(anyString(), eq(7L), anyString(), any())).thenReturn(false);
        ValidacionException e = assertThrows(ValidacionException.class, () -> servicio.solicitar(7L));
        assertTrue(e.getMessage().contains("Ya estamos mandando"));
        assertTrue(transporte.enviados.isEmpty());
    }

    @Test
    void liberaLosEnviosColgadosAntesDeContar() {
        servicio.solicitar(7L);
        verify(dao).liberarEnviosColgados(7L, AHORA.minusMinutes(2));
    }

    @Test
    void siYaEstaVerificadoNoMandaNada() {
        when(dao.verificadoEn(7L)).thenReturn(Optional.of(AHORA.minusDays(1)));
        assertThrows(ValidacionException.class, () -> servicio.solicitar(7L));
        assertTrue(transporte.enviados.isEmpty());
    }
}
