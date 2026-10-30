package co.edu.unicesar.marketradar.adapter.mercadolibre;

import co.edu.unicesar.marketradar.exception.MarketplaceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Token de acceso de Mercado Libre. Lo renueva con el refresh token antes de que venza.
 *
 * <p>Mercado Libre solo permite usar cada refresh token una vez y entrega uno nuevo en cada renovación,
 * así que el nuevo se guarda en un archivo. Al arrancar se usa el del archivo, si existe, y si no, el
 * de la configuración.</p>
 */
class TokenMercadoLibre {

    private static final Logger LOG = LoggerFactory.getLogger(TokenMercadoLibre.class);
    private static final Duration MARGEN = Duration.ofMinutes(5);

    private final RestClient cliente;
    private final String clientId;
    private final String clientSecret;
    private final Path archivo;
    private final Clock reloj;

    private String refreshToken;
    private String accessToken;
    private Instant venceEn = Instant.MIN;

    /**
     * @param cliente cliente HTTP con la dirección base de la API
     * @param clientId identificador de la aplicación
     * @param clientSecret secreto de la aplicación
     * @param refreshTokenInicial refresh token de la configuración
     * @param archivo archivo donde se guarda el refresh token vigente; {@code null} para no guardarlo
     * @param reloj reloj para calcular el vencimiento
     */
    TokenMercadoLibre(RestClient cliente, String clientId, String clientSecret, String refreshTokenInicial,
                      Path archivo, Clock reloj) {
        this.cliente = cliente;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.archivo = archivo;
        this.reloj = reloj;
        String guardado = leerArchivo();
        this.refreshToken = guardado != null ? guardado : refreshTokenInicial;
    }

    /**
     * Devuelve un token de acceso vigente y lo renueva si está por vencer.
     *
     * @return token de acceso
     * @throws MarketplaceException si Mercado Libre rechaza la renovación
     */
    synchronized String vigente() {
        if (accessToken == null || !reloj.instant().isBefore(venceEn.minus(MARGEN))) {
            renovar();
        }
        return accessToken;
    }

    /**
     * Descarta el token actual, por ejemplo cuando la API responde 401, para pedir otro en la siguiente llamada.
     */
    synchronized void invalidar() {
        accessToken = null;
    }

    private void renovar() {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("grant_type", "refresh_token");
        formulario.add("client_id", clientId);
        formulario.add("client_secret", clientSecret);
        formulario.add("refresh_token", refreshToken);
        RespuestaToken respuesta;
        try {
            respuesta = cliente.post().uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formulario)
                    .retrieve()
                    .body(RespuestaToken.class);
        } catch (HttpClientErrorException e) {
            throw new MarketplaceException(AdaptadorMercadoLibre.CODIGO,
                    "Mercado Libre rechazó el refresh token (" + e.getStatusCode().value()
                            + "). Hay que generar uno nuevo con el flujo de autorización.", e);
        }
        if (respuesta == null || respuesta.accessToken() == null) {
            throw new MarketplaceException(AdaptadorMercadoLibre.CODIGO, "Mercado Libre no devolvió el token de acceso");
        }
        accessToken = respuesta.accessToken();
        venceEn = reloj.instant().plusSeconds(respuesta.expiresIn());
        if (respuesta.refreshToken() != null && !respuesta.refreshToken().equals(refreshToken)) {
            refreshToken = respuesta.refreshToken();
            guardarArchivo(refreshToken);
        }
    }

    private String leerArchivo() {
        if (archivo == null || !Files.isRegularFile(archivo)) {
            return null;
        }
        try {
            String contenido = Files.readString(archivo, StandardCharsets.UTF_8).strip();
            return contenido.isEmpty() ? null : contenido;
        } catch (IOException e) {
            LOG.warn("No se pudo leer el refresh token de Mercado Libre en {}", archivo, e);
            return null;
        }
    }

    private void guardarArchivo(String token) {
        if (archivo == null) {
            return;
        }
        try {
            Path carpeta = archivo.toAbsolutePath().getParent();
            if (carpeta != null) {
                Files.createDirectories(carpeta);
            }
            Files.writeString(archivo, token, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("No se pudo guardar el refresh token de Mercado Libre en {}. Al reiniciar habrá que generar otro.",
                    archivo, e);
        }
    }

    /**
     * Respuesta de {@code POST /oauth/token}.
     *
     * @param accessToken token de acceso
     * @param expiresIn segundos de vigencia
     * @param refreshToken refresh token nuevo
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaToken(@JsonProperty("access_token") String accessToken,
                          @JsonProperty("expires_in") long expiresIn,
                          @JsonProperty("refresh_token") String refreshToken) {
    }
}
