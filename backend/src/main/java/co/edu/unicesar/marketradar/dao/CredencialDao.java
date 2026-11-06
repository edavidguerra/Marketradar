package co.edu.unicesar.marketradar.dao;

import java.util.Optional;

/**
 * Acceso a la tabla {@code credencial_marketplace}: credenciales que el sistema renueva solo,
 * como el refresh token de Mercado Libre, para que no se pierdan al reiniciar el servidor.
 */
public interface CredencialDao {

    /**
     * Credencial guardada.
     *
     * @param valor valor vigente
     * @param valorInicialHash SHA-256 (hexadecimal) del valor configurado del que se partió
     */
    record CredencialGuardada(String valor, String valorInicialHash) {
    }

    /**
     * @param codigo nombre de la credencial, por ejemplo {@code mercadolibre.refresh_token}
     * @return la credencial guardada, o vacío si no hay
     */
    Optional<CredencialGuardada> buscar(String codigo);

    /**
     * Guarda o reemplaza la credencial.
     *
     * @param codigo nombre de la credencial
     * @param credencial valor y hash del valor inicial
     */
    void guardar(String codigo, CredencialGuardada credencial);
}
