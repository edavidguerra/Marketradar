package co.edu.unicesar.marketradar.asistente;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import co.edu.unicesar.marketradar.service.ServicioBusqueda;
import co.edu.unicesar.marketradar.service.ServicioBusquedaFlexible;
import co.edu.unicesar.marketradar.service.ServicioTasasCambio;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Funciones que Claude puede llamar para responder con datos reales de Marketradar,
 * en lugar de inventar precios. {@link ProveedorIAClaude} las declara como herramientas.
 */
@Component
public class HerramientasAsistente {

    private static final int OFERTAS_A_MOSTRAR = 3;
    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private final ServicioBusquedaFlexible servicioBusqueda;
    private final ServicioTasasCambio servicioTasas;

    public HerramientasAsistente(ServicioBusquedaFlexible servicioBusqueda, ServicioTasasCambio servicioTasas) {
        this.servicioBusqueda = servicioBusqueda;
        this.servicioTasas = servicioTasas;
    }

    /**
     * Busca un producto en las tiendas (entendiendo errores al escribir) y resume la mejor opción y las más
     * baratas. Cada oferta trae su enlace ya escrito como [Ver en tienda](https://...), que es la forma en que la
     * página lo convierte en un botón que la persona puede presionar.
     *
     * @param producto nombre del producto
     * @return resumen en texto para el modelo
     */
    public String buscarMejorOpcion(String producto) {
        ConsultaBusqueda consulta;
        try {
            consulta = ConsultaBusqueda.crear(producto, "precio_asc", Set.of(), 1, OFERTAS_A_MOSTRAR);
        } catch (ValidacionException e) {
            return e.getMessage();
        }
        ServicioBusquedaFlexible.Resultado flexible = servicioBusqueda.buscar(consulta, null, false);
        ResultadoBusqueda resultado = flexible.resultado();
        StringBuilder resumen = new StringBuilder();
        if (flexible.terminoOriginal() != null) {
            resumen.append("SE BUSCÓ: \"").append(flexible.consulta().getTermino())
                    .append("\" (la persona escribió \"").append(flexible.terminoOriginal()).append("\").\n");
        }
        if (resultado.getOfertas().isEmpty()) {
            return resumen + "No se encontraron ofertas. " + String.join(" ", resultado.getAdvertencias());
        }
        resultado.getProductoMvp().ifPresent(mvp -> resumen
                .append("MEJOR OPCIÓN: ").append(describir(mvp.oferta()))
                .append(". Razones: ").append(String.join(" ", mvp.razones()))
                .append(". Enlace: ").append(enlace(mvp.oferta())).append('\n'));
        resumen.append("MÁS BARATAS:\n").append(resultado.getOfertas().stream()
                .map(oferta -> "- " + describir(oferta) + ". Enlace: " + enlace(oferta))
                .collect(Collectors.joining("\n")));
        return resumen.toString();
    }

    /**
     * @return valor del dólar usado para convertir precios
     */
    public String valorDelDolar() {
        return servicioTasas.tasaVigente("USD")
                .map(tasa -> "1 dólar = " + pesos(tasa.valor()) + " (" + tasa.fuente() + ", " + tasa.vigenteDesde() + ")")
                .orElse("No hay una tasa del dólar disponible en este momento.");
    }

    /**
     * Enlace en el formato que la página convierte en botón. Solo se ofrecen enlaces https.
     *
     * @param oferta oferta con su enlace de compra
     * @return "[Ver en Éxito](https://...)" o un aviso si el enlace no es seguro
     */
    static String enlace(OfertaNormalizada oferta) {
        String url = oferta.getUrlDestino();
        if (url == null || !url.startsWith("https://") || url.contains(")") || url.contains(" ")) {
            return "(sin enlace seguro; búsquelo en la página de Buscar)";
        }
        String tienda = oferta.getVendedor() != null && !oferta.getVendedor().isBlank()
                ? oferta.getVendedor().replaceAll("[\\[\\]()]", "").strip()
                : ServicioBusqueda.nombreVisible(oferta.getCodigoMarketplace());
        return "[Ver en " + tienda + "](" + url + ")";
    }

    private static String describir(OfertaNormalizada oferta) {
        String envio = oferta.getCostoEnvio() == null ? " (el envío se confirma en la tienda)"
                : oferta.getCostoEnvio().signum() == 0 ? " con envío gratis" : " con envío incluido";
        String desde = oferta.ubicacion().texto();
        String ubicacion = desde == null ? "" : oferta.ubicacion().desdeExterior()
                ? "; vende desde " + desde + ", puede tardar y cobrar impuestos" : "; vende desde Colombia";
        return oferta.getTitulo() + " en " + tienda(oferta) + " por " + pesos(oferta.precioTotal()) + envio
                + ubicacion;
    }

    /** En Google Shopping la tienda real va en el vendedor, por ejemplo "Éxito". */
    private static String tienda(OfertaNormalizada oferta) {
        if ("googleshopping".equals(oferta.getCodigoMarketplace()) && oferta.getVendedor() != null) {
            return oferta.getVendedor();
        }
        return ServicioBusqueda.nombreVisible(oferta.getCodigoMarketplace());
    }

    private static String pesos(BigDecimal valor) {
        return "$" + NumberFormat.getIntegerInstance(COLOMBIA).format(valor);
    }
}
