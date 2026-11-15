package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.dao.SeguimientoDao;
import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
import co.edu.unicesar.marketradar.domain.Seguimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Estados de los avisos: aceptado por el proveedor, fallo definitivo y pausa justo antes de mandar. */
class ServicioEnvioAvisosEstadosTest {

    private AvisosEnMemoria avisos;
    private TransporteCorreoEnMemoria transporte;
    private SeguimientoDao seguimientos;
    private ServicioEnvioAvisos servicio;

    @BeforeEach
    void preparar() {
        avisos = new AvisosEnMemoria();
        transporte = new TransporteCorreoEnMemoria();
        seguimientos = mock(SeguimientoDao.class);
        when(seguimientos.asegurarToken(anyLong(), anyLong(), anyString())).thenReturn(ServicioEnvioAvisosTest.TOKEN);
        servicio = new ServicioEnvioAvisos(avisos, seguimientos, transporte, ServicioEnvioAvisosTest.correo(),
                ServicioEnvioAvisosTest.propiedades(), Clock.fixed(ServicioEnvioAvisosTest.AHORA, ZoneOffset.UTC));
    }

    private long nuevoAviso(String clave) {
        AvisoSeguimiento a = new AvisoSeguimiento();
        a.setUsuarioId(1);
        a.setOfertaId(10);
        a.setTipo(AvisoSeguimiento.Tipo.BAJA_PRECIO);
        a.setClaveEvento(clave);
        a.setCorreoDestino("ana@example.com");
        a.setPrecioAnterior(new BigDecimal("3000000"));
        a.setPrecioActual(new BigDecimal("2700000"));
        a.setMoneda("COP");
        a.setCreadoEn(OffsetDateTime.ofInstant(ServicioEnvioAvisosTest.AHORA.minusSeconds(60), ZoneOffset.UTC));
        return avisos.insertarSiEsNuevo(a).orElseThrow();
    }

    private static Seguimiento seguimiento(Seguimiento.Estado estado) {
        Seguimiento s = new Seguimiento();
        s.setUsuarioId(1);
        s.setOfertaId(10);
        s.setEstado(estado);
        return s;
    }

    @Test
    void unRechazoDefinitivoNoSeReintenta() {
        nuevoAviso("baja:1");
        transporte.fallosPendientes = 1;
        transporte.fallosDefinitivos = true;
        assertEquals(0, servicio.enviarPendientes(10));
        assertEquals(AvisoSeguimiento.Estado.FALLIDO, avisos.avisos.get(0).getEstado(),
                "Una clave inválida o un remitente sin verificar no mejoran reintentando");
        assertEquals(1, avisos.avisos.get(0).getIntentos());
    }

    @Test
    void unFalloPasajeroQuedaPendienteParaOtroIntento() {
        nuevoAviso("baja:2");
        transporte.fallosPendientes = 1;
        assertEquals(0, servicio.enviarPendientes(10));
        assertEquals(AvisoSeguimiento.Estado.PENDIENTE, avisos.avisos.get(0).getEstado());
        assertEquals(1, avisos.avisos.get(0).getIntentos());
    }

    @Test
    void siLaPersonaPausaMientrasSeApartaElAvisoNoSeManda() {
        nuevoAviso("baja:3");
        // La ronda tomó el aviso cuando el seguimiento estaba activo, pero justo antes de mandar ya está pausado.
        when(seguimientos.buscar(1, 10)).thenReturn(Optional.of(seguimiento(Seguimiento.Estado.PAUSADO)));
        assertEquals(0, servicio.enviarPendientes(10));
        assertTrue(transporte.enviados.isEmpty());
        assertEquals(AvisoSeguimiento.Estado.DESCARTADO, avisos.avisos.get(0).getEstado());
    }

    @Test
    void siLaPersonaCancelaTampocoSeManda() {
        nuevoAviso("baja:4");
        when(seguimientos.buscar(1, 10)).thenReturn(Optional.of(seguimiento(Seguimiento.Estado.CANCELADO)));
        assertEquals(0, servicio.enviarPendientes(10));
        assertTrue(transporte.enviados.isEmpty());
    }

    @Test
    void elCorreoLlevaAlApartadoSeguimientosYUsaSuNombre() {
        nuevoAviso("baja:5");
        when(seguimientos.buscar(1, 10)).thenReturn(Optional.of(seguimiento(Seguimiento.Estado.ACTIVO)));
        assertEquals(1, servicio.enviarPendientes(10));
        String texto = transporte.enviados.get(0).texto();
        assertTrue(texto.contains("https://marketradar.example.com/usuario.html#seguimientos"), texto);
        assertTrue(texto.contains("Abrir Seguimientos"), texto);
        assertTrue(transporte.enviados.get(0).claveIdempotencia().startsWith("aviso-"));
    }
}
