package co.edu.unicesar.marketradar.adapter;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;

import java.util.List;

/**
 * Contrato que cumple cada marketplace integrado. Agregar un marketplace nuevo consiste en crear
 * otra implementación, sin modificar el servicio de búsqueda (OCP).
 */
public interface AdaptadorMarketplace {

    /**
     * Código único del marketplace, igual al de la tabla {@code marketplace}.
     *
     * @return código, por ejemplo {@code aliexpress}
     */
    String codigo();

    /**
     * Indica si el adaptador tiene credenciales y está activado por configuración.
     *
     * @return {@code true} si se puede consultar
     */
    boolean estaHabilitado();

    /**
     * Busca ofertas en el marketplace y las devuelve en el modelo común, con precios en COP.
     *
     * @param consulta parámetros validados de la búsqueda
     * @return ofertas encontradas
     * @throws co.edu.unicesar.marketradar.exception.MarketplaceException si la API falla
     */
    List<OfertaNormalizada> buscar(ConsultaBusqueda consulta);
}
