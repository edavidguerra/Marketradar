package co.edu.unicesar.marketradar.domain;

/**
 * Rol de una cuenta. Cada rol ve una interfaz distinta.
 */
public enum Rol {
    /** Persona que busca productos, consulta su historial y radica PQR. */
    USUARIO,
    /** Persona del equipo que gestiona y responde las PQR. */
    ADMINISTRADOR
}
