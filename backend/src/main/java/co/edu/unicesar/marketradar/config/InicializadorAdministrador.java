package co.edu.unicesar.marketradar.config;

import co.edu.unicesar.marketradar.exception.ValidacionException;
import co.edu.unicesar.marketradar.service.ServicioCuentas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crea la cuenta de administrador al arrancar, con los datos de las variables
 * {@code ADMIN_CORREO} y {@code ADMIN_CLAVE}. Así la contraseña nunca queda en el repositorio.
 */
@Component
public class InicializadorAdministrador implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InicializadorAdministrador.class);

    private final ServicioCuentas servicioCuentas;
    private final PropiedadesMarketradar.Administrador administrador;

    public InicializadorAdministrador(ServicioCuentas servicioCuentas, PropiedadesMarketradar propiedades) {
        this.servicioCuentas = servicioCuentas;
        this.administrador = propiedades.administrador();
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (administrador == null || administrador.correo() == null || administrador.correo().isBlank()) {
            LOG.info("No se configuró ADMIN_CORREO: no se crea la cuenta de administrador inicial");
            return;
        }
        try {
            if (servicioCuentas.crearAdministradorSiNoExiste(administrador.nombres(), administrador.apellidos(),
                    administrador.correo(), administrador.clave())) {
                LOG.info("Cuenta de administrador creada para {}", administrador.correo());
            }
        } catch (ValidacionException e) {
            LOG.error("No se pudo crear el administrador inicial: {}", e.getMessage());
        }
    }
}
