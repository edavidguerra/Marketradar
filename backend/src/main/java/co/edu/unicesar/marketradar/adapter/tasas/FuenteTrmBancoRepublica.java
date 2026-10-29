package co.edu.unicesar.marketradar.adapter.tasas;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * TRM (Tasa Representativa del Mercado) oficial del dólar, certificada por la Superintendencia
 * Financiera y publicada en datos abiertos del Gobierno de Colombia (conjunto {@code 32sa-8pi3}).
 * No requiere clave. Formato de respuesta por confirmar en https://www.datos.gov.co.
 */
@Component
@Order(1)
public class FuenteTrmBancoRepublica implements FuenteTasaCambio {

    static final String NOMBRE = "TRM Superintendencia Financiera (datos.gov.co)";

    private final RestClient cliente;
    private final URI url;

    public FuenteTrmBancoRepublica(RestClient clienteTasas, PropiedadesMarketradar propiedades) {
        this.cliente = clienteTasas;
        this.url = URI.create(propiedades.moneda().urlTrm());
    }

    @Override
    public String nombre() {
        return NOMBRE;
    }

    @Override
    public List<TasaCambio> consultar() {
        RegistroTrm[] registros = cliente.get().uri(url).retrieve().body(RegistroTrm[].class);
        if (registros == null || registros.length == 0 || registros[0].valor() == null) {
            throw new IllegalStateException("La TRM llegó vacía");
        }
        RegistroTrm ultimo = registros[0];
        LocalDate vigencia = LocalDate.parse(ultimo.vigenciadesde().substring(0, 10));
        return List.of(new TasaCambio("USD", new BigDecimal(ultimo.valor()), vigencia, NOMBRE));
    }

    /**
     * Fila del conjunto de datos de la TRM.
     *
     * @param valor pesos por dólar
     * @param vigenciadesde fecha desde la que rige, en formato ISO
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RegistroTrm(String valor, String vigenciadesde) {
    }
}
