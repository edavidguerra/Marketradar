package co.edu.unicesar.marketradar.adapter.mercadolibre;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplaceBase;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Integración con la API oficial de Mercado Libre Colombia (sitio MCO) mediante OAuth.
 *
 * <p><b>Por confirmar:</b> las búsquedas anónimas pueden devolver 403. Primero se hace una prueba
 * mínima de acceso; si falla, el adaptador queda desactivado con un aviso claro.</p>
 */
@Component
public class AdaptadorMercadoLibre extends AdaptadorMarketplaceBase {

    public static final String CODIGO = "mercadolibre";

    private final PropiedadesMarketradar.MercadoLibre configuracion;

    public AdaptadorMercadoLibre(PropiedadesMarketradar propiedades, ConversorMoneda conversor) {
        super(CODIGO, conversor, 5);
        this.configuracion = propiedades.marketplaces().mercadolibre();
    }

    @Override
    public boolean estaHabilitado() {
        return configuracion.habilitado()
                && tieneValor(configuracion.clientId())
                && tieneValor(configuracion.clientSecret());
    }

    @Override
    protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
        throw new MarketplaceException(CODIGO, "Integración pendiente: prueba mínima de acceso OAuth a la API MCO");
    }
}
