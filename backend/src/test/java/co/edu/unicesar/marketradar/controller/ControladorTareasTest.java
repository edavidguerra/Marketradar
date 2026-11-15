package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.config.PropiedadesSeguimientos;
import co.edu.unicesar.marketradar.dao.EjecucionTareaDao;
import co.edu.unicesar.marketradar.service.seguimiento.ServicioComprobacionPrecios;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
        Map<String, Object> r = ControladorTareas.resumen(rondas(0, 15), 15);
        assertEquals(2, r.get("rondas_registradas"));
        assertEquals(15L, r.get("mayor_espera_minutos"));
        assertNull(r.get("frecuencia_cumplida"), "Con dos rondas no hay datos suficientes");
    }

    @Test
    void cumpleLaFrecuenciaConMargenDeArranque() {
        Map<String, Object> r = ControladorTareas.resumen(rondas(0, 15, 34, 49), 15);
        assertEquals(Boolean.TRUE, r.get("frecuencia_cumplida"));
        assertEquals(19L, r.get("mayor_espera_minutos"));
    }

    @Test
    void unHuecoLargoDiceQueNoSeCumple() {
        Map<String, Object> r = ControladorTareas.resumen(rondas(0, 15, 75), 15);
        assertEquals(Boolean.FALSE, r.get("frecuencia_cumplida"));
        assertEquals(60L, r.get("mayor_espera_minutos"));
    }

    @Test
    void lasRondasOmitidasNoCuentanComoCorridas() {
        List<EjecucionTareaDao.Ejecucion> lista = new ArrayList<>(rondas(0, 15));
        lista.add(0, ronda(16, EjecucionTareaDao.Estado.OMITIDA));
        Map<String, Object> r = ControladorTareas.resumen(lista, 15);
        assertEquals(2, r.get("rondas_registradas"));
        assertEquals(3, ((List<?>) r.get("rondas")).size(), "Se muestran todas, también la omitida");
    }

    @Test
    void sinTokenConfiguradoLaRutaNoExisteYConTokenMaloSeRechaza() {
        ServicioComprobacionPrecios servicio = mock(ServicioComprobacionPrecios.class);
        EjecucionTareaDao dao = mock(EjecucionTareaDao.class);
        assertEquals(404, new ControladorTareas(servicio, propiedades(""), dao).seguimientos("x")
                .getStatusCode().value());
        ControladorTareas c = new ControladorTareas(servicio, propiedades("secreto-de-prueba"), dao);
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
                mock(EjecucionTareaDao.class)).seguimientos("secreto-de-prueba");
        assertEquals(200, r.getStatusCode().value());
        assertEquals(resumen, r.getBody());
        assertTrue(((ServicioComprobacionPrecios.Resumen) r.getBody()).ejecutada());
        assertFalse(r.getBody() instanceof Map);
    }
}
