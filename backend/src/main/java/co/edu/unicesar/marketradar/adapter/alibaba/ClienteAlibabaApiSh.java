package co.edu.unicesar.marketradar.adapter.alibaba;

import co.edu.unicesar.marketradar.config.PropiedadesMayoristas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.time.Duration;

/**
 * Cliente de API.SH para Alibaba ({@code GET /alibaba/v1/search} y {@code GET /alibaba/v1/product}). Manda la clave en
 * el encabezado {@code x-api-key} y traduce los códigos de error que documenta API.SH a {@link ApiShException}. La
 * clave nunca aparece en los mensajes ni en los registros.
 */
@Component
public class ClienteAlibabaApiSh {

    private static final Logger LOG = LoggerFactory.getLogger(ClienteAlibabaApiSh.class);

    private final RestClient cliente;
    private final String apiKey;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param builder constructor de clientes HTTP de Spring
     * @param propiedades configuración de API.SH
     */
    @Autowired
    public ClienteAlibabaApiSh(RestClient.Builder builder, PropiedadesMayoristas propiedades) {
        this(builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(Duration.ofSeconds(10))
                                .withReadTimeout(Duration.ofSeconds(propiedades.timeoutSegundos()))))
                .baseUrl(propiedades.urlApi())
                .build(), propiedades.apiKey());
    }

    /** Para las pruebas, con el cliente HTTP ya creado. */
    ClienteAlibabaApiSh(RestClient cliente, String apiKey) {
        this.cliente = cliente;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    /** @return {@code true} si hay clave */
    public boolean estaConfigurado() {
        return !apiKey.isEmpty();
    }

    /**
     * Busca productos. Cada llamada cuesta un crédito, aunque API.SH la sirva de su caché.
     *
     * @param termino texto a buscar
     * @param pagina página, de 1 a 100
     * @return página normalizada
     */
    public LectorRespuestasAlibaba.Pagina buscar(String termino, int pagina) {
        JsonNode raiz = pedir("/alibaba/v1/search?q={q}&page={pagina}", termino, pagina);
        return LectorRespuestasAlibaba.leerBusqueda(raiz);
    }

    /**
     * Trae el detalle de un producto: escalas de precio, pedido mínimo y variantes.
     *
     * @param urlOId enlace de Alibaba o identificador del producto
     * @return respuesta tal como llegó, para leerla con {@link LectorRespuestasAlibaba#leerProducto}
     */
    public JsonNode producto(String urlOId) {
        return pedir("/alibaba/v1/product?url={url}", urlOId);
    }

    private JsonNode pedir(String ruta, Object... variables) {
        if (!estaConfigurado()) {
            throw new ApiShException(ApiShException.Tipo.CLAVE_INVALIDA, "Falta APISH_KEY", null);
        }
        try {
            String cuerpo = cliente.get()
                    .uri(ruta, variables)
                    .header("x-api-key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if (cuerpo == null || cuerpo.isBlank()) {
                throw new ApiShException(ApiShException.Tipo.RESPUESTA_INVALIDA, "Respuesta vacía", null);
            }
            return json.readTree(cuerpo);
        } catch (RestClientResponseException e) {
            int codigo = e.getStatusCode().value();
            LOG.warn("API.SH respondió {} en {}", codigo, ruta.substring(0, ruta.indexOf('?')));
            throw traducir(codigo, e.getResponseHeaders() == null ? null
                    : e.getResponseHeaders().getFirst("retry-after"));
        } catch (IOException e) {
            throw new ApiShException(ApiShException.Tipo.RESPUESTA_INVALIDA, "JSON no válido", null);
        } catch (RestClientException e) {
            LOG.warn("No se pudo consultar API.SH: {}", e.getClass().getSimpleName());
            throw new ApiShException(ApiShException.Tipo.NO_DISPONIBLE, e.getClass().getSimpleName(), null);
        }
    }

    static ApiShException traducir(int codigo, String retryAfter) {
        return switch (codigo) {
            case 400 -> new ApiShException(ApiShException.Tipo.PARAMETRO_INVALIDO, "400", null);
            case 401, 403 -> new ApiShException(ApiShException.Tipo.CLAVE_INVALIDA, String.valueOf(codigo), null);
            case 402 -> new ApiShException(ApiShException.Tipo.SIN_CREDITOS, "402", null);
            case 404 -> new ApiShException(ApiShException.Tipo.NO_ENCONTRADO, "404", null);
            case 429 -> new ApiShException(ApiShException.Tipo.LIMITE, "429", segundos(retryAfter));
            default -> new ApiShException(ApiShException.Tipo.NO_DISPONIBLE, String.valueOf(codigo), null);
        };
    }

    private static Long segundos(String retryAfter) {
        if (retryAfter == null || !retryAfter.strip().matches("\\d{1,6}")) {
            return 60L;
        }
        return Math.max(1L, Long.parseLong(retryAfter.strip()));
    }
}
