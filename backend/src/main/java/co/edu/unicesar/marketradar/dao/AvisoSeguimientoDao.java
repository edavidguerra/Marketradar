package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.AvisoSeguimiento;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code aviso_seguimiento}: los correos de los seguimientos y su estado de envío.
 */
public interface AvisoSeguimientoDao {

    /**
     * Un aviso que toca mandar, con lo que dice el correo.
     *
     * @param aviso el aviso
     * @param estadoSeguimiento estado actual del seguimiento
     * @param correoActual correo actual de la persona
     * @param correoVerificado si lo verificó
     * @param nombre nombres de la persona
     * @param tituloOferta producto
     * @param urlOferta enlace de la oferta
     * @param vendedor vendedor de la oferta
     * @param codigoMarketplace tienda
     * @param tipoAviso qué avisa el seguimiento
     * @param precioObjetivo precio objetivo del seguimiento, o {@code null}
     */
    record Pendiente(AvisoSeguimiento aviso, String estadoSeguimiento, String correoActual, boolean correoVerificado,
                     String nombre, String tituloOferta, String urlOferta, String vendedor, String codigoMarketplace,
                     String tipoAviso, java.math.BigDecimal precioObjetivo) {
    }

    /**
     * Guarda un aviso nuevo. Si ya existe otro con la misma clave de evento, no hace nada: así el mismo evento nunca se
     * avisa dos veces.
     *
     * @param aviso aviso
     * @return identificador del aviso nuevo, o vacío si ya existía
     */
    Optional<Long> insertarSiEsNuevo(AvisoSeguimiento aviso);

    /**
     * Aparta los avisos pendientes cuyo intento ya toca.
     *
     * @param limite cuántos como máximo
     * @param minutosApartado por cuántos minutos se apartan
     * @return avisos apartados
     */
    List<Pendiente> tomarPendientes(int limite, int minutosApartado);

    /**
     * Aparta un aviso pendiente concreto, para mandarlo enseguida.
     *
     * @param id aviso
     * @param minutosApartado por cuántos minutos se aparta
     * @return el aviso, o vacío si ya no está pendiente
     */
    Optional<Pendiente> tomar(long id, int minutosApartado);

    /** @param id aviso que el servicio de correo aceptó */
    void marcarEnviado(long id);

    /**
     * Marca el aviso como aceptado por el servicio de correo (estado {@code ENVIADO}; no confirma la entrega) y guarda
     * quién lo aceptó.
     *
     * @param id aviso
     * @param resultado proveedor e identificador del mensaje
     */
    default void marcarAceptado(long id, co.edu.unicesar.marketradar.correo.ResultadoEnvio resultado) {
        marcarEnviado(id);
    }

    /**
     * Registra un intento que no se pudo mandar.
     *
     * @param id aviso
     * @param error motivo, sin datos privados
     * @param contarIntento si cuenta como intento (no cuenta cuando el correo no está configurado)
     * @param proximoIntento cuándo intentar otra vez, o {@code null} si ya no se reintenta (queda FALLIDO)
     */
    void registrarFallo(long id, String error, boolean contarIntento, OffsetDateTime proximoIntento);

    /**
     * @param id aviso que ya no se debe mandar
     * @param motivo por qué
     */
    void descartar(long id, String motivo);

    /**
     * Descarta los avisos pendientes de un seguimiento (por ejemplo, al cancelarlo o pausarlo).
     *
     * @param usuarioId persona
     * @param ofertaId oferta
     * @param motivo por qué
     * @return cuántos se descartaron
     */
    int descartarPendientes(long usuarioId, long ofertaId, String motivo);

    /**
     * @param usuarioId persona
     * @param ofertaId oferta
     * @param limite cuántos
     * @return los avisos más recientes de un seguimiento
     */
    List<AvisoSeguimiento> recientes(long usuarioId, long ofertaId, int limite);
}
