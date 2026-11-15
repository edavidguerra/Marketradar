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

    /** Cómo terminó. */
    enum Estado { EN_CURSO, TERMINADA, OMITIDA, FALLIDA }

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
     */
    record Ejecucion(long id, String tarea, Origen origen, Estado estado, OffsetDateTime iniciadaEn,
                     OffsetDateTime terminadaEn, int revisados, int consultas, int pospuestos, int avisosNuevos,
                     int correosAceptados, String detalle) {
    }

    /** No registra nada ni bloquea: para las pruebas que no usan base de datos. */
    EjecucionTareaDao SIN_REGISTRO = new EjecucionTareaDao() {
        @Override
        public long iniciar(String tarea, Origen origen) {
            return 0;
        }

        @Override
        public void terminar(long id, Estado estado, int revisados, int consultas, int pospuestos, int avisos,
                             int correos, String detalle) {
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
    void terminar(long id, Estado estado, int revisados, int consultas, int pospuestos, int avisos, int correos,
                  String detalle);

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
