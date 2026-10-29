package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.Marketplace;

import java.util.Optional;

/**
 * Acceso a la tabla {@code marketplace}.
 */
public interface MarketplaceDao extends Dao<Marketplace> {

    /**
     * Busca un marketplace por su código único.
     *
     * @param codigo código, por ejemplo {@code aliexpress}
     * @return el marketplace, o vacío si no existe
     */
    Optional<Marketplace> buscarPorCodigo(String codigo);
}
