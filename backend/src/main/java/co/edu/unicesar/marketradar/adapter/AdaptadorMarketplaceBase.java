package co.edu.unicesar.marketradar.adapter;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Lógica común de los adaptadores (método plantilla): verifica que esté habilitado, respeta el límite
 * de uso, traduce errores a {@link MarketplaceException} y convierte los precios a COP.
 * Cada subclase solo implementa {@link #consultarApi(ConsultaBusqueda)}.
 */
public abstract class AdaptadorMarketplaceBase implements AdaptadorMarketplace {

    private final String codigo;
    private final ConversorMoneda conversor;
    private final RateLimiter limitador;

    /**
     * @param codigo código del marketplace
     * @param conversor conversor de moneda
     * @param llamadasPorSegundo límite de uso de la API (por confirmar en la documentación oficial)
     */
    protected AdaptadorMarketplaceBase(String codigo, ConversorMoneda conversor, int llamadasPorSegundo) {
        this.codigo = codigo;
        this.conversor = conversor;
        this.limitador = RateLimiter.of(codigo, RateLimiterConfig.custom()
                .limitForPeriod(llamadasPorSegundo)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofMillis(500))
                .build());
    }

    @Override
    public final String codigo() {
        return codigo;
    }

    @Override
    public final List<OfertaNormalizada> buscar(ConsultaBusqueda consulta) {
        if (!estaHabilitado()) {
            throw new MarketplaceException(codigo, "El adaptador no está habilitado");
        }
        List<OfertaNormalizada> ofertasOriginales;
        try {
            ofertasOriginales = limitador.executeSupplier(() -> consultarApi(consulta));
        } catch (RequestNotPermitted e) {
            throw new MarketplaceException(codigo, "Se alcanzó el límite de uso de la API", e);
        } catch (MarketplaceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MarketplaceException(codigo, "Error inesperado al consultar la API", e);
        }
        return convertirAMonedaDestino(ofertasOriginales);
    }

    /**
     * Llama a la API del marketplace y mapea su respuesta al modelo común (GRASP Creator),
     * en la moneda original de la API.
     *
     * @param consulta parámetros de la búsqueda
     * @return ofertas encontradas
     */
    protected abstract List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta);

    private List<OfertaNormalizada> convertirAMonedaDestino(List<OfertaNormalizada> ofertas) {
        String destino = conversor.getMonedaDestino();
        List<OfertaNormalizada> convertidas = new ArrayList<>();
        for (OfertaNormalizada oferta : ofertas) {
            if (destino.equals(oferta.getMoneda())) {
                convertidas.add(oferta);
            } else {
                convertidas.add(oferta.conPrecio(
                        conversor.convertir(oferta.getPrecio(), oferta.getMoneda()),
                        conversor.convertir(oferta.getCostoEnvio(), oferta.getMoneda()),
                        destino));
            }
        }
        return convertidas;
    }

    /**
     * Indica si un valor de configuración tiene contenido.
     *
     * @param valor valor leído de la configuración
     * @return {@code true} si no es nulo ni vacío
     */
    protected static boolean tieneValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
