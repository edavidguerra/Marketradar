package co.edu.unicesar.marketradar.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente HTTP con tiempos de espera cortos para las fuentes de tasas de cambio, y tareas programadas
 * para actualizarlas cada día.
 */
@Configuration
@EnableScheduling
public class ConfiguracionTasas {

    @Bean
    public RestClient clienteTasas(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings tiempos = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(5));
        return builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(tiempos)).build();
    }
}
