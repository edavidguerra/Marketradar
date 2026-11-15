package co.edu.unicesar.marketradar.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Elige cómo se mandan los correos según {@link PropiedadesCorreo#modo()} y deja en el registro de arranque qué se
 * eligió (sin claves). Si se configuró SMTP en Render con un puerto que el plan gratis bloquea, lo advierte: así el
 * fallo no se confunde con una clave equivocada.
 */
@Configuration
public class ConfiguracionCorreo {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracionCorreo.class);

    @Bean
    public TransporteCorreo transporteCorreo(PropiedadesCorreo propiedades) {
        PropiedadesCorreo.Modo modo = propiedades.modo();
        boolean enRender = System.getenv("RENDER") != null || System.getenv("RENDER_EXTERNAL_URL") != null;
        TransporteCorreo transporte = switch (modo) {
            case BREVO, RESEND -> new TransporteCorreoHttp(propiedades, HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(propiedades.timeoutSegundos())).build());
            case SMTP, NINGUNO -> new TransporteCorreoSmtp(propiedades);
        };
        LOG.info("Correo: modo {}, remitente {}, configurado {}", modo, propiedades.remitente() == null ? "(vacío)"
                : MensajeCorreo.ocultar(propiedades.remitente()), transporte.estaConfigurado());
        if (modo == PropiedadesCorreo.Modo.SMTP && enRender && propiedades.puertoSmtpBloqueadoEnRenderGratis()) {
            LOG.warn("Correo por SMTP al puerto {} en Render: el plan gratis bloquea la salida a los puertos 25, 465 y "
                    + "587, así que los correos no saldrán. Use CORREO_PROVEEDOR=brevo (o resend) con CORREO_API_KEY.",
                    propiedades.puerto());
        }
        return transporte;
    }
}
