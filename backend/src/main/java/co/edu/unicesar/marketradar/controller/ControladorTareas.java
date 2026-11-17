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
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
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
    private final Clock reloj;

    public ControladorTareas(ServicioComprobacionPrecios comprobacion, PropiedadesSeguimientos propiedades,
                             EjecucionTareaDao ejecuciones, Clock reloj) {
        this.comprobacion = comprobacion;
        this.propiedades = propiedades;
        this.ejecuciones = ejecuciones;
        this.reloj = reloj;
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
        return ResponseEntity.ok(resumen(recientes, propiedades.revisarCadaMinutos(), OffsetDateTime.now(reloj)));
    }

    /** Margen para el arranque del servidor dormido de Render. */
    static final long MARGEN_MINUTOS = 5;

    /**
     * Resume las rondas. Separa tres preguntas: si el cron llega con la frecuencia esperada (contando también el tiempo
     * que lleva sin llegar ninguna), si las rondas de verdad consultaron precios en las tiendas y qué pasó con los
     * correos. Que una ronda termine ({@code TERMINADA}) no prueba que haya comprobado precios: se mira cuántas
     * consultas respondieron. Una ronda sin trabajo pendiente no es un fallo, pero tampoco cuenta como comprobación.
     * Cuando no hay datos suficientes para responder, el valor es {@code null}: no se afirma nada.
     *
     * @param recientes rondas de la más reciente a la más antigua
     * @param esperadaMinutos cada cuánto deberían correr
     * @param ahora hora actual (reloj controlable en las pruebas)
     */
    public static Map<String, Object> resumen(List<EjecucionTareaDao.Ejecucion> recientes, int esperadaMinutos,
                                       OffsetDateTime ahora) {
        List<EjecucionTareaDao.Ejecucion> corridas = recientes.stream()
                .filter(e -> e.estado() != EjecucionTareaDao.Estado.OMITIDA).toList();
        long limite = esperadaMinutos + MARGEN_MINUTOS;
        long mayorHueco = 0;
        List<Long> huecos = new ArrayList<>();
        for (int i = 1; i < corridas.size(); i++) {
            long minutos = Duration.between(corridas.get(i).iniciadaEn(), corridas.get(i - 1).iniciadaEn()).toMinutes();
            huecos.add(minutos);
            mayorHueco = Math.max(mayorHueco, minutos);
        }
        EjecucionTareaDao.Ejecucion ultima = corridas.isEmpty() ? null : corridas.get(0);
        EjecucionTareaDao.Ejecucion ultimaTerminada = corridas.stream()
                .filter(e -> e.estado() == EjecucionTareaDao.Estado.TERMINADA).findFirst().orElse(null);
        // La última ronda que de verdad consultó tiendas (con éxito o no) y la última en que alguna consulta respondió.
        EjecucionTareaDao.Ejecucion ultimaConsulta = corridas.stream().filter(ControladorTareas::consulto)
                .findFirst().orElse(null);
        EjecucionTareaDao.Ejecucion ultimaExitosa = corridas.stream()
                .filter(e -> e.estado() == EjecucionTareaDao.Estado.TERMINADA && e.consultasExitosas() > 0)
                .findFirst().orElse(null);
        Long minutosDesdeUltima = ultima == null ? null
                : Math.max(0, Duration.between(ultima.iniciadaEn(), ahora).toMinutes());
        Long minutosDesdeExitosa = ultimaExitosa == null ? null
                : Math.max(0, Duration.between(ultimaExitosa.iniciadaEn(), ahora).toMinutes());
        int fallidasSeguidas = 0;
        for (EjecucionTareaDao.Ejecucion e : corridas) {
            if (e.estado() == EjecucionTareaDao.Estado.EN_CURSO) {
                continue;
            }
            if (e.estado() != EjecucionTareaDao.Estado.FALLIDA) {
                break;
            }
            fallidasSeguidas++;
        }

        // Frecuencia: si la última ronda ya es vieja, no se cumple aunque las de antes fueran puntuales.
        Boolean frecuencia;
        if (minutosDesdeUltima != null && minutosDesdeUltima > limite) {
            frecuencia = false;
        } else if (huecos.size() < 2) {
            frecuencia = null;
        } else {
            frecuencia = mayorHueco <= limite;
        }
        // Resultado: correr a tiempo con errores no es comprobar precios, y terminar sin consultar nada tampoco.
        boolean rondasRecientes = minutosDesdeUltima != null && minutosDesdeUltima <= limite;
        EjecucionTareaDao.ResultadoConsultas resultadoUltimaConsulta = ultimaConsulta == null ? null
                : ultimaConsulta.resultado();
        Boolean comprobacion;
        if (ultima == null) {
            comprobacion = null;
        } else if (fallidasSeguidas > 0) {
            comprobacion = false;
        } else if (resultadoUltimaConsulta == EjecucionTareaDao.ResultadoConsultas.FALLIDAS
                || resultadoUltimaConsulta == EjecucionTareaDao.ResultadoConsultas.PARCIALES) {
            comprobacion = false;
        } else if (ultimaConsulta != null
                && Duration.between(ultimaConsulta.iniciadaEn(), ahora).toMinutes() <= limite) {
            comprobacion = true;
        } else {
            // Sin una consulta reciente: si las rondas siguen llegando (solo que sin trabajo) no se afirma nada; si
            // tampoco llegan rondas, no hay comprobación.
            comprobacion = rondasRecientes ? null : false;
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("frecuencia_esperada_minutos", esperadaMinutos);
        datos.put("consultado_en", ahora);
        datos.put("rondas_registradas", corridas.size());
        datos.put("mayor_espera_minutos", huecos.isEmpty() ? null : mayorHueco);
        datos.put("minutos_desde_ultima", minutosDesdeUltima);
        datos.put("frecuencia_cumplida", frecuencia);
        datos.put("ultima_ejecucion", ultima == null ? null : fila(ultima));
        datos.put("ultima_ronda_terminada", ultimaTerminada == null ? null : fila(ultimaTerminada));
        // "Exitosa" = alguna consulta a una tienda respondió (no basta con que la ronda terminara).
        datos.put("ultima_ejecucion_exitosa", ultimaExitosa == null ? null : fila(ultimaExitosa));
        datos.put("minutos_desde_ultima_exitosa", minutosDesdeExitosa);
        datos.put("ultima_comprobacion_de_precios", ultimaConsulta == null ? null : fila(ultimaConsulta));
        datos.put("resultado_ultima_comprobacion", minusculas(resultadoUltimaConsulta));
        datos.put("fallidas_seguidas", fallidasSeguidas);
        datos.put("comprobacion_correcta", comprobacion);
        datos.put("envio_correo_ultima_ronda", envioCorreo(ultimaTerminada));
        datos.put("estado_general", estadoGeneral(frecuencia, comprobacion, ultima, fallidasSeguidas,
                resultadoUltimaConsulta));
        datos.put("rondas", recientes.stream().limit(20).map(ControladorTareas::fila).toList());
        return datos;
    }

    /** Una ronda terminada que consultó al menos una tienda (con resultado registrado, desde V15). */
    private static boolean consulto(EjecucionTareaDao.Ejecucion e) {
        return e.estado() == EjecucionTareaDao.Estado.TERMINADA && e.resultado() != null
                && e.resultado() != EjecucionTareaDao.ResultadoConsultas.SIN_TRABAJO
                && e.resultado() != EjecucionTareaDao.ResultadoConsultas.SOLO_POSPUESTOS;
    }

    private static String minusculas(Enum<?> valor) {
        return valor == null ? null : valor.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Qué pasó con los correos en la última ronda terminada. "aceptados" significa que el proveedor los aceptó; no
     * confirma que hayan llegado a la bandeja.
     */
    private static String envioCorreo(EjecucionTareaDao.Ejecucion ronda) {
        if (ronda == null) {
            return null;
        }
        if (ronda.detalle() != null && ronda.detalle().startsWith("Sin servicio de correo")) {
            return "sin_configurar";
        }
        if (ronda.correosRechazados() > 0) {
            return ronda.correosAceptados() > 0 ? "aceptados_y_rechazados" : "rechazados";
        }
        return ronda.correosAceptados() > 0 ? "aceptados" : "sin_envios";
    }

    private static String estadoGeneral(Boolean frecuencia, Boolean comprobacion, EjecucionTareaDao.Ejecucion ultima,
                                        int fallidasSeguidas, EjecucionTareaDao.ResultadoConsultas resultado) {
        if (ultima == null) {
            return "sin_datos";
        }
        if (fallidasSeguidas > 0) {
            return "con_errores";
        }
        if (Boolean.FALSE.equals(frecuencia)) {
            return "atrasado";
        }
        if (resultado == EjecucionTareaDao.ResultadoConsultas.FALLIDAS) {
            return "consultas_fallidas";
        }
        if (resultado == EjecucionTareaDao.ResultadoConsultas.PARCIALES) {
            return "consultas_parciales";
        }
        if (Boolean.TRUE.equals(frecuencia) && Boolean.TRUE.equals(comprobacion)) {
            return "al_dia";
        }
        return "sin_datos_suficientes";
    }

    private static Map<String, Object> fila(EjecucionTareaDao.Ejecucion e) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("origen", e.origen().name().toLowerCase(java.util.Locale.ROOT));
        fila.put("estado", e.estado().name().toLowerCase(java.util.Locale.ROOT));
        fila.put("iniciada_en", e.iniciadaEn());
        fila.put("terminada_en", e.terminadaEn());
        fila.put("revisados", e.revisados());
        fila.put("consultas", e.consultas());
        fila.put("consultas_exitosas", e.consultasExitosas());
        fila.put("consultas_fallidas", e.consultasFallidas());
        fila.put("ofertas_no_encontradas", e.noEncontradas());
        fila.put("pospuestos", e.pospuestos());
        fila.put("resultado_consultas", minusculas(e.resultado()));
        fila.put("avisos_nuevos", e.avisosNuevos());
        fila.put("correos_aceptados", e.correosAceptados());
        fila.put("correos_rechazados", e.correosRechazados());
        fila.put("detalle", e.detalle());
        return fila;
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
