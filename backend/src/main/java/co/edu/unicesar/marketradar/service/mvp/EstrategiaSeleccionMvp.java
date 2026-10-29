package co.edu.unicesar.marketradar.service.mvp;

import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ProductoMvp;

import java.util.List;
import java.util.Optional;

/**
 * Criterio para elegir la mejor opción de compra entre las ofertas de una búsqueda (patrón Strategy).
 * Se puede cambiar el criterio sin modificar el servicio de búsqueda (OCP, DIP).
 */
public interface EstrategiaSeleccionMvp {

    /**
     * @param ofertas ofertas encontradas, todas en la misma moneda
     * @return la mejor opción, o vacío si no hay ofertas
     */
    Optional<ProductoMvp> elegir(List<OfertaNormalizada> ofertas);
}
