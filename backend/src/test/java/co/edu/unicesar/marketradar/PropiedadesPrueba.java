package co.edu.unicesar.marketradar;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Configuración de ejemplo para las pruebas unitarias.
 */
public final class PropiedadesPrueba {

    private PropiedadesPrueba() {
    }

    public static PropiedadesMarketradar crear() {
        return new PropiedadesMarketradar(
                true,
                new PropiedadesMarketradar.Busqueda(1, 15, 20, 4),
                new PropiedadesMarketradar.Cors(List.of("http://localhost:8080")),
                new PropiedadesMarketradar.Moneda("COP", new BigDecimal("4000"), LocalDate.of(2026, 10, 28)),
                new PropiedadesMarketradar.Marketplaces(
                        new PropiedadesMarketradar.AliExpress(false, "", "", ""),
                        new PropiedadesMarketradar.Shopify(false, ""),
                        new PropiedadesMarketradar.MercadoLibre(false, "", "")),
                new PropiedadesMarketradar.Asistente("ninguno", ""));
    }
}
