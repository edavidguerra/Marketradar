package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.config.PropiedadesSeguimientos;
import co.edu.unicesar.marketradar.correo.MensajeCorreo;
import co.edu.unicesar.marketradar.correo.PropiedadesCorreo;
import co.edu.unicesar.marketradar.dao.SeguimientoDao;
import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicioEnvioAvisosTest {

    static final Instant AHORA = Instant.parse("2026-11-14T15:00:00Z");
    static final String TOKEN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private AvisosEnMemoria avisos;
    private TransporteCorreoEnMemoria transporte;
    private ServicioEnvioAvisos servicio;

    static PropiedadesSeguimientos propiedades() {
        return new PropiedadesSeguimientos(true, 720, 15, 25, 5, BigDecimal.ONE, 3, 48, 20, "");
    }

    static PropiedadesCorreo correo() {
        return new PropiedadesCorreo("smtp.example.com", 587, "u", "secreto", "avisos@example.com", null, true, false,
                10, "https://marketradar.example.com/");
    }

    @BeforeEach
    void preparar() {
        avisos = new AvisosEnMemoria();
        transporte = new TransporteCorreoEnMemoria();
        SeguimientoDao seguimientos = mock(SeguimientoDao.class);
        when(seguimientos.asegurarToken(anyLong(), anyLong(), anyString())).thenReturn(TOKEN);
        servicio = new ServicioEnvioAvisos(avisos, seguimientos, transporte, correo(), propiedades(),
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private long nuevoAviso(AvisoSeguimiento.Tipo tipo, String clave) {
        AvisoSeguimiento a = new AvisoSeguimiento();
        a.setUsuarioId(1);
        a.setOfertaId(10);
        a.setTipo(tipo);
        a.setClaveEvento(clave);
        a.setCorreoDestino("ana@example.com");
        a.setPrecioAnterior(new BigDecimal("3000000"));
        a.setPrecioActual(new BigDecimal("2700000"));
        a.setMoneda("COP");
        a.setComprobadoEn(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
        a.setCreadoEn(OffsetDateTime.ofInstant(AHORA.minusSeconds(60), ZoneOffset.UTC));
        return avisos.insertarSiEsNuevo(a).orElseThrow();
    }

    @Test
    void elCorreoDeBajadaTraeTodoLoQuePideLaPersona() {
        nuevoAviso(AvisoSeguimiento.Tipo.BAJA_PRECIO, "baja:1:10:1:2700000");
        assertEquals(1, servicio.enviarPendientes(10));
        MensajeCorreo m = transporte.enviados.get(0);
        assertEquals("ana@example.com", m.para());
        for (String esperado : new String[]{"iPhone 13 128 GB", "Éxito", "3.000.000", "2.700.000", "COP",
                "https://www.exito.com/iphone-13", "https://marketradar.example.com/cancelar-seguimiento.html#token=" + TOKEN,
                "14 de noviembre de 2026"}) {
            assertTrue(m.texto().contains(esperado), "Falta en el texto: " + esperado + "\n" + m.texto());
        }
        assertTrue(m.html().contains("cancelar-seguimiento.html#token=" + TOKEN));
        assertEquals(AvisoSeguimiento.Estado.ENVIADO, avisos.avisos.get(0).getEstado());
    }

    @Test
    void unAvisoEnviadoNoSeMandaOtraVez() {
        nuevoAviso(AvisoSeguimiento.Tipo.BAJA_PRECIO, "baja:1:10:1:2700000");
        servicio.enviarPendientes(10);
        servicio.enviarPendientes(10);
        assertEquals(1, transporte.enviados.size());
        assertTrue(avisos.insertarSiEsNuevo(copia("baja:1:10:1:2700000")).isEmpty(), "La misma clave no se repite");
    }

    private static AvisoSeguimiento copia(String clave) {
        AvisoSeguimiento a = new AvisoSeguimiento();
        a.setClaveEvento(clave);
        return a;
    }

    @Test
    void sinServidorDeCorreoQuedaPendienteYNoSeFingeElEnvio() {
        transporte.configurado = false;
        long id = nuevoAviso(AvisoSeguimiento.Tipo.CONFIRMACION, "confirmacion:1:10:v1");
        assertEquals(AvisoSeguimiento.Estado.PENDIENTE, servicio.enviarAhora(id));
        assertTrue(transporte.enviados.isEmpty());
        AvisoSeguimiento a = avisos.avisos.get(0);
        assertEquals(0, a.getIntentos(), "No cuenta como intento");
        assertNull(a.getEnviadoEn());
        assertTrue(a.getUltimoError().contains("no tiene configurado"));
    }

    @Test
    void reintentaConEsperaYAlFinalQuedaFallido() {
        transporte.fallosPendientes = 5;
        long id = nuevoAviso(AvisoSeguimiento.Tipo.BAJA_PRECIO, "baja:1:10:1:2700000");
        assertEquals(AvisoSeguimiento.Estado.PENDIENTE, servicio.enviarAhora(id));
        OffsetDateTime primero = avisos.ultimoProximoIntento;
        assertEquals(AvisoSeguimiento.Estado.PENDIENTE, servicio.enviarAhora(id));
        assertTrue(avisos.ultimoProximoIntento.isAfter(primero), "La espera crece");
        assertEquals(AvisoSeguimiento.Estado.FALLIDO, servicio.enviarAhora(id));
        assertEquals(3, avisos.avisos.get(0).getIntentos());
        assertEquals(AvisoSeguimiento.Estado.DESCARTADO, servicio.enviarAhora(id), "Uno fallido ya no se toma");
        assertTrue(transporte.enviados.isEmpty());
    }

    @Test
    void siElSeguimientoSePausoOCanceloNoSeMandaElAviso() {
        avisos.estadoSeguimiento = "PAUSADO";
        long id = nuevoAviso(AvisoSeguimiento.Tipo.BAJA_PRECIO, "baja:1:10:1:2700000");
        assertEquals(AvisoSeguimiento.Estado.DESCARTADO, servicio.enviarAhora(id));
        assertTrue(transporte.enviados.isEmpty());
    }

    @Test
    void siElCorreoCambioOYaNoEstaVerificadoNoSeManda() {
        avisos.correoActual = "otra@example.com";
        assertEquals(AvisoSeguimiento.Estado.DESCARTADO,
                servicio.enviarAhora(nuevoAviso(AvisoSeguimiento.Tipo.BAJA_PRECIO, "a")));
        avisos.correoActual = "ana@example.com";
        avisos.correoVerificado = false;
        assertEquals(AvisoSeguimiento.Estado.DESCARTADO,
                servicio.enviarAhora(nuevoAviso(AvisoSeguimiento.Tipo.BAJA_PRECIO, "b")));
        assertTrue(transporte.enviados.isEmpty());
    }

    @Test
    void unAvisoViejoVenceEnVezDeMandarUnPrecioDesactualizado() {
        AvisoSeguimiento a = new AvisoSeguimiento();
        a.setUsuarioId(1);
        a.setOfertaId(10);
        a.setTipo(AvisoSeguimiento.Tipo.BAJA_PRECIO);
        a.setClaveEvento("viejo");
        a.setCorreoDestino("ana@example.com");
        a.setPrecioActual(BigDecimal.TEN);
        a.setCreadoEn(OffsetDateTime.ofInstant(AHORA.minusSeconds(49 * 3600), ZoneOffset.UTC));
        long id = avisos.insertarSiEsNuevo(a).orElseThrow();
        assertEquals(AvisoSeguimiento.Estado.DESCARTADO, servicio.enviarAhora(id));
        assertFalse(avisos.avisos.get(0).getUltimoError().isBlank());
    }
}
