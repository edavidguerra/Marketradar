package co.edu.unicesar.marketradar.adapter.tasas;

import co.edu.unicesar.marketradar.domain.TasaCambio;

import java.util.List;

/**
 * Servicio externo que publica tasas de cambio a pesos colombianos. Cuando dos fuentes informan
 * la misma moneda, gana la de menor {@code @Order}.
 */
public interface FuenteTasaCambio {

    /**
     * @return nombre de la fuente, para los registros
     */
    String nombre();

    /**
     * Consulta las tasas vigentes.
     *
     * @return pesos por unidad de cada moneda
     * @throws RuntimeException si la fuente no responde o la respuesta no es válida
     */
    List<TasaCambio> consultar();
}
