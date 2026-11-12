package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.OfertaDestacada;

import java.util.List;
import java.util.Map;
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
     * Las ofertas guardadas más baratas cuyo título coincide con el texto, para elegir entre ellas la que de verdad es
     * el producto (sin accesorios ni otras versiones).
     *
     * @param texto palabras buscadas
     * @param limite cuántas como máximo
     * @return ofertas de la más barata a la más cara
     */
    List<OfertaDestacada> listarMasBaratasPorTitulo(String texto, int limite);

    /**
     * Busca la oferta cuyo precio más bajó respecto al precio más alto registrado en el periodo para la misma
     * oferta, con el mismo vendedor, la misma condición y la misma moneda.
     *
     * @param dias días hacia atrás que se revisan
     * @return la oferta con su precio anterior, o vacío si ningún precio bajó
     */
    Optional<OfertaDestacada> buscarMayorBajaDePrecio(int dias);

    /**
     * Inserta o actualiza muchas ofertas en una sola consulta y registra en el historial el precio de las nuevas
     * y de las que cambiaron de precio. Reemplaza las dos o tres consultas por oferta que se hacían antes.
     *
     * @param ofertas ofertas con marketplace e identificador externo; si se repite una, gana la primera
     * @return identificador de cada oferta, por clave {@code marketplaceId|idExterno}
     */
    Map<String, Long> guardarLote(List<Oferta> ofertas);

    /**
     * @param marketplaceId marketplace de la oferta
     * @param idExterno identificador en la tienda
     * @return clave con la que {@link #guardarLote(List)} devuelve los identificadores
     */
    static String clave(short marketplaceId, String idExterno) {
        return marketplaceId + "|" + idExterno;
    }
}
