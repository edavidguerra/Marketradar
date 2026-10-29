package co.edu.unicesar.marketradar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Beans de uso general.
 */
@Configuration
public class ConfiguracionGeneral {

    /** Zona horaria de Colombia, usada para fechas de negocio como la mayoría de edad. */
    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    /**
     * Reloj del sistema en hora de Colombia; las pruebas pueden reemplazarlo por uno fijo.
     *
     * @return reloj
     */
    @Bean
    public Clock reloj() {
        return Clock.system(ZONA_COLOMBIA);
    }
}
