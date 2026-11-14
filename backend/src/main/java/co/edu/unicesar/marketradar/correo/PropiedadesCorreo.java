package co.edu.unicesar.marketradar.correo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Servidor de correo (SMTP) y dirección pública de la página, leídos de variables de entorno. La clave nunca se
 * escribe en el repositorio ni en los registros.
 *
 * @param host servidor SMTP, por ejemplo {@code smtp.gmail.com}; vacío si no hay correo configurado
 * @param puerto puerto (587 con STARTTLS, 465 con SSL)
 * @param usuario usuario del servidor SMTP
 * @param clave clave o contraseña de aplicación
 * @param remitente dirección que aparece como remitente
 * @param nombreRemitente nombre que aparece como remitente
 * @param starttls si se usa STARTTLS
 * @param ssl si se usa SSL directo (puerto 465)
 * @param timeoutSegundos espera máxima del servidor
 * @param urlPublica dirección pública de la página, para los enlaces de los correos
 */
@ConfigurationProperties(prefix = "marketradar.correo")
public record PropiedadesCorreo(String host, int puerto, String usuario, String clave, String remitente,
                                String nombreRemitente, boolean starttls, boolean ssl, int timeoutSegundos,
                                String urlPublica) {

    /** Valores por defecto para lo que no se configuró. */
    public PropiedadesCorreo {
        puerto = puerto <= 0 ? 587 : puerto;
        timeoutSegundos = timeoutSegundos <= 0 ? 10 : timeoutSegundos;
        nombreRemitente = nombreRemitente == null || nombreRemitente.isBlank() ? "Marketradar" : nombreRemitente;
        urlPublica = urlPublica == null || urlPublica.isBlank() ? "http://localhost:8080"
                : urlPublica.replaceAll("/+$", "");
    }

    /** @return {@code true} si hay servidor y remitente */
    public boolean configurado() {
        return host != null && !host.isBlank() && remitente != null && !remitente.isBlank();
    }

    @Override
    public String toString() {
        return "PropiedadesCorreo[host=" + host + ", puerto=" + puerto + ", remitente=" + remitente + "]";
    }
}
