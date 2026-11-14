package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.Seguimiento;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla {@code seguimiento_oferta}. Cada consulta que cambia un seguimiento recibe el usuario, así una
 * persona nunca puede ver ni cambiar el seguimiento de otra.
 */
public interface SeguimientoDao {

    /**
     * Un seguimiento con la oferta que sigue.
     *
     * @param seguimiento el seguimiento
     * @param oferta la oferta guardada
     * @param codigoMarketplace tienda de la oferta
     */
    record Detalle(Seguimiento seguimiento, Oferta oferta, String codigoMarketplace) {
    }

    /**
     * Un seguimiento que toca comprobar, con lo necesario para buscar la oferta y avisar.
     *
     * @param seguimiento el seguimiento
     * @param oferta la oferta guardada
     * @param codigoMarketplace tienda de la oferta
     * @param correo correo actual de la persona
     * @param correoVerificado si la persona verificó ese correo
     */
    record Pendiente(Seguimiento seguimiento, Oferta oferta, String codigoMarketplace, String correo,
                     boolean correoVerificado) {
    }

    /**
     * @param usuarioId persona
     * @param ofertaId oferta
     * @return el seguimiento, en cualquier estado
     */
    Optional<Seguimiento> buscar(long usuarioId, long ofertaId);

    /**
     * Crea el seguimiento o, si la persona ya había seguido esa oferta, lo vuelve a activar con los datos nuevos. Así
     * nunca hay dos filas para la misma persona y la misma oferta.
     *
     * @param seguimiento datos del seguimiento, con su referencia
     * @return el seguimiento guardado
     */
    Seguimiento activar(Seguimiento seguimiento);

    /**
     * @param usuarioId persona
     * @return sus seguimientos que no están cancelados, del más reciente al más antiguo
     */
    List<Detalle> listarDelUsuario(long usuarioId);

    /**
     * Cambia qué se avisa y el estado (activo o pausado) de un seguimiento que no está cancelado.
     *
     * @param usuarioId persona
     * @param ofertaId oferta
     * @param tipo qué avisar
     * @param precioObjetivo precio objetivo, o {@code null}
     * @param estado {@code ACTIVO} o {@code PAUSADO}
     * @return {@code true} si se cambió
     */
    boolean actualizarPreferencias(long usuarioId, long ofertaId, Seguimiento.TipoAviso tipo,
                                   java.math.BigDecimal precioObjetivo, Seguimiento.Estado estado);

    /**
     * @param usuarioId persona
     * @param ofertaId oferta
     * @return {@code true} si estaba activo o pausado y quedó cancelado
     */
    boolean cancelar(long usuarioId, long ofertaId);

    /**
     * Cancela el seguimiento del enlace "Dejar de seguir" de un correo.
     *
     * @param token clave del enlace
     * @return el seguimiento cancelado, o vacío si la clave no existe o ya estaba cancelado
     */
    Optional<Seguimiento> cancelarPorToken(String token);

    /**
     * Toma los seguimientos activos que ya toca comprobar y los aparta por unos minutos, para que otra ronda no los
     * tome a la vez.
     *
     * @param limite cuántos como máximo
     * @param minutosApartado por cuántos minutos se apartan
     * @return seguimientos apartados
     */
    List<Pendiente> tomarPendientes(int limite, int minutosApartado);

    /**
     * Guarda el resultado de una comprobación, solo si nadie cambió el seguimiento mientras tanto.
     *
     * @param seguimiento seguimiento con los datos nuevos y la versión que se leyó
     * @return {@code true} si se guardó
     */
    boolean guardarComprobacion(Seguimiento seguimiento);

    /**
     * @param usuarioId persona
     * @param ofertaId oferta
     * @param cuando cuándo comprobarlo
     */
    void posponer(long usuarioId, long ofertaId, OffsetDateTime cuando);

    /**
     * Pone la clave del enlace "Dejar de seguir" si el seguimiento todavía no tiene una.
     *
     * @param usuarioId persona
     * @param ofertaId oferta
     * @param token clave nueva
     * @return la clave del seguimiento (la que ya tenía, o la nueva)
     */
    String asegurarToken(long usuarioId, long ofertaId, String token);
}
