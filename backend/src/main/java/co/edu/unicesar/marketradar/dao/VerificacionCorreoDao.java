package co.edu.unicesar.marketradar.dao;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Verificación del correo de las personas: tabla {@code token_correo} y columna {@code usuario.correo_verificado_en}.
 * Solo se guarda el SHA-256 de cada enlace. Cada enlace guarda también en qué quedó su envío.
 */
public interface VerificacionCorreoDao {

    /** En qué quedó el envío de un enlace. */
    enum EstadoEnvio { SIN_REGISTRO, PENDIENTE, ACEPTADO, FALLIDO }

    /**
     * El último enlace que se pidió.
     *
     * @param estado en qué quedó el envío
     * @param creadoEn cuándo se pidió
     * @param aceptadoEn cuándo lo aceptó el servicio de correo, o {@code null}
     * @param venceEn hasta cuándo sirve
     * @param usado si ya se usó
     * @param ultimoError por qué falló, o {@code null}
     */
    record Envio(EstadoEnvio estado, OffsetDateTime creadoEn, OffsetDateTime aceptadoEn, OffsetDateTime venceEn,
                 boolean usado, String ultimoError) {
    }

    /**
     * @param usuarioId persona
     * @return cuándo verificó su correo actual, o vacío si no lo ha verificado
     */
    Optional<OffsetDateTime> verificadoEn(long usuarioId);

    /**
     * Guarda un enlace nuevo como PENDIENTE de envío. Solo puede haber un envío en curso por persona.
     *
     * @param tokenHash SHA-256 del enlace, en hexadecimal
     * @param usuarioId persona
     * @param correo correo al que se manda el enlace
     * @param venceEn hasta cuándo sirve
     * @return {@code false} si ya había otro envío en curso para esa persona
     */
    boolean guardarToken(String tokenHash, long usuarioId, String correo, OffsetDateTime venceEn);

    /**
     * Da por fallidos los envíos que quedaron PENDIENTES desde antes de {@code antesDe} (por ejemplo, si el servidor se
     * reinició mientras mandaba), para que no bloqueen un envío nuevo.
     *
     * @param usuarioId persona
     * @param antesDe límite
     */
    void liberarEnviosColgados(long usuarioId, OffsetDateTime antesDe);

    /**
     * @param tokenHash enlace
     * @param intentos intentos hechos
     * @param proveedor quién lo aceptó
     * @param idMensaje identificador del proveedor, o {@code null}
     */
    void marcarAceptado(String tokenHash, int intentos, String proveedor, String idMensaje);

    /**
     * @param tokenHash enlace
     * @param intentos intentos hechos
     * @param error motivo, sin datos privados
     */
    void marcarFallido(String tokenHash, int intentos, String error);

    /**
     * @param usuarioId persona
     * @param desde desde cuándo contar
     * @return enlaces pedidos desde ese momento que no fallaron (pendientes, aceptados o anteriores a V14)
     */
    int contarEnviados(long usuarioId, OffsetDateTime desde);

    /**
     * @param usuarioId persona
     * @param desde desde cuándo contar
     * @return todas las solicitudes desde ese momento, también las fallidas
     */
    int contarSolicitudes(long usuarioId, OffsetDateTime desde);

    /**
     * @param usuarioId persona
     * @return el último enlace pedido, o vacío
     */
    Optional<Envio> ultimoEnvio(long usuarioId);

    /**
     * Usa un enlace: si existe, no está vencido, no se ha usado y el correo de la cuenta sigue siendo el mismo, marca el
     * correo como verificado.
     *
     * @param tokenHash SHA-256 del enlace
     * @return la persona que verificó su correo, o vacío si el enlace no sirve
     */
    Optional<Long> usarToken(String tokenHash);
}
