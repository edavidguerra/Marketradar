package co.edu.unicesar.marketradar.domain;

/**
 * Estado de atención de una PQR.
 */
public enum EstadoPqr {
    /** Recién creada, nadie la ha leído. */
    RADICADA,
    /** Un administrador la está revisando. */
    EN_PROCESO,
    /** Ya tiene respuesta para el usuario. */
    RESPONDIDA
}
