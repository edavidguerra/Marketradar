package co.edu.unicesar.marketradar.adapter;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Convierte precios a pesos colombianos con la tasa vigente que entrega {@link ProveedorTasas}
 * (TRM del día para dólares).
 */
@Component
public class ConversorMoneda {

    private final String monedaDestino;
    private final ProveedorTasas proveedorTasas;

    /**
     * @param propiedades configuración con la moneda destino
     * @param proveedorTasas fuente de las tasas vigentes
     */
    public ConversorMoneda(PropiedadesMarketradar propiedades, ProveedorTasas proveedorTasas) {
        this.monedaDestino = propiedades.moneda().destino();
        this.proveedorTasas = proveedorTasas;
    }

    /**
     * Convierte un valor a la moneda destino. Los pesos se redondean a valores enteros, como se
     * muestran en Colombia.
     *
     * @param valor valor en la moneda de origen; {@code null} devuelve {@code null}
     * @param monedaOrigen código ISO 4217 de origen
     * @return valor convertido
     * @throws IllegalArgumentException si no hay tasa conocida para la moneda
     */
    public BigDecimal convertir(BigDecimal valor, String monedaOrigen) {
        if (valor == null) {
            return null;
        }
        if (monedaDestino.equals(monedaOrigen)) {
            return valor;
        }
        BigDecimal tasa = proveedorTasas.tasaVigente(monedaOrigen)
                .orElseThrow(() -> new IllegalArgumentException("No hay tasa de cambio para " + monedaOrigen))
                .valor();
        return valor.multiply(tasa).setScale(0, RoundingMode.HALF_UP);
    }

    public String getMonedaDestino() {
        return monedaDestino;
    }
}
