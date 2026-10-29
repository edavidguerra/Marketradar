package co.edu.unicesar.marketradar.adapter;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Convierte precios a pesos colombianos con tasas de cambio configurables.
 */
@Component
public class ConversorMoneda {

    private final String monedaDestino;
    private final Map<String, BigDecimal> tasasADestino = new HashMap<>();
    private final LocalDate fechaTasa;

    /**
     * Crea el conversor a partir de la configuración.
     *
     * @param propiedades configuración con la tasa USD-COP y su fecha
     */
    public ConversorMoneda(PropiedadesMarketradar propiedades) {
        this.monedaDestino = propiedades.moneda().destino();
        this.fechaTasa = propiedades.moneda().fechaTasa();
        tasasADestino.put(monedaDestino, BigDecimal.ONE);
        tasasADestino.put("USD", propiedades.moneda().tasaUsdCop());
    }

    /**
     * Convierte un valor a la moneda destino, redondeado a dos decimales.
     *
     * @param valor valor en la moneda de origen; {@code null} devuelve {@code null}
     * @param monedaOrigen código ISO 4217 de origen
     * @return valor convertido
     * @throws IllegalArgumentException si no hay tasa configurada para la moneda
     */
    public BigDecimal convertir(BigDecimal valor, String monedaOrigen) {
        if (valor == null) {
            return null;
        }
        BigDecimal tasa = tasasADestino.get(monedaOrigen);
        if (tasa == null) {
            throw new IllegalArgumentException("No hay tasa de cambio configurada para " + monedaOrigen);
        }
        return valor.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
    }

    public String getMonedaDestino() {
        return monedaDestino;
    }

    public LocalDate getFechaTasa() {
        return fechaTasa;
    }
}
