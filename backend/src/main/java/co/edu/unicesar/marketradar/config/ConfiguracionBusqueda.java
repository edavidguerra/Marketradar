package co.edu.unicesar.marketradar.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Beans de infraestructura de la búsqueda: pool de hilos acotado y circuit breakers por marketplace.
 */
@Configuration
public class ConfiguracionBusqueda {

    /**
     * Pool de hilos de tamaño fijo para consultar los marketplaces en paralelo.
     *
     * @param propiedades configuración de la aplicación
     * @return ejecutor de tareas
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService ejecutorBusqueda(PropiedadesMarketradar propiedades) {
        return Executors.newFixedThreadPool(propiedades.busqueda().hilos());
    }

    /**
     * Registro de circuit breakers: si un marketplace falla seguido, se deja de consultar un tiempo.
     *
     * @return registro con la configuración común
     */
    @Bean
    public CircuitBreakerRegistry registroCircuitBreakers() {
        CircuitBreakerConfig configuracion = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        return CircuitBreakerRegistry.of(configuracion);
    }
}
