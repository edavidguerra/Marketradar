package co.edu.unicesar.marketradar.adapter.shopify;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplaceBase;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Integración con Shopify.
 *
 * <p><b>Pendiente de decisión del equipo:</b> Catalog API global o Storefront MCP por tienda, según
 * los requisitos de acceso y términos de uso que se verifiquen.</p>
 */
@Component
public class AdaptadorShopify extends AdaptadorMarketplaceBase {

    public static final String CODIGO = "shopify";

    private final PropiedadesMarketradar.Shopify configuracion;

    public AdaptadorShopify(PropiedadesMarketradar propiedades, ConversorMoneda conversor) {
        super(CODIGO, conversor, 2);
        this.configuracion = propiedades.marketplaces().shopify();
    }

    @Override
    public boolean estaHabilitado() {
        return configuracion.habilitado() && tieneValor(configuracion.token());
    }

    @Override
    protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
        throw new MarketplaceException(CODIGO, "Integración pendiente: definir Catalog API o Storefront MCP");
    }
}
