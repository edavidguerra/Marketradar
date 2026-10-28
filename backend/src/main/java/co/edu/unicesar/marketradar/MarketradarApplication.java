package co.edu.unicesar.marketradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de Marketradar, el comparador de precios accesible para adultos mayores.
 *
 * <p>Spring Boot se usa solo en la capa web; el acceso a datos se hace con JDBC y el patrón DAO.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MarketradarApplication {

    /**
     * Inicia la aplicación.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(MarketradarApplication.class, args);
    }
}
