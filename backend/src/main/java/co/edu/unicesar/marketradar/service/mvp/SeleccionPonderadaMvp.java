package co.edu.unicesar.marketradar.service.mvp;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.CondicionProducto;
import co.edu.unicesar.marketradar.domain.EstadoEnvio;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ProductoMvp;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import co.edu.unicesar.marketradar.domain.UbicacionVendedor;
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
 * Elige la mejor opción de compra con un puntaje de conveniencia de tres criterios, con pesos configurables en
 * {@code marketradar.mvp}. La explicación para la persona está en {@link PoliticaRecomendacion}, que usa los mismos
 * pesos: la página, el tutorial y el asistente dicen lo mismo.
 *
 * <p>Regla general: <b>lo que no se sabe no suma</b>. Un envío desconocido no da puntos, no tener opiniones cuenta
 * como una calificación de 3 de 5 (por debajo de lo normal en las tiendas) y una condición sin confirmar cuenta como
 * no nuevo. Así ningún dato que falta hace subir el puntaje.</p>
 *
 * <p>El servicio de búsqueda solo le pasa ofertas que cumplen lo que pidió la persona y cuyo precio no tiene
 * alertas; si no queda ninguna, no hay recomendación.</p>
 */
@Component
public class SeleccionPonderadaMvp implements EstrategiaSeleccionMvp {

    /** Calificación que se le supone a un producto sin opiniones: por debajo del promedio habitual (cerca de 4). */
    static final double CALIFICACION_NEUTRA = 3.0;
    /** Un producto que no es nuevo confirmado pierde el 10 % del puntaje. */
    static final double PENALIZACION_NO_NUEVO = 0.9;
    /**
     * Desde el exterior pueden cobrar impuestos de importación que no se conocen y no se suman al precio: el puntaje
     * baja un 10 %. No se inventa cuánto serían; solo se evita que una oferta gane por un costo que no se ve.
     */
    static final double PENALIZACION_EXTERIOR = 0.9;
    private static final double CALIFICACION_MAXIMA = 5.0;
    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private final PropiedadesMarketradar.Mvp pesos;

    public SeleccionPonderadaMvp(PropiedadesMarketradar propiedades) {
        this.pesos = propiedades.mvp();
    }

    /** @return pesos con que se calcula el puntaje */
    public PropiedadesMarketradar.Mvp pesos() {
        return pesos;
    }

    @Override
    public Optional<ProductoMvp> elegir(List<OfertaNormalizada> ofertas) {
        List<OfertaNormalizada> candidatas = ofertas == null ? List.of()
                : ofertas.stream().filter(OfertaNormalizada::precioVerificado).toList();
        if (candidatas.isEmpty()) {
            return Optional.empty();
        }
        Comparacion comparacion = new Comparacion(candidatas);
        OfertaNormalizada mejor = candidatas.stream()
                .max(Comparator.comparingDouble((OfertaNormalizada oferta) -> puntaje(oferta, comparacion))
                        .thenComparing(comparacion::costoComparable, Comparator.reverseOrder()))
                .orElseThrow();
        List<String> faltantes = faltantes(mejor);
        ProductoMvp.Confianza confianza = faltantes.isEmpty() ? ProductoMvp.Confianza.ALTA
                : faltantes.size() == 1 ? ProductoMvp.Confianza.MEDIA : ProductoMvp.Confianza.BAJA;
        return Optional.of(new ProductoMvp(mejor, (int) Math.round(puntaje(mejor, comparacion) * 100),
                razones(mejor, comparacion), confianza, faltantes, candidatas.size()));
    }

    /**
     * Puntaje de 0 a 1 de una oferta, comparada con las demás.
     *
     * @param oferta oferta evaluada
     * @param comparacion ofertas con que se compara
     * @return puntaje ponderado
     */
    double puntaje(OfertaNormalizada oferta, Comparacion comparacion) {
        double total = pesos.pesoPrecio() * puntajePrecio(oferta, comparacion)
                + pesos.pesoCalificacion() * puntajeResenas(oferta)
                + pesos.pesoEnvio() * puntajeEnvio(oferta);
        if (!nuevoConfirmado(oferta)) {
            total *= PENALIZACION_NO_NUEVO;
        }
        return oferta.ubicacion().desdeExterior() ? total * PENALIZACION_EXTERIOR : total;
    }

    private static double puntajePrecio(OfertaNormalizada oferta, Comparacion comparacion) {
        BigDecimal costo = comparacion.costoComparable(oferta);
        if (costo.signum() == 0) {
            return 1.0;
        }
        return comparacion.minimo().divide(costo, MathContext.DECIMAL64).doubleValue();
    }

    double puntajeResenas(OfertaNormalizada oferta) {
        if (!oferta.tieneResenas()) {
            return CALIFICACION_NEUTRA / CALIFICACION_MAXIMA;
        }
        double opiniones = oferta.getTotalResenas();
        double minimas = pesos.resenasMinimas();
        double bayesiana = (opiniones / (opiniones + minimas)) * oferta.getCalificacion().doubleValue()
                + (minimas / (opiniones + minimas)) * CALIFICACION_NEUTRA;
        return bayesiana / CALIFICACION_MAXIMA;
    }

    /** Gratis suma todo; con costo, menos según lo que pese en el total; desconocido, aproximado o del exterior, 0. */
    static double puntajeEnvio(OfertaNormalizada oferta) {
        if (!oferta.getEstadoEnvio().confirmado() || oferta.ubicacion().desdeExterior()) {
            return 0.0;
        }
        BigDecimal envio = oferta.getCostoEnvio();
        if (envio.signum() == 0) {
            return 1.0;
        }
        return 1.0 - envio.divide(oferta.costoConocido(), MathContext.DECIMAL64).doubleValue();
    }

    private static boolean nuevoConfirmado(OfertaNormalizada oferta) {
        return oferta.condicionProducto().filter(c -> c == CondicionProducto.NUEVO).isPresent();
    }

    /** Lo que faltó para dar la recomendación con toda la información. */
    private List<String> faltantes(OfertaNormalizada oferta) {
        List<String> faltan = new ArrayList<>();
        if (!oferta.getEstadoEnvio().confirmado()) {
            faltan.add("el costo del envío");
        }
        UbicacionVendedor ubicacion = oferta.ubicacion();
        if (ubicacion.desdeExterior()) {
            faltan.add("los impuestos de importación");
        } else if (ubicacion.alcance() == UbicacionVendedor.Alcance.DESCONOCIDA) {
            faltan.add("desde dónde vende la tienda");
        }
        if (!oferta.tieneResenas()) {
            faltan.add("opiniones de compradores");
        } else if (oferta.getTotalResenas() < pesos.resenasMinimas()) {
            faltan.add("más opiniones de compradores");
        }
        if (oferta.condicionProducto().isEmpty()) {
            faltan.add("la condición del producto (si es nuevo)");
        }
        return faltan;
    }

    private List<String> razones(OfertaNormalizada oferta, Comparacion comparacion) {
        List<String> razones = new ArrayList<>();
        int cuantas = comparacion.candidatas().size();
        OfertaNormalizada masBarata = comparacion.masBarata();
        BigDecimal diferencia = comparacion.costoComparable(oferta).subtract(comparacion.minimo());
        Optional<String> falta = oferta.faltaParaElTotal();
        if (cuantas == 1) {
            razones.add("Es la única oferta que cumple todo lo que usted pidió.");
        } else if (diferencia.signum() <= 0) {
            razones.add(falta.isEmpty()
                    ? "Tiene el precio total más bajo de las " + cuantas + " ofertas que cumplen su búsqueda."
                    : "Tiene el precio más bajo de las " + cuantas + " ofertas que cumplen su búsqueda, aunque "
                    + falta.get() + ".");
        } else {
            BigDecimal visible = oferta.costoConocido().subtract(masBarata.costoConocido());
            razones.add((visible.signum() > 0 ? "Cuesta " + pesos(visible) + " más que la oferta más barata que cumple"
                    : "Cuesta parecido a la oferta más barata que cumple") + ", pero " + ventajas(oferta, masBarata)
                    + ".");
        }
        if (oferta.tieneResenas()) {
            String deQuien = "googleshopping".equals(oferta.getCodigoMarketplace())
                    ? " opiniones del producto reunidas por Google (no son opiniones sobre esta tienda)"
                    : " opiniones de compradores";
            razones.add("Tiene " + decimal(oferta.getCalificacion()) + " de 5 estrellas en "
                    + NumberFormat.getIntegerInstance(COLOMBIA).format(oferta.getTotalResenas()) + deQuien + "."
                    + (oferta.getTotalResenas() < pesos.resenasMinimas()
                    ? " Son pocas, así que pesan poco en el puntaje." : ""));
        } else {
            razones.add("No tiene opiniones de compradores: cuenta como 3 de 5 estrellas, sin ventaja.");
        }
        razones.add(switch (oferta.getEstadoEnvio()) {
            case GRATIS -> "El envío es gratis.";
            case CONOCIDO -> "El precio incluye el envío de " + pesos(oferta.getCostoEnvio()) + ".";
            case ESTIMADO -> "El envío es aproximado (" + pesos(oferta.getCostoEnvio())
                    + " o más) y se confirma en la tienda, así que no suma puntos.";
            case DESCONOCIDO -> "La tienda no informa el envío: se confirma allá y no suma puntos.";
        });
        UbicacionVendedor ubicacion = oferta.ubicacion();
        if (ubicacion.alcance() == UbicacionVendedor.Alcance.COLOMBIA) {
            razones.add("La tienda vende desde Colombia.");
        } else if (ubicacion.desdeExterior()) {
            razones.add("Atención: la tienda vende desde " + ubicacion.texto()
                    + ". Puede tardar más en llegar y cobrar impuestos de importación que no están en el precio.");
        }
        Optional<CondicionProducto> condicion = oferta.condicionProducto();
        if (condicion.isEmpty()) {
            razones.add("La tienda no dice si es nuevo; revíselo antes de comprar.");
        } else if (condicion.get() == CondicionProducto.NUEVO) {
            razones.add("La tienda dice que es nuevo.");
        } else {
            razones.add("Atención: es un producto " + condicion.get().texto().toLowerCase(COLOMBIA) + ".");
        }
        TasaCambio tasa = oferta.getTasaAplicada();
        if (tasa != null) {
            razones.add("El precio venía en " + oferta.getMonedaOriginal() + " y se convirtió con 1 "
                    + oferta.getMonedaOriginal() + " = " + pesos(tasa.valor()) + " (" + tasa.fuente() + ", "
                    + tasa.vigenteDesde() + ").");
        }
        return razones;
    }

    /** Por qué una oferta más cara queda por encima de la más barata, con los datos que se compararon. */
    private String ventajas(OfertaNormalizada oferta, OfertaNormalizada masBarata) {
        List<String> ventajas = new ArrayList<>();
        if (puntajeResenas(oferta) > puntajeResenas(masBarata) + 0.001) {
            ventajas.add("tiene mejores opiniones");
        }
        if (puntajeEnvio(oferta) > puntajeEnvio(masBarata) + 0.001) {
            ventajas.add(oferta.getEstadoEnvio() == EstadoEnvio.GRATIS ? "el envío es gratis"
                    : "el envío está confirmado");
        }
        if (nuevoConfirmado(oferta) && !nuevoConfirmado(masBarata)) {
            ventajas.add("la tienda confirma que es nuevo");
        }
        if (!masBarata.getEstadoEnvio().confirmado() && oferta.getEstadoEnvio().confirmado()) {
            ventajas.add("la más barata no informa el envío");
        }
        return ventajas.isEmpty() ? "sumando precio, opiniones y envío queda mejor" : String.join(" y ", ventajas);
    }

    private static String pesos(BigDecimal valor) {
        return "$" + NumberFormat.getIntegerInstance(COLOMBIA).format(valor);
    }

    private static String decimal(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /**
     * Las ofertas que se comparan. Para comparar precios, a una oferta sin envío confirmado se le suma el envío más
     * caro que informan las demás (si ninguna lo informa, no se suma nada): así no queda más barata solo porque no
     * se sabe cuánto cobra. Ese valor solo se usa para comparar; nunca se muestra como su precio.
     *
     * @param candidatas ofertas que cumplen, con precio verificado
     */
    record Comparacion(List<OfertaNormalizada> candidatas) {

        BigDecimal envioMasCaro() {
            return candidatas.stream().filter(o -> o.getEstadoEnvio() != EstadoEnvio.DESCONOCIDO)
                    .map(OfertaNormalizada::getCostoEnvio).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        }

        BigDecimal costoComparable(OfertaNormalizada oferta) {
            if (oferta.getEstadoEnvio().confirmado()) {
                return oferta.costoConocido();
            }
            BigDecimal propio = oferta.getCostoEnvio() == null ? BigDecimal.ZERO : oferta.getCostoEnvio();
            return oferta.getPrecio().add(propio.max(envioMasCaro()));
        }

        BigDecimal minimo() {
            return candidatas.stream().map(this::costoComparable).min(Comparator.naturalOrder()).orElseThrow();
        }

        OfertaNormalizada masBarata() {
            return candidatas.stream().min(Comparator.comparing(this::costoComparable)).orElseThrow();
        }
    }
}
