package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.OfertaDestacada;

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

    /**
     * Busca la oferta guardada más barata cuyo título coincide con el texto (búsqueda de texto en español).
     *
     * @param texto palabras buscadas
     * @return la oferta y su tienda, o vacío si no hay coincidencias
     */
    Optional<OfertaDestacada> buscarMasBarataPorTitulo(String texto);

    /**
     * Busca la oferta cuyo precio más bajó respecto al precio más alto registrado en el periodo.
     *
     * @param dias días hacia atrás que se revisan
     * @return la oferta con su precio anterior, o vacío si ningún precio bajó
     */
    Optional<OfertaDestacada> buscarMayorBajaDePrecio(int dias);
}
