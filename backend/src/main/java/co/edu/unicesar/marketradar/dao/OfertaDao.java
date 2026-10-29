package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.Oferta;

import java.util.Optional;

/**
 * Acceso a la tabla {@code oferta}.
 */
public interface OfertaDao extends Dao<Oferta> {

    /**
     * Busca una oferta por su marketplace y su identificador externo (clave única natural).
     *
     * @param marketplaceId marketplace de la oferta
     * @param idExterno identificador de la oferta en el marketplace
     * @return la oferta, o vacío si no existe
     */
    Optional<Oferta> buscarPorMarketplaceYIdExterno(short marketplaceId, String idExterno);
}
