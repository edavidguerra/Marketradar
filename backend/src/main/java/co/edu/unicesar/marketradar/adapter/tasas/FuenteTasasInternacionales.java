package co.edu.unicesar.marketradar.adapter.tasas;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tasas de mercado para monedas distintas del dólar (euro, real, peso mexicano, yuan...), del servicio
 * gratuito ExchangeRate-API (open.er-api.com, sin clave, actualización diaria; condiciones por confirmar).
 * Se consulta con base USD y se calcula cuántos pesos vale cada moneda.
 */
@Component
@Order(2)
public class FuenteTasasInternacionales implements FuenteTasaCambio {

    static final String NOMBRE = "ExchangeRate-API (open.er-api.com)";

    private final RestClient cliente;
    private final URI url;
    private final List<String> monedas;

    public FuenteTasasInternacionales(RestClient clienteTasas, PropiedadesMarketradar propiedades) {
        this.cliente = clienteTasas;
        this.url = URI.create(propiedades.moneda().urlTasasInternacionales());
        this.monedas = propiedades.moneda().monedasAdicionales();
    }

    @Override
    public String nombre() {
        return NOMBRE;
    }

    @Override
    public List<TasaCambio> consultar() {
        RespuestaTasas respuesta = cliente.get().uri(url).retrieve().body(RespuestaTasas.class);
        if (respuesta == null || !"success".equals(respuesta.resultado()) || respuesta.tasas() == null
                || respuesta.tasas().get("COP") == null) {
            throw new IllegalStateException("Respuesta de tasas no válida");
        }
        BigDecimal pesosPorDolar = respuesta.tasas().get("COP");
        LocalDate hoy = LocalDate.now();
        List<TasaCambio> tasas = new ArrayList<>();
        tasas.add(new TasaCambio("USD", pesosPorDolar.setScale(4, RoundingMode.HALF_UP), hoy, NOMBRE));
        for (String moneda : monedas) {
            BigDecimal unidadesPorDolar = respuesta.tasas().get(moneda);
            if (unidadesPorDolar != null && unidadesPorDolar.signum() > 0) {
                BigDecimal pesos = pesosPorDolar.divide(unidadesPorDolar, MathContext.DECIMAL64)
                        .setScale(4, RoundingMode.HALF_UP);
                tasas.add(new TasaCambio(moneda, pesos, hoy, NOMBRE));
            }
        }
        return tasas;
    }

    /**
     * @param resultado {@code success} si la consulta funcionó
     * @param tasas unidades de cada moneda por un dólar
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaTasas(@JsonProperty("result") String resultado, @JsonProperty("rates") Map<String, BigDecimal> tasas) {
    }
}
