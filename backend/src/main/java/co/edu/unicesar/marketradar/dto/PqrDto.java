package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.EstadoPqr;
import co.edu.unicesar.marketradar.domain.Pqr;
import co.edu.unicesar.marketradar.domain.TipoPqr;

import java.time.OffsetDateTime;

/**
 * PQR para mostrar al usuario o al administrador.
 *
 * @param id identificador (número de radicado)
 * @param tipo clase de solicitud
 * @param asunto resumen
 * @param descripcion detalle
 * @param estado RADICADA, EN_PROCESO o RESPONDIDA
 * @param respuesta respuesta del administrador
 * @param creadaEn fecha de radicación
 * @param respondidaEn fecha de respuesta
 * @param nombreUsuario nombre de quien la radicó
 * @param correoUsuario correo de quien la radicó
 */
public record PqrDto(Long id, TipoPqr tipo, String asunto, String descripcion, EstadoPqr estado, String respuesta,
                     OffsetDateTime creadaEn, OffsetDateTime respondidaEn, String nombreUsuario,
                     String correoUsuario) {

    /**
     * @param pqr PQR guardada
     * @return DTO
     */
    public static PqrDto desde(Pqr pqr) {
        return new PqrDto(pqr.getId(), pqr.getTipo(), pqr.getAsunto(), pqr.getDescripcion(), pqr.getEstado(),
                pqr.getRespuesta(), pqr.getCreadaEn(), pqr.getRespondidaEn(), pqr.getNombreUsuario(),
                pqr.getCorreoUsuario());
    }
}
