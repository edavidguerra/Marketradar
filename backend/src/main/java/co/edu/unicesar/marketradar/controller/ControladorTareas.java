package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.config.PropiedadesSeguimientos;
import co.edu.unicesar.marketradar.dao.EjecucionTareaDao;
import co.edu.unicesar.marketradar.service.seguimiento.ServicioComprobacionPrecios;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Permite que un servicio externo de tareas programadas (por ejemplo cron-job.org o GitHub Actions) despierte el
 * servidor y ejecute una ronda de comprobación de precios. Exige la clave {@code TAREAS_TOKEN} en la cabecera
 * {@code X-Tarea-Token}; sin esa variable configurada, la ruta no existe.
 *
 * <p>En el plan gratis de Render el servidor se duerme tras 15 minutos sin visitas y, mientras duerme, la tarea interna
 * del servidor ({@code @Scheduled}) no corre. Por eso la frecuencia real depende de este disparador externo. Con
 * {@code GET} se ven las últimas rondas registradas, para comprobar con datos cada cuánto corren.</p>
 */
@RestController
@RequestMapping("/api/v1/tareas")
public class ControladorTareas {

    private final ServicioComprobacionPrecios comprobacion;
    private final PropiedadesSeguimientos propiedades;
    private final EjecucionTareaDao ejecuciones;

    public ControladorTareas(ServicioComprobacionPrecios comprobacion, PropiedadesSeguimientos propiedades,
                             EjecucionTareaDao ejecuciones) {
        this.comprobacion = comprobacion;
        this.propiedades = propiedades;
        this.ejecuciones = ejecuciones;
    }

    @PostMapping("/seguimientos")
    public ResponseEntity<Object> seguimientos(@RequestHeader(name = "X-Tarea-Token", required = false) String token) {
        Optional<ResponseEntity<Object>> rechazo = rechazo(token);
        if (rechazo.isPresent()) {
            return rechazo.get();
        }
        if (!propiedades.habilitado()) {
            return ResponseEntity.ok(Map.of("ejecutada", false, "motivo", "La comprobación de seguimientos está apagada"));
        }
        return ResponseEntity.ok(comprobacion.ejecutarRonda(EjecucionTareaDao.Origen.CRON));
    }

    /**
     * Últimas rondas registradas, con el tiempo entre una y otra. Sirve para comprobar si el cron externo está
     * llegando con la frecuencia esperada. No muestra datos de personas.
     *
     * @param token clave de tareas
     * @return rondas recientes y resumen
     */
    @GetMapping("/seguimientos")
    public ResponseEntity<Object> estado(@RequestHeader(name = "X-Tarea-Token", required = false) String token) {
        Optional<ResponseEntity<Object>> rechazo = rechazo(token);
        if (rechazo.isPresent()) {
            return rechazo.get();
        }
        List<EjecucionTareaDao.Ejecucion> recientes = ejecuciones.recientes(ServicioComprobacionPrecios.TAREA, 50);
        return ResponseEntity.ok(resumen(recientes, propiedades.revisarCadaMinutos()));
    }

    /** Resume las rondas: cuántas hubo, la espera más larga entre dos seguidas y si se cumple la frecuencia. */
    static Map<String, Object> resumen(List<EjecucionTareaDao.Ejecucion> recientes, int esperadaMinutos) {
        List<EjecucionTareaDao.Ejecucion> corridas = recientes.stream()
                .filter(e -> e.estado() != EjecucionTareaDao.Estado.OMITIDA).toList();
        long mayorHueco = 0;
        List<Long> huecos = new ArrayList<>();
        for (int i = 1; i < corridas.size(); i++) {
            long minutos = Duration.between(corridas.get(i).iniciadaEn(), corridas.get(i - 1).iniciadaEn()).toMinutes();
            huecos.add(minutos);
            mayorHueco = Math.max(mayorHueco, minutos);
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("frecuencia_esperada_minutos", esperadaMinutos);
        datos.put("rondas_registradas", corridas.size());
        datos.put("mayor_espera_minutos", huecos.isEmpty() ? null : mayorHueco);
        // Se da margen de 5 minutos por el arranque del servidor dormido. Con menos de 3 rondas no se puede afirmar.
        datos.put("frecuencia_cumplida", huecos.size() < 2 ? null : mayorHueco <= esperadaMinutos + 5L);
        datos.put("rondas", recientes.stream().limit(20).map(e -> {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("origen", e.origen().name().toLowerCase(java.util.Locale.ROOT));
            fila.put("estado", e.estado().name().toLowerCase(java.util.Locale.ROOT));
            fila.put("iniciada_en", e.iniciadaEn());
            fila.put("terminada_en", e.terminadaEn());
            fila.put("revisados", e.revisados());
            fila.put("consultas", e.consultas());
            fila.put("avisos_nuevos", e.avisosNuevos());
            fila.put("correos_aceptados", e.correosAceptados());
            fila.put("detalle", e.detalle());
            return fila;
        }).toList());
        return datos;
    }

    private Optional<ResponseEntity<Object>> rechazo(String token) {
        String esperado = propiedades.tokenTareas();
        if (esperado == null || esperado.isBlank()) {
            return Optional.of(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        }
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                esperado.getBytes(StandardCharsets.UTF_8))) {
            return Optional.of(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return Optional.empty();
    }
}
