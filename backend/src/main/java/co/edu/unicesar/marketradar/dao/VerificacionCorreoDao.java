package co.edu.unicesar.marketradar.dao;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Verificación del correo de las personas: tabla {@code token_correo} y columna {@code usuario.correo_verificado_en}.
 * Solo se guarda el SHA-256 de cada enlace.
 */
public interface VerificacionCorreoDao {

    /**
     * @param usuarioId persona
     * @return cuándo verificó su correo actual, o vacío si no lo ha verificado
     */
    Optional<OffsetDateTime> verificadoEn(long usuarioId);

    /**
     * @param tokenHash SHA-256 del enlace, en hexadecimal
     * @param usuarioId persona
     * @param correo correo al que se manda el enlace
     * @param venceEn hasta cuándo sirve
     */
    void guardarToken(String tokenHash, long usuarioId, String correo, OffsetDateTime venceEn);

    /**
     * @param usuarioId persona
     * @param desde desde cuándo contar
     * @return cuántos enlaces se le han mandado desde ese momento
     */
    int contarEnviados(long usuarioId, OffsetDateTime desde);

    /**
     * Usa un enlace: si existe, no está vencido, no se ha usado y el correo de la cuenta sigue siendo el mismo, marca el
     * correo como verificado.
     *
     * @param tokenHash SHA-256 del enlace
     * @return la persona que verificó su correo, o vacío si el enlace no sirve
     */
    Optional<Long> usarToken(String tokenHash);
}
