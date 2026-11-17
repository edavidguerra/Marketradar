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

    /** Ronda en que la única consulta respondió (o, si no terminó, sin resultado). */
    private static EjecucionTareaDao.Ejecucion ronda(int minutoInicio, EjecucionTareaDao.Estado estado) {
        boolean terminada = estado == EjecucionTareaDao.Estado.TERMINADA;
        return ronda(minutoInicio, estado, terminada ? 1 : 0, 0, terminada ? EjecucionTareaDao.ResultadoConsultas.CORRECTAS : null);
    }

    private static EjecucionTareaDao.Ejecucion ronda(int minutoInicio, EjecucionTareaDao.Estado estado, int exitosas,
                                                     int fallidas, EjecucionTareaDao.ResultadoConsultas resultado) {
        return new EjecucionTareaDao.Ejecucion(minutoInicio, "seguimientos", EjecucionTareaDao.Origen.CRON, estado,
                T0.plusMinutes(minutoInicio), T0.plusMinutes(minutoInicio).plusSeconds(5), exitosas + fallidas,
                exitosas + fallidas, 0, 0, 0, null, exitosas, fallidas, 0, 0, resultado);
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

    // --- Ronda terminada no es comprobación exitosa ---

    @Test
    void rondasPuntualesConTodasLasConsultasFallidasNoSonComprobacionCorrecta() {
        List<EjecucionTareaDao.Ejecucion> lista = new ArrayList<>();
        for (int minuto : new int[] {45, 30, 15, 0}) {
            lista.add(ronda(minuto, EjecucionTareaDao.Estado.TERMINADA, 0, 2, EjecucionTareaDao.ResultadoConsultas.FALLIDAS));
        }
        Map<String, Object> r = ControladorTareas.resumen(lista, 15, T0.plusMinutes(50));
        assertEquals(Boolean.TRUE, r.get("frecuencia_cumplida"), "El cron llega a tiempo");
        assertEquals(Boolean.FALSE, r.get("comprobacion_correcta"), "Pero ninguna consulta respondió");
        assertNull(r.get("ultima_ejecucion_exitosa"));
        assertEquals(0, r.get("fallidas_seguidas"), "Técnicamente ninguna ronda falló");
        assertEquals("consultas_fallidas", r.get("estado_general"));
    }

    @Test
    void rondasSinTrabajoNoSonFalloNiEvidenciaDeConsultas() {
        List<EjecucionTareaDao.Ejecucion> lista = new ArrayList<>();
        for (int minuto : new int[] {45, 30, 15, 0}) {
            lista.add(ronda(minuto, EjecucionTareaDao.Estado.TERMINADA, 0, 0, EjecucionTareaDao.ResultadoConsultas.SIN_TRABAJO));
        }
        Map<String, Object> r = ControladorTareas.resumen(lista, 15, T0.plusMinutes(50));
        assertEquals(Boolean.TRUE, r.get("frecuencia_cumplida"));
        assertNull(r.get("comprobacion_correcta"));
        assertNull(r.get("ultima_comprobacion_de_precios"));
        assertEquals("sin_datos_suficientes", r.get("estado_general"));
    }

    @Test
    void unaComprobacionViejaSeguidaDeRondasSinTrabajoNoSeDaPorVigente() {
        List<EjecucionTareaDao.Ejecucion> lista = new ArrayList<>();
        lista.add(ronda(300, EjecucionTareaDao.Estado.TERMINADA, 0, 0, EjecucionTareaDao.ResultadoConsultas.SIN_TRABAJO));
        lista.add(ronda(285, EjecucionTareaDao.Estado.TERMINADA, 0, 0, EjecucionTareaDao.ResultadoConsultas.SIN_TRABAJO));
        lista.add(ronda(270, EjecucionTareaDao.Estado.TERMINADA, 0, 0, EjecucionTareaDao.ResultadoConsultas.SIN_TRABAJO));
        lista.add(ronda(0, EjecucionTareaDao.Estado.TERMINADA));
        Map<String, Object> r = ControladorTareas.resumen(lista, 15, T0.plusMinutes(305));
        assertNull(r.get("comprobacion_correcta"), "Hay rondas recientes, pero sin precios que consultar");
        assertEquals(T0, ((Map<?, ?>) r.get("ultima_comprobacion_de_precios")).get("iniciada_en"));
    }

    @Test
    void ejecucionesAntiguasYNingunaRecienteNoCumplen() {
        List<EjecucionTareaDao.Ejecucion> lista = new ArrayList<>();
        lista.add(ronda(30, EjecucionTareaDao.Estado.TERMINADA, 1, 1, EjecucionTareaDao.ResultadoConsultas.PARCIALES));
        lista.add(ronda(15, EjecucionTareaDao.Estado.TERMINADA));
        lista.add(ronda(0, EjecucionTareaDao.Estado.TERMINADA));
        Map<String, Object> r = ControladorTareas.resumen(lista, 15, T0.plusMinutes(30 + 600));
        assertEquals(Boolean.FALSE, r.get("frecuencia_cumplida"));
        assertEquals(Boolean.FALSE, r.get("comprobacion_correcta"));
        assertEquals(600L, r.get("minutos_desde_ultima"));
        assertEquals("atrasado", r.get("estado_general"));
        assertEquals(T0.plusMinutes(30), ((Map<?, ?>) r.get("ultima_ejecucion_exitosa")).get("iniciada_en"),
                "La parcial tuvo una consulta que respondió");
    }

    @Test
    void lasRondasAnterioresAlRegistroDeConsultasNoCuentanComoComprobacion() {
        // Filas anteriores a V15: TERMINADA sin resultado de consultas. No se sabe si consultaron con éxito.
        List<EjecucionTareaDao.Ejecucion> lista = new ArrayList<>();
        for (int minuto : new int[] {45, 30, 15, 0}) {
            lista.add(new EjecucionTareaDao.Ejecucion(minuto, "seguimientos", EjecucionTareaDao.Origen.CRON,
                    EjecucionTareaDao.Estado.TERMINADA, T0.plusMinutes(minuto), T0.plusMinutes(minuto).plusSeconds(5),
                    1, 1, 0, 0, 0, null));
        }
        Map<String, Object> r = ControladorTareas.resumen(lista, 15, T0.plusMinutes(50));
        assertNull(r.get("comprobacion_correcta"));
        assertNull(r.get("ultima_ejecucion_exitosa"));
        assertNull(((Map<?, ?>) r.get("ultima_ejecucion")).get("resultado_consultas"));
    }
}
