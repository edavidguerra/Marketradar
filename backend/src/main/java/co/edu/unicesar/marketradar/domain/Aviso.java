package co.edu.unicesar.marketradar.domain;

/**
 * Aviso del inicio que invita a la persona a comprar o a buscar.
 *
 * @param tipo {@code BAJA_PRECIO}, {@code SEGUIMIENTO} o {@code INVITACION}
 * @param titulo frase corta y llamativa
 * @param mensaje explicación en una o dos frases
 * @param textoAccion texto del botón
 * @param terminoBusqueda término para buscar al pulsar el botón, si aplica
 * @param enlace enlace directo al producto en la tienda, si aplica
 */
public record Aviso(String tipo, String titulo, String mensaje, String textoAccion, String terminoBusqueda,
                    String enlace) {
}
