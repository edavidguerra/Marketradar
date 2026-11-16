package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.config.PropiedadesSeguimientos;
import co.edu.unicesar.marketradar.dao.EjecucionTareaDao;
import co.edu.unicesar.marketradar.service.seguimiento.ServicioComprobacionPrecios;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControladorTareasTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-14T10:00:00Z");
    private static final Clock RELOJ = Clock.fixed(T0.toInstant(), ZoneOffset.UTC);

    private static PropiedadesSeguimientos propiedades(String token) {
        return new PropiedadesSeguimientos(true, 15, 15, 20, 10, new BigDecimal("1"), 3, 48, 20, token);
    }

    private static EjecucionTareaDao.Ejecucion ronda(int minutoInicio, EjecucionTareaDao.Estado estado) {
        return new EjecucionTareaDao.Ejecucion(minutoInicio, "seguimientos", EjecucionTareaDao.Origen.CRON, estado,
                T0.plusMinutes(minutoInicio), T0.plusMinutes(minutoInicio).plusSeconds(5), 1, 1, 0, 0, 0, null);
    }

    /** De la más reciente a la más antigua, como las devuelve el DAO. */
    private static List<EjecucionTareaDao.Ejecucion> rondas(int... minutos) {
        List<EjecucionTareaDao.Ejecucion> lista = new ArrayList<>();
        for (int i = minutos.length - 1; i >= 0; i--) {
            lista.add(ronda(minutos[i], EjecucionTareaDao.Estado.TERMINADA));
        }
        return lista;
    }

    @Test
    void conPocasRondasNoAfirmaLaFrecuencia() {
        Map<String, Object> r = ControladorTareas.resumen(rondas(0, 15), 15, T0.plusMinutes(16));
        assertEquals(2, r.get("rondas_registradas"));
        assertEquals(15L, r.get("mayor_espera_minutos"));
        assertNull(r.get("frecuencia_cumplida"), "Con dos rondas no hay datos suficientes");
    }

    @Test
    void cumpleLaFrecuenciaConMargenDeArranque() {
        Map<String, Object> r = ControladorTareas.resumen(rondas(0, 15, 34, 49), 15, T0.plusMinutes(50));
        assertEquals(Boolean.TRUE, r.get("frecuencia_cumplida"));
        assertEquals(19L, r.get("mayor_espera_minutos"));
    }

    @Test
    void unHuecoLargoDiceQueNoSeCumple() {
        Map<String, Object> r = ControladorTareas.resumen(rondas(0, 15, 75), 15, T0.plusMinutes(76));
        assertEquals(Boolean.FALSE, r.get("frecuencia_cumplida"));
        assertEquals(60L, r.get("mayor_espera_minutos"));
    }

    @Test
    void lasRondasOmitidasNoCuentanComoCorridas() {
        List<EjecucionTareaDao.Ejecucion> lista = new ArrayList<>(rondas(0, 15));
        lista.add(0, ronda(16, EjecucionTareaDao.Estado.OMITIDA));
        Map<String, Object> r = ControladorTareas.resumen(lista, 15, T0.plusMinutes(17));
        assertEquals(2, r.get("rondas_registradas"));
        assertEquals(3, ((List<?>) r.get("rondas")).size(), "Se muestran todas, también la omitida");
    }

    @Test
    void sinTokenConfiguradoLaRutaNoExisteYConTokenMaloSeRechaza() {
        ServicioComprobacionPrecios servicio = mock(ServicioComprobacionPrecios.class);
        EjecucionTareaDao dao = mock(EjecucionTareaDao.class);
        assertEquals(404, new ControladorTareas(servicio, propiedades(""), dao, RELOJ).seguimientos("x")
                .getStatusCode().value());
        ControladorTareas c = new ControladorTareas(servicio, propiedades("secreto-de-prueba"), dao, RELOJ);
        assertEquals(403, c.seguimientos(null).getStatusCode().value());
        assertEquals(403, c.seguimientos("secreto-de-prueb").getStatusCode().value());
        assertEquals(403, c.estado("otro").getStatusCode().value());
        verify(servicio, never()).ejecutarRonda(any(EjecucionTareaDao.Origen.class));
    }

    @Test
    void conTokenCorrectoCorreLaRondaComoCron() {
        ServicioComprobacionPrecios servicio = mock(ServicioComprobacionPrecios.class);
        ServicioComprobacionPrecios.Resumen resumen = new ServicioComprobacionPrecios.Resumen(true, 2, 1, 0, 0, 0, true);
        when(servicio.ejecutarRonda(EjecucionTareaDao.Origen.CRON)).thenReturn(resumen);
        ResponseEntity<Object> r = new ControladorTareas(servicio, propiedades("secreto-de-prueba"),
                mock(EjecucionTareaDao.class), RELOJ).seguimientos("secreto-de-prueba");
        assertEquals(200, r.getStatusCode().value());
        assertEquals(resumen, r.getBody());
        assertTrue(((ServicioComprobacionPrecios.Resumen) r.getBody()).ejecutada());
        assertFalse(r.getBody() instanceof Map);
    }

    @Test
    void rondasRegularesYRecientesEstanAlDia() {
        Map<String, Object> r = ControladorTareas.resumen(rondas(0, 15, 30, 45), 15, T0.plusMinutes(52));
        assertEquals(Boolean.TRUE, r.get("frecuencia_cumplida"));
        assertEquals(Boolean.TRUE, r.get("comprobacion_correcta"));
        assertEquals(7L, r.get("minutos_desde_ultima"));
        assertEquals("al_dia", r.get("estado_general"));
    }

    @Test
    void rondasPuntualesPeroSinActividadDespuesNoCumplen() {
        // Las cuatro rondas fueron puntuales, pero la última fue hace 6 horas: el cron dejó de llegar.
        Map<String, Object> r = ControladorTareas.resumen(rondas(0, 15, 30, 45), 15, T0.plusMinutes(45 + 360));
        assertEquals(Boolean.FALSE, r.get("frecuencia_cumplida"));
        assertEquals(360L, r.get("minutos_desde_ultima"));
        assertEquals(Boolean.FALSE, r.get("comprobacion_correcta"), "Tampoco hay una comprobación reciente");
        assertEquals("atrasado", r.get("estado_general"));
    }

    @Test
    void unaSolaRondaViejaYaDiceQueNoSeCumple() {
        Map<String, Object> r = ControladorTareas.resumen(rondas(0), 15, T0.plusMinutes(120));
        assertEquals(Boolean.FALSE, r.get("frecuencia_cumplida"));
    }

    @Test
    void erroresSeguidosATiempoNoSonComprobacionesCorrectas() {
        List<EjecucionTareaDao.Ejecucion> lista = new ArrayList<>();
        lista.add(ronda(45, EjecucionTareaDao.Estado.FALLIDA));
        lista.add(ronda(30, EjecucionTareaDao.Estado.FALLIDA));
        lista.add(ronda(15, EjecucionTareaDao.Estado.TERMINADA));
        lista.add(ronda(0, EjecucionTareaDao.Estado.TERMINADA));
        Map<String, Object> r = ControladorTareas.resumen(lista, 15, T0.plusMinutes(50));
        assertEquals(Boolean.TRUE, r.get("frecuencia_cumplida"), "Llegan a tiempo");
        assertEquals(Boolean.FALSE, r.get("comprobacion_correcta"), "Pero fallan");
        assertEquals(2, r.get("fallidas_seguidas"));
        assertEquals("fallida", ((Map<?, ?>) r.get("ultima_ejecucion")).get("estado"));
        assertEquals(T0.plusMinutes(15), ((Map<?, ?>) r.get("ultima_ejecucion_exitosa")).get("iniciada_en"));
        assertEquals("con_errores", r.get("estado_general"));
    }

    @Test
    void lasOmitidasNoCuentanComoUltimaEjecucion() {
        List<EjecucionTareaDao.Ejecucion> lista = new ArrayList<>();
        lista.add(ronda(300, EjecucionTareaDao.Estado.OMITIDA));
        lista.addAll(rondas(0, 15, 30));
        Map<String, Object> r = ControladorTareas.resumen(lista, 15, T0.plusMinutes(301));
        assertEquals(271L, r.get("minutos_desde_ultima"), "Una ronda omitida no comprobó nada");
        assertEquals(Boolean.FALSE, r.get("frecuencia_cumplida"));
    }

    @Test
    void sinRegistrosNoSeAfirmaNada() {
        Map<String, Object> r = ControladorTareas.resumen(List.of(), 15, T0);
        assertNull(r.get("frecuencia_cumplida"));
        assertNull(r.get("comprobacion_correcta"));
        assertNull(r.get("ultima_ejecucion"));
        assertNull(r.get("ultima_ejecucion_exitosa"));
        assertEquals("sin_datos", r.get("estado_general"));
    }

    @Test
    void elEstadoUsaElRelojDelServidorYNoMuestraElToken() {
        EjecucionTareaDao dao = mock(EjecucionTareaDao.class);
        when(dao.recientes(ServicioComprobacionPrecios.TAREA, 50)).thenReturn(rondas(0, 15, 30));
        Clock reloj = Clock.fixed(T0.plusMinutes(31).toInstant(), ZoneOffset.UTC);
        ResponseEntity<Object> r = new ControladorTareas(mock(ServicioComprobacionPrecios.class),
                propiedades("secreto-de-prueba"), dao, reloj).estado("secreto-de-prueba");
        assertEquals(200, r.getStatusCode().value());
        assertEquals(1L, ((Map<?, ?>) r.getBody()).get("minutos_desde_ultima"));
        assertFalse(String.valueOf(r.getBody()).contains("secreto-de-prueba"));
    }

    @Test
    void laRespuestaDelCronEsCorta() {
        // cron-job.org desactiva el trabajo si la respuesta es grande: la de la ronda debe ser mínima.
        ServicioComprobacionPrecios servicio = mock(ServicioComprobacionPrecios.class);
        when(servicio.ejecutarRonda(EjecucionTareaDao.Origen.CRON))
                .thenReturn(new ServicioComprobacionPrecios.Resumen(true, 200, 20, 3, 4, 4, true));
        ResponseEntity<Object> r = new ControladorTareas(servicio, propiedades("secreto-de-prueba"),
                mock(EjecucionTareaDao.class), RELOJ).seguimientos("secreto-de-prueba");
        assertTrue(String.valueOf(r.getBody()).length() < 300);
    }
}
