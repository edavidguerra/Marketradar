package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.Aviso;

/**
 * Aviso del inicio.
 *
 * @param tipo BAJA_PRECIO, SEGUIMIENTO o INVITACION
 * @param titulo frase corta
 * @param mensaje explicación
 * @param textoAccion texto del botón
 * @param terminoBusqueda término que se busca al pulsar el botón, si aplica
 * @param enlace enlace directo al producto, si aplica
 */
public record AvisoDto(String tipo, String titulo, String mensaje, String textoAccion, String terminoBusqueda,
                       String enlace) {

    /**
     * @param aviso aviso del servicio
     * @return DTO
     */
    public static AvisoDto desde(Aviso aviso) {
        return new AvisoDto(aviso.tipo(), aviso.titulo(), aviso.mensaje(), aviso.textoAccion(),
                aviso.terminoBusqueda(), aviso.enlace());
    }
}
