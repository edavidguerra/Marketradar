package co.edu.unicesar.marketradar.dto;

/**
 * Cifras para la pantalla de inicio del administrador.
 *
 * @param pqrRadicadas PQR nuevas sin leer
 * @param pqrEnProceso PQR leídas sin respuesta
 * @param pqrRespondidas PQR ya respondidas
 * @param usuariosRegistrados cuentas con rol USUARIO
 */
public record ResumenAdministradorDto(long pqrRadicadas, long pqrEnProceso, long pqrRespondidas,
                                      long usuariosRegistrados) {
}
