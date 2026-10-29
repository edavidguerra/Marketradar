package co.edu.unicesar.marketradar.service.mvp;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ProductoMvp;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Elige el producto MVP con un puntaje ponderado de tres criterios, configurables en
 * {@code marketradar.mvp}:
 * <ul>
 *   <li><b>Precio total</b> (precio más envío): la más barata obtiene 1 y las demás, en proporción.</li>
 *   <li><b>Reseñas</b>: promedio bayesiano de las estrellas, para que 5 estrellas con 2 opiniones no
 *       superen a 4,7 estrellas con 1.000 opiniones. Sin reseñas se asume una calificación neutra.</li>
 *   <li><b>Envío</b>: gratis obtiene 1; desconocido, 0,5; con costo, menos según lo que pese en el total.</li>
 * </ul>
 * Los productos usados o reacondicionados pierden un 10 % del puntaje.
 */
@Component
public class SeleccionPonderadaMvp implements EstrategiaSeleccionMvp {

    static final double CALIFICACION_NEUTRA = 3.0;
    static final double PENALIZACION_USADO = 0.9;
    private static final double CALIFICACION_MAXIMA = 5.0;
    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private final PropiedadesMarketradar.Mvp pesos;

    public SeleccionPonderadaMvp(PropiedadesMarketradar propiedades) {
        this.pesos = propiedades.mvp();
    }

    @Override
    public Optional<ProductoMvp> elegir(List<OfertaNormalizada> ofertas) {
        if (ofertas == null || ofertas.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal minimo = ofertas.stream().map(OfertaNormalizada::precioTotal)
                .min(Comparator.naturalOrder()).orElseThrow();
        return ofertas.stream()
                .max(Comparator.comparingDouble((OfertaNormalizada oferta) -> puntaje(oferta, minimo))
                        .thenComparing(OfertaNormalizada::precioTotal, Comparator.reverseOrder()))
                .map(mejor -> new ProductoMvp(mejor, (int) Math.round(puntaje(mejor, minimo) * 100),
                        razones(mejor, minimo)));
    }

    /**
     * Puntaje de 0 a 1 de una oferta.
     *
     * @param oferta oferta evaluada
     * @param minimo precio total más bajo de la búsqueda
     * @return puntaje ponderado
     */
    double puntaje(OfertaNormalizada oferta, BigDecimal minimo) {
        double total = pesos.pesoPrecio() * puntajePrecio(oferta, minimo)
                + pesos.pesoCalificacion() * puntajeResenas(oferta)
                + pesos.pesoEnvio() * puntajeEnvio(oferta);
        return esUsado(oferta) ? total * PENALIZACION_USADO : total;
    }

    private static double puntajePrecio(OfertaNormalizada oferta, BigDecimal minimo) {
        BigDecimal total = oferta.precioTotal();
        if (total.signum() == 0) {
            return 1.0;
        }
        return minimo.divide(total, MathContext.DECIMAL64).doubleValue();
    }

    private double puntajeResenas(OfertaNormalizada oferta) {
        if (!oferta.tieneResenas()) {
            return CALIFICACION_NEUTRA / CALIFICACION_MAXIMA;
        }
        double opiniones = oferta.getTotalResenas();
        double minimas = pesos.resenasMinimas();
        double bayesiana = (opiniones / (opiniones + minimas)) * oferta.getCalificacion().doubleValue()
                + (minimas / (opiniones + minimas)) * CALIFICACION_NEUTRA;
        return bayesiana / CALIFICACION_MAXIMA;
    }

    private static double puntajeEnvio(OfertaNormalizada oferta) {
        BigDecimal envio = oferta.getCostoEnvio();
        if (envio == null) {
            return 0.5;
        }
        if (envio.signum() == 0) {
            return 1.0;
        }
        return 1.0 - envio.divide(oferta.precioTotal(), MathContext.DECIMAL64).doubleValue();
    }

    private static boolean esUsado(OfertaNormalizada oferta) {
        String condicion = oferta.getCondicion() == null ? "" : oferta.getCondicion().toLowerCase(Locale.ROOT);
        return condicion.contains("usad") || condicion.contains("used") || condicion.contains("reacondicionad")
                || condicion.contains("refurbished");
    }

    private static List<String> razones(OfertaNormalizada oferta, BigDecimal minimo) {
        List<String> razones = new ArrayList<>();
        BigDecimal diferencia = oferta.precioTotal().subtract(minimo);
        if (diferencia.signum() == 0) {
            razones.add("Es el precio más bajo de esta búsqueda.");
        } else {
            razones.add("Cuesta " + pesos(diferencia) + " más que la opción más barata, pero en conjunto es "
                    + "mejor compra.");
        }
        if (oferta.tieneResenas()) {
            razones.add("Los compradores le dan " + decimal(oferta.getCalificacion()) + " de 5 estrellas ("
                    + NumberFormat.getIntegerInstance(COLOMBIA).format(oferta.getTotalResenas()) + " opiniones).");
        } else {
            razones.add("La tienda no muestra opiniones de compradores para este producto.");
        }
        BigDecimal envio = oferta.getCostoEnvio();
        if (envio == null) {
            razones.add("El costo del envío se confirma en la tienda.");
        } else if (envio.signum() == 0) {
            razones.add("El envío es gratis.");
        } else {
            razones.add("El precio ya incluye el envío de " + pesos(envio) + ".");
        }
        if (esUsado(oferta)) {
            razones.add("Atención: es un producto usado o reacondicionado.");
        }
        return razones;
    }

    private static String pesos(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getIntegerInstance(COLOMBIA);
        return "$" + formato.format(valor);
    }

    private static String decimal(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
