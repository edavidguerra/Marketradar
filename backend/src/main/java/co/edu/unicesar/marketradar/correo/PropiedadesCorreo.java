package co.edu.unicesar.marketradar.correo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Locale;

/**
 * Servicio de correo y dirección pública de la página, leídos de variables de entorno. Las claves nunca se escriben en
 * el repositorio ni en los registros.
 *
 * <p>Hay dos formas de mandar: SMTP ({@code SMTP_HOST}...) o la API HTTPS de un proveedor ({@code CORREO_PROVEEDOR} =
 * {@code brevo} o {@code resend} y {@code CORREO_API_KEY}). El plan gratis de Render bloquea la salida a los puertos
 * SMTP 25, 465 y 587, así que allí solo funciona la API HTTPS (puerto 443).</p>
 *
 * @param host servidor SMTP, por ejemplo {@code smtp.gmail.com}; vacío si no hay SMTP
 * @param puerto puerto SMTP (587 con STARTTLS, 465 con SSL)
 * @param usuario usuario del servidor SMTP
 * @param clave clave o contraseña de aplicación SMTP
 * @param remitente dirección que aparece como remitente (debe estar verificada en el proveedor)
 * @param nombreRemitente nombre que aparece como remitente
 * @param starttls si se usa STARTTLS
 * @param ssl si se usa SSL directo (puerto 465)
 * @param timeoutSegundos espera máxima del servicio de correo
 * @param urlPublica dirección pública de la página, para los enlaces de los correos
 * @param proveedor {@code auto}, {@code smtp}, {@code brevo} o {@code resend}
 * @param apiKey clave de la API del proveedor (Brevo o Resend)
 * @param urlApi dirección base de la API; vacía usa la oficial del proveedor
 */
@ConfigurationProperties(prefix = "marketradar.correo")
public record PropiedadesCorreo(String host, int puerto, String usuario, String clave, String remitente,
                                String nombreRemitente, boolean starttls, boolean ssl, int timeoutSegundos,
                                String urlPublica, String proveedor, String apiKey, String urlApi) {

    /** Valores por defecto para lo que no se configuró. */
    @ConstructorBinding
    public PropiedadesCorreo {
        puerto = puerto <= 0 ? 587 : puerto;
        timeoutSegundos = timeoutSegundos <= 0 ? 10 : timeoutSegundos;
        nombreRemitente = nombreRemitente == null || nombreRemitente.isBlank() ? "Marketradar" : nombreRemitente;
        urlPublica = urlPublica == null || urlPublica.isBlank() ? "http://localhost:8080"
                : urlPublica.replaceAll("/+$", "");
        proveedor = proveedor == null || proveedor.isBlank() ? "auto" : proveedor.strip().toLowerCase(Locale.ROOT);
        apiKey = apiKey == null ? "" : apiKey.strip();
        urlApi = urlApi == null ? "" : urlApi.strip().replaceAll("/+$", "");
    }

    /**
     * Solo SMTP (como antes de la API HTTPS).
     *
     * @param host servidor SMTP
     * @param puerto puerto
     * @param usuario usuario
     * @param clave clave
     * @param remitente remitente
     * @param nombreRemitente nombre del remitente
     * @param starttls STARTTLS
     * @param ssl SSL
     * @param timeoutSegundos espera
     * @param urlPublica dirección pública
     */
    public PropiedadesCorreo(String host, int puerto, String usuario, String clave, String remitente,
                             String nombreRemitente, boolean starttls, boolean ssl, int timeoutSegundos,
                             String urlPublica) {
        this(host, puerto, usuario, clave, remitente, nombreRemitente, starttls, ssl, timeoutSegundos, urlPublica,
                "smtp", null, null);
    }

    /** Cómo se mandan los correos. */
    public enum Modo { SMTP, BREVO, RESEND, NINGUNO }

    /**
     * Decide el modo: si se nombró un proveedor, ese; con {@code auto}, la API si hay {@code CORREO_API_KEY} (Resend si
     * la clave empieza por {@code re_}, si no Brevo) y SMTP si hay {@code SMTP_HOST}.
     *
     * @return modo de envío
     */
    public Modo modo() {
        return switch (proveedor) {
            case "smtp" -> Modo.SMTP;
            case "brevo" -> Modo.BREVO;
            case "resend" -> Modo.RESEND;
            case "ninguno", "none" -> Modo.NINGUNO;
            default -> !apiKey.isEmpty() ? (apiKey.startsWith("re_") ? Modo.RESEND : Modo.BREVO)
                    : host != null && !host.isBlank() ? Modo.SMTP : Modo.NINGUNO;
        };
    }

    /** @return {@code true} si hay remitente y lo que pide el modo (servidor SMTP o clave de la API) */
    public boolean configurado() {
        boolean hayRemitente = remitente != null && !remitente.isBlank();
        return hayRemitente && switch (modo()) {
            case SMTP -> host != null && !host.isBlank();
            case BREVO, RESEND -> !apiKey.isEmpty();
            case NINGUNO -> false;
        };
    }

    /** @return {@code true} si el puerto SMTP es uno de los que bloquea el plan gratis de Render */
    public boolean puertoSmtpBloqueadoEnRenderGratis() {
        return puerto == 25 || puerto == 465 || puerto == 587;
    }

    /** No muestra claves ni usuarios. */
    @Override
    public String toString() {
        return "PropiedadesCorreo[modo=" + modo() + ", host=" + host + ", puerto=" + puerto + ", remitente="
                + remitente + ", apiKey=" + (apiKey.isEmpty() ? "(vacía)" : "****") + "]";
    }
}
