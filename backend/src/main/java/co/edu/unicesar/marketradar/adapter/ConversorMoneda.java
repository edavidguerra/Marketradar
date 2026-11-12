package co.edu.unicesar.marketradar.adapter;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Convierte precios a pesos colombianos con la tasa vigente que entrega {@link ProveedorTasas}. Cada tasa trae su
 * fuente y la fecha desde la que rige (la TRM oficial más reciente para dólares, o la tasa de respaldo si esa no se
 * pudo consultar), y la oferta convertida las guarda para mostrarlas.
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

    /**
     * Tasa con que se convierte una moneda a la moneda destino, con su fuente y fecha.
     *
     * @param monedaOrigen código ISO 4217
     * @return tasa vigente, o vacío si no hay ninguna
     */
    public Optional<TasaCambio> tasaPara(String monedaOrigen) {
        return proveedorTasas.tasaVigente(monedaOrigen);
    }

    /**
     * Convierte con una tasa ya elegida, para que el precio y el envío usen la misma.
     *
     * @param valor valor en la moneda de origen; {@code null} devuelve {@code null}
     * @param tasa pesos por unidad de la moneda de origen
     * @return valor en pesos, redondeado a entero
     */
    public static BigDecimal convertir(BigDecimal valor, TasaCambio tasa) {
        return valor == null ? null : valor.multiply(tasa.valor()).setScale(0, RoundingMode.HALF_UP);
    }

    public String getMonedaDestino() {
        return monedaDestino;
    }
}
