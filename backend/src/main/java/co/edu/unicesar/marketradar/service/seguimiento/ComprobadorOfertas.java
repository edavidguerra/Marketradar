package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplace;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import co.edu.unicesar.marketradar.service.ServicioPersistenciaOfertas;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Vuelve a consultar una sola tienda para encontrar las ofertas seguidas. Usa el mismo adaptador, el mismo límite de
 * uso y el mismo circuit breaker que la búsqueda, y guarda los precios nuevos en el historial.
 */
@Component
public class ComprobadorOfertas {

    private static final Logger LOG = LoggerFactory.getLogger(ComprobadorOfertas.class);

    private final Map<String, AdaptadorMarketplace> adaptadores = new HashMap<>();
    private final CircuitBreakerRegistry circuitBreakers;
    private final ServicioPersistenciaOfertas persistencia;

    public ComprobadorOfertas(List<AdaptadorMarketplace> adaptadores, CircuitBreakerRegistry circuitBreakers,
                              ServicioPersistenciaOfertas persistencia) {
        adaptadores.forEach(adaptador -> this.adaptadores.put(adaptador.codigo(), adaptador));
        this.circuitBreakers = circuitBreakers;
        this.persistencia = persistencia;
    }

    /**
     * Busca en una tienda.
     *
     * @param codigoMarketplace tienda
     * @param termino lo que se busca
     * @return ofertas encontradas (con sus precios en pesos)
     * @throws MarketplaceException si la tienda no está habilitada o no respondió
     */
    public List<OfertaNormalizada> consultar(String codigoMarketplace, String termino) {
        AdaptadorMarketplace adaptador = adaptadores.get(codigoMarketplace);
        if (adaptador == null || !adaptador.estaHabilitado()) {
            throw new MarketplaceException(codigoMarketplace, "La tienda no está habilitada");
        }
        ConsultaBusqueda consulta = ConsultaBusqueda.crear(termino, null, Set.of(codigoMarketplace), 1,
                ConsultaBusqueda.TAMANO_MAXIMO);
        long inicio = System.nanoTime();
        boolean exitoso = false;
        try {
            List<OfertaNormalizada> ofertas = circuitBreakers.circuitBreaker(codigoMarketplace)
                    .executeSupplier(() -> adaptador.buscar(consulta));
            exitoso = true;
            try {
                persistencia.guardarOfertas(ofertas);
            } catch (AccesoDatosException e) {
                LOG.error("No se pudieron guardar los precios comprobados de {}", codigoMarketplace, e);
            }
            return ofertas;
        } catch (MarketplaceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MarketplaceException(codigoMarketplace, "La tienda no respondió: " + e.getClass().getSimpleName());
        } finally {
            try {
                persistencia.registrarConsumoApi(codigoMarketplace, exitoso,
                        (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
            } catch (AccesoDatosException e) {
                LOG.error("No se pudo registrar el consumo de {}", codigoMarketplace, e);
            }
        }
    }
}
