package co.edu.unicesar.marketradar.asistente;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.EnlaceCompra;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import co.edu.unicesar.marketradar.service.ServicioBusqueda;
import co.edu.unicesar.marketradar.service.mvp.PoliticaRecomendacion;
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
    private final PropiedadesMarketradar.Mvp pesos;

    public HerramientasAsistente(ServicioBusquedaFlexible servicioBusqueda, ServicioTasasCambio servicioTasas,
                                 PropiedadesMarketradar propiedades) {
        this.servicioBusqueda = servicioBusqueda;
        this.servicioTasas = servicioTasas;
        this.pesos = propiedades.mvp();
    }

    /**
     * Cómo se elige la recomendación y se calcula el puntaje, con los pesos que de verdad se usan. Es el mismo texto
     * que muestra la página en "¿Cómo se calcula?".
     *
     * @return explicación para el modelo
     */
    public String explicarPuntaje() {
        return "CÓMO RECOMIENDA MARKETRADAR (explíquelo con estas mismas reglas, sin agregar otras):\n- "
                + String.join("\n- ", PoliticaRecomendacion.explicar(pesos));
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
        ResultadoBusqueda.Detalle detalle = resultado.getDetalle();
        if (!detalle.requisitos().isEmpty()) {
            resumen.append("LO QUE PIDIÓ LA PERSONA (cada oferta se revisó contra esto): ")
                    .append(String.join(", ", detalle.requisitos())).append(".\n");
        }
        if (resultado.getOfertas().isEmpty() && detalle.alternativas().isEmpty()) {
            return resumen + "No se encontraron ofertas. " + String.join(" ", resultado.getAdvertencias());
        }
        resultado.getProductoMvp().ifPresent(mvp -> resumen
                .append("MEJOR OPCIÓN (puntaje de conveniencia ").append(mvp.puntaje())
                .append(" de 100 comparada con ").append(mvp.comparadas()).append(" ofertas que cumplen; confianza ")
                .append(mvp.confianza().name().toLowerCase(COLOMBIA)).append("): ").append(describir(mvp.oferta()))
                .append(". Razones: ").append(String.join(" ", mvp.razones()))
                .append(". Enlace: ").append(enlace(mvp.oferta())).append('\n'));
        if (detalle.motivoSinRecomendacion() != null) {
            resumen.append("SIN RECOMENDACIÓN: ").append(detalle.motivoSinRecomendacion())
                    .append(" No recomiende ninguna como si cumpliera.\n");
        }
        if (!resultado.getOfertas().isEmpty()) {
            resumen.append("MÁS BARATAS QUE CUMPLEN:\n").append(resultado.getOfertas().stream()
                    .map(oferta -> "- " + describir(oferta) + ". Enlace: " + enlace(oferta))
                    .collect(Collectors.joining("\n"))).append('\n');
        }
        if (!detalle.alternativas().isEmpty()) {
            resumen.append("ALTERNATIVAS QUE NO CUMPLEN TODO (diga siempre qué les falta):\n")
                    .append(detalle.alternativas().stream().limit(OFERTAS_A_MOSTRAR)
                            .map(a -> "- " + describir(a.oferta()) + ". Le falta: " + String.join("; ", a.motivos())
                                    + ". Enlace: " + enlace(a.oferta()))
                            .collect(Collectors.joining("\n")));
        }
        return resumen.toString().strip();
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
     * @return "[Ver la oferta en exito.com](https://...)", "[Buscar esta oferta en Google](https://...)" o un aviso
     *         si el enlace no es seguro. El texto dice a dónde lleva de verdad.
     */
    static String enlace(OfertaNormalizada oferta) {
        String url = oferta.getUrlDestino();
        if (!EnlaceCompra.esSeguro(url) || url.contains(")") || url.contains(" ")) {
            return "(sin enlace seguro; búsquelo en la página de Buscar)";
        }
        return "[" + oferta.enlace().textoBoton() + "](" + url + ")";
    }

    private static String describir(OfertaNormalizada oferta) {
        String envio = switch (oferta.getEstadoEnvio()) {
            case GRATIS -> " con envío gratis";
            case CONOCIDO -> " con envío incluido";
            case ESTIMADO -> " más un envío aproximado (se confirma en la tienda)";
            case DESCONOCIDO -> " MÁS ENVÍO: la tienda no lo informa, no es el precio total";
        };
        String desde = oferta.ubicacion().texto();
        String ubicacion = desde == null ? "" : oferta.ubicacion().desdeExterior()
                ? "; vende desde " + desde + ", puede tardar y cobrar impuestos que no están en el precio"
                : "; vende desde Colombia";
        String condicion = oferta.condicionProducto().map(c -> "; condición: " + c.texto().toLowerCase(COLOMBIA))
                .orElse("; la tienda no dice si es nuevo");
        String alerta = oferta.precioVerificado() ? "" : "; ALERTA DE PRECIO: " + oferta.getAlertaPrecio();
        return oferta.getTitulo() + " en " + tienda(oferta) + " por " + pesos(oferta.costoConocido()) + envio
                + ubicacion + condicion + alerta;
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
