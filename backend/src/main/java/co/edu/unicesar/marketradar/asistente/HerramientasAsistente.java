package co.edu.unicesar.marketradar.asistente;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import co.edu.unicesar.marketradar.service.ServicioBusqueda;
import co.edu.unicesar.marketradar.service.ServicioTasasCambio;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Funciones que el modelo de IA puede llamar para responder con datos reales de Marketradar,
 * en lugar de inventar precios (tool calling de Spring AI).
 */
@Component
public class HerramientasAsistente {

    private static final int OFERTAS_A_MOSTRAR = 3;
    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private final ServicioBusqueda servicioBusqueda;
    private final ServicioTasasCambio servicioTasas;

    public HerramientasAsistente(ServicioBusqueda servicioBusqueda, ServicioTasasCambio servicioTasas) {
        this.servicioBusqueda = servicioBusqueda;
        this.servicioTasas = servicioTasas;
    }

    /**
     * Busca un producto en las tiendas y resume la mejor opción y las más baratas.
     *
     * @param producto nombre del producto
     * @return resumen en texto para el modelo
     */
    @Tool(description = "Busca un producto en las tiendas conectadas a Marketradar y devuelve la mejor opción de "
            + "compra (producto MVP) con su enlace directo, y las ofertas más baratas en pesos colombianos.")
    public String buscarMejorOpcion(@ToolParam(description = "Nombre del producto, por ejemplo 'tensiómetro de brazo'")
                                    String producto) {
        ConsultaBusqueda consulta;
        try {
            consulta = ConsultaBusqueda.crear(producto, "precio_asc", Set.of(), 1, OFERTAS_A_MOSTRAR);
        } catch (ValidacionException e) {
            return e.getMessage();
        }
        ResultadoBusqueda resultado = servicioBusqueda.buscar(consulta);
        if (resultado.getOfertas().isEmpty()) {
            return "No se encontraron ofertas. " + String.join(" ", resultado.getAdvertencias());
        }
        StringBuilder resumen = new StringBuilder();
        resultado.getProductoMvp().ifPresent(mvp -> resumen
                .append("MEJOR OPCIÓN: ").append(describir(mvp.oferta()))
                .append(". Razones: ").append(String.join(" ", mvp.razones()))
                .append(" Enlace directo: ").append(mvp.oferta().getUrlDestino()).append('\n'));
        resumen.append("MÁS BARATAS:\n").append(resultado.getOfertas().stream()
                .map(oferta -> "- " + describir(oferta) + " | " + oferta.getUrlDestino())
                .collect(Collectors.joining("\n")));
        return resumen.toString();
    }

    /**
     * @return valor del dólar usado para convertir precios
     */
    @Tool(description = "Devuelve cuántos pesos colombianos vale hoy un dólar (TRM) según Marketradar.")
    public String valorDelDolar() {
        return servicioTasas.tasaVigente("USD")
                .map(tasa -> "1 dólar = " + pesos(tasa.valor()) + " (" + tasa.fuente() + ", " + tasa.vigenteDesde() + ")")
                .orElse("No hay una tasa del dólar disponible en este momento.");
    }

    private static String describir(OfertaNormalizada oferta) {
        return oferta.getTitulo() + " en " + oferta.getCodigoMarketplace() + " por " + pesos(oferta.precioTotal())
                + " con envío";
    }

    private static String pesos(BigDecimal valor) {
        return "$" + NumberFormat.getIntegerInstance(COLOMBIA).format(valor);
    }
}
