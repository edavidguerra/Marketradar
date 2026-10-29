package co.edu.unicesar.marketradar.asistente;

/**
 * Autor de un mensaje en la conversación con el asistente.
 */
public enum RolMensaje {
    /** Instrucciones internas del asistente; el usuario no las escribe. */
    SISTEMA,
    /** Mensaje escrito por la persona. */
    USUARIO,
    /** Respuesta del asistente. */
    ASISTENTE
}
