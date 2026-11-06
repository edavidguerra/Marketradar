package co.edu.unicesar.marketradar.adapter.mercadolibre;

import java.util.Optional;

/**
 * Lugar donde se guarda el refresh token de Mercado Libre entre reinicios del servidor.
 * Mercado Libre entrega uno nuevo en cada renovación y el anterior deja de servir.
 */
interface AlmacenToken {

    /**
     * @param tokenInicial refresh token de la configuración
     * @return el refresh token guardado que sigue vigente para esa configuración, o vacío
     */
    Optional<String> leer(String tokenInicial);

    /**
     * @param token refresh token nuevo
     * @param tokenInicial refresh token de la configuración del que se partió
     */
    void guardar(String token, String tokenInicial);
}
