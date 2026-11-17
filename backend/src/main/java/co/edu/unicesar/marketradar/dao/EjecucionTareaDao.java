package co.edu.unicesar.marketradar.dao;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Registro de las rondas de las tareas periódicas (tabla {@code ejecucion_tarea}) y bloqueo para que nunca corran dos
 * a la vez, aunque haya dos copias del servidor.
 */
public interface EjecucionTareaDao {

    /** Quién disparó la ronda. */
    enum Origen { CRON, INTERNA }

    /** Cómo terminó técnicamente: {@code TERMINADA} solo dice que la ronda acabó sin excepción, no que comprobara precios. */
    enum Estado { EN_CURSO, TERMINADA, OMITIDA, FALLIDA }

    /**
     * Qué pasó con las consultas a las tiendas de una ronda terminada.
     * <ul>
     *   <li>{@code SIN_TRABAJO}: no había seguimientos pendientes. No es un fallo, pero tampoco prueba nada.</li>
     *   <li>{@code SOLO_POSPUESTOS}: había pendientes, pero el cupo o el tiempo los dejó todos para después.</li>
     *   <li>{@code CORRECTAS}: todas las consultas hechas respondieron (aunque alguna oferta ya no apareciera).</li>
     *   <li>{@code PARCIALES}: unas respondieron y otras fallaron.</li>
     *   <li>{@code FALLIDAS}: ninguna consulta respondió.</li>
     * </ul>
     */
    enum ResultadoConsultas { SIN_TRABAJO, SOLO_POSPUESTOS, CORRECTAS, PARCIALES, FALLIDAS;

        /**
         * @param exitosas consultas que respondieron
         * @param fallidas consultas que fallaron
         * @param pospuestos seguimientos que quedaron para después
         * @return el resultado que corresponde a esos números
         */
        public static ResultadoConsultas de(int exitosas, int fallidas, int pospuestos) {
            if (exitosas == 0 && fallidas == 0) {
                return pospuestos > 0 ? SOLO_POSPUESTOS : SIN_TRABAJO;
            }
            if (fallidas == 0) {
                return CORRECTAS;
            }
            return exitosas == 0 ? FALLIDAS : PARCIALES;
        }
    }

    /**
     * Lo que hizo una ronda, para registrarlo.
     *
     * @param revisados seguimientos revisados
     * @param consultas consultas hechas a las tiendas
     * @param consultasExitosas consultas que respondieron
     * @param consultasFallidas consultas que fallaron (error de la tienda o de la red)
     * @param noEncontradas seguimientos cuya consulta respondió pero la oferta ya no apareció
     * @param pospuestos seguimientos que quedaron para después
     * @param avisosNuevos avisos creados
     * @param correosAceptados correos que el proveedor aceptó (no confirma que lleguen)
     * @param correosRechazados intentos de envío que el proveedor rechazó
     * @param resultado resultado de las consultas, o {@code null} si no se sabe
     */
    record Conteo(int revisados, int consultas, int consultasExitosas, int consultasFallidas, int noEncontradas,
                  int pospuestos, int avisosNuevos, int correosAceptados, int correosRechazados,
                  ResultadoConsultas resultado) {
    }

    /**
     * Una ronda registrada.
     *
     * @param id identificador
     * @param tarea nombre de la tarea
     * @param origen quién la disparó
     * @param estado cómo terminó
     * @param iniciadaEn cuándo empezó
     * @param terminadaEn cuándo terminó, o {@code null}
     * @param revisados seguimientos revisados
     * @param consultas consultas a tiendas
     * @param pospuestos seguimientos pospuestos
     * @param avisosNuevos avisos creados
     * @param correosAceptados correos aceptados por el servicio de correo
     * @param detalle nota corta, o {@code null}
     * @param consultasExitosas consultas que respondieron
     * @param consultasFallidas consultas que fallaron
     * @param noEncontradas seguimientos cuya oferta no apareció en una consulta que sí respondió
     * @param correosRechazados intentos de envío rechazados por el proveedor
     * @param resultado resultado de las consultas; {@code null} en rondas anteriores a V15, en curso u omitidas
     */
    record Ejecucion(long id, String tarea, Origen origen, Estado estado, OffsetDateTime iniciadaEn,
                     OffsetDateTime terminadaEn, int revisados, int consultas, int pospuestos, int avisosNuevos,
                     int correosAceptados, String detalle, int consultasExitosas, int consultasFallidas,
                     int noEncontradas, int correosRechazados, ResultadoConsultas resultado) {

        /** Ronda sin el detalle de las consultas (como las anteriores a V15). */
        public Ejecucion(long id, String tarea, Origen origen, Estado estado, OffsetDateTime iniciadaEn,
                         OffsetDateTime terminadaEn, int revisados, int consultas, int pospuestos, int avisosNuevos,
                         int correosAceptados, String detalle) {
            this(id, tarea, origen, estado, iniciadaEn, terminadaEn, revisados, consultas, pospuestos, avisosNuevos,
                    correosAceptados, detalle, 0, 0, 0, 0, null);
        }
    }

    /** No registra nada ni bloquea: para las pruebas que no usan base de datos. */
    EjecucionTareaDao SIN_REGISTRO = new EjecucionTareaDao() {
        @Override
        public long iniciar(String tarea, Origen origen) {
            return 0;
        }

        @Override
        public void terminar(long id, Estado estado, Conteo conteo, String detalle) {
        }

        @Override
        public void omitida(String tarea, Origen origen, String motivo) {
        }

        @Override
        public List<Ejecucion> recientes(String tarea, int limite) {
            return List.of();
        }

        @Override
        public <T> Optional<T> conBloqueo(String tarea, Supplier<T> trabajo) {
            return Optional.ofNullable(trabajo.get());
        }
    };

    /**
     * @param tarea nombre de la tarea
     * @param origen quién la disparó
     * @return identificador de la ronda
     */
    long iniciar(String tarea, Origen origen);

    /**
     * @param id ronda
     * @param estado cómo terminó
     * @param revisados seguimientos revisados
     * @param consultas consultas
     * @param pospuestos pospuestos
     * @param avisos avisos nuevos
     * @param correos correos aceptados
     * @param detalle nota corta, o {@code null}
     */
    default void terminar(long id, Estado estado, int revisados, int consultas, int pospuestos, int avisos, int correos,
                          String detalle) {
        terminar(id, estado, new Conteo(revisados, consultas, 0, 0, 0, pospuestos, avisos, correos, 0, null), detalle);
    }

    /**
     * Cierra una ronda con el detalle de sus consultas y correos.
     *
     * @param id ronda
     * @param estado cómo terminó técnicamente
     * @param conteo lo que hizo
     * @param detalle nota corta, o {@code null}
     */
    void terminar(long id, Estado estado, Conteo conteo, String detalle);

    /**
     * Registra una ronda que no corrió porque ya había otra en curso.
     *
     * @param tarea tarea
     * @param origen quién la pidió
     * @param motivo por qué no corrió
     */
    void omitida(String tarea, Origen origen, String motivo);

    /**
     * @param tarea tarea
     * @param limite cuántas
     * @return las últimas rondas, de la más reciente a la más antigua
     */
    List<Ejecucion> recientes(String tarea, int limite);

    /**
     * Ejecuta el trabajo solo si ninguna otra ronda de la misma tarea está corriendo (bloqueo asesor de PostgreSQL).
     *
     * @param tarea tarea
     * @param trabajo lo que se hace con el bloqueo tomado
     * @param <T> tipo del resultado
     * @return el resultado, o vacío si otra ronda tenía el bloqueo
     */
    <T> Optional<T> conBloqueo(String tarea, Supplier<T> trabajo);
}
