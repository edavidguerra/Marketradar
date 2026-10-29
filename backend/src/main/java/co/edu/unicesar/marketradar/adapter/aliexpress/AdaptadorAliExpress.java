package co.edu.unicesar.marketradar.adapter.aliexpress;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplaceBase;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Integración con la API de afiliados de AliExpress Open Platform.
 *
 * <p><b>Por confirmar en la documentación oficial:</b> método de búsqueda de productos de afiliados,
 * algoritmo de firma (HMAC-SHA256 o MD5), parámetros obligatorios y límites de uso. Sin Tracking ID
 * la API puede devolver resultados vacíos sin error.</p>
 */
@Component
public class AdaptadorAliExpress extends AdaptadorMarketplaceBase {

    public static final String CODIGO = "aliexpress";

    private final PropiedadesMarketradar.AliExpress configuracion;

    public AdaptadorAliExpress(PropiedadesMarketradar propiedades, ConversorMoneda conversor) {
        super(CODIGO, conversor, 5);
        this.configuracion = propiedades.marketplaces().aliexpress();
    }

    @Override
    public boolean estaHabilitado() {
        return configuracion.habilitado()
                && tieneValor(configuracion.appKey())
                && tieneValor(configuracion.appSecret())
                && tieneValor(configuracion.trackingId());
    }

    @Override
    protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
        throw new MarketplaceException(CODIGO, "Integración pendiente: verificar la API de afiliados de AliExpress");
    }
}
