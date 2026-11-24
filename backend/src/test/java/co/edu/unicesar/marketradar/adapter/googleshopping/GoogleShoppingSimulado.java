package co.edu.unicesar.marketradar.adapter.googleshopping;

import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Adaptador de Google Shopping real que responde siempre con un JSON fijo de SerpApi, para pruebas de recorrido. */
public final class GoogleShoppingSimulado {

    private static final String URL = "http://localhost/serpapi";

    private GoogleShoppingSimulado() {
    }

    /**
     * @param conversor conversor de moneda
     * @param respuesta JSON que devuelve SerpApi
     * @return adaptador habilitado que no sale a la red
     */
    public static AdaptadorGoogleShopping crear(ConversorMoneda conversor, String respuesta) {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL).messageConverters(c -> c.addFirst(
                new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE))));
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(manyTimes(), requestTo(startsWith(URL + "/search.json")))
                .andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));
        return new AdaptadorGoogleShopping(conversor, builder.build(), true, "clave-de-prueba");
    }
}
