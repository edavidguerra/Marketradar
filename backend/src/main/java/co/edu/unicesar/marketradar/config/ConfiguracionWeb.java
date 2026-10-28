package co.edu.unicesar.marketradar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración web: CORS con lista de orígenes permitidos para la API.
 */
@Configuration
public class ConfiguracionWeb implements WebMvcConfigurer {

    private final PropiedadesMarketradar propiedades;

    public ConfiguracionWeb(PropiedadesMarketradar propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public void addCorsMappings(CorsRegistry registro) {
        registro.addMapping("/api/**")
                .allowedOrigins(propiedades.cors().origenesPermitidos().toArray(String[]::new))
                .allowedMethods("GET", "POST");
    }
}
