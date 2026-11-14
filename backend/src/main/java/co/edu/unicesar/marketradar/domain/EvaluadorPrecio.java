package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Decide qué pasó con el precio de una oferta seguida y si hay que avisar. Es la única regla de los avisos, para que
 * nunca se tome como descuento algo que no lo es:
 * <ul>
 *   <li>Si la tienda falló o la publicación no apareció, no se compara nada.</li>
 *   <li>Si el precio llegó con una alerta ("desde", por cuotas, distinto al de la tienda), no se usa.</li>
 *   <li>Si cambió el vendedor, la condición, la moneda o la variante, el precio nuevo es de otra oferta: no se avisa ni
 *       se cambia la referencia.</li>
 *   <li>Si el precio se convirtió de otra moneda, se compara en la moneda de la tienda: un cambio de la TRM no es un
 *       descuento.</li>
 *   <li>Una bajada cuenta si es de al menos el porcentaje configurado.</li>
 * </ul>
 */
public final class EvaluadorPrecio {

    private EvaluadorPrecio() {
    }

    /**
     * Un aviso que hay que mandar.
     *
     * @param tipo bajada o precio objetivo
     * @param precioAnterior precio de referencia, en la moneda de la oferta
     * @param precioActual precio nuevo
     * @param moneda moneda de los dos precios
     */
    public record Evento(Seguimiento.TipoAviso tipo, BigDecimal precioAnterior, BigDecimal precioActual, String moneda) {
    }

    /**
     * Resultado de una comprobación.
     *
     * @param resultado qué pasó
     * @param actualizarReferencia si la oferta encontrada pasa a ser la nueva referencia
     * @param objetivoAvisado si ya se avisó que se alcanzó el precio objetivo
     * @param evento aviso que hay que mandar, si hay
     */
    public record Decision(Seguimiento.Resultado resultado, boolean actualizarReferencia, boolean objetivoAvisado,
                           Optional<Evento> evento) {
    }

    /**
     * Compara la oferta encontrada con la referencia del seguimiento.
     *
     * @param seguimiento seguimiento con su referencia
     * @param encontrada oferta tal como la devolvió la tienda ahora, o vacío si no apareció
     * @param varianteActual variante de la oferta encontrada ({@link NormalizadorEspecificaciones#claveVariante})
     * @param bajaMinimaPorcentaje cuánto debe bajar, como mínimo, para avisar (por ejemplo 1 = 1 %)
     * @return decisión
     */
    public static Decision evaluar(Seguimiento seguimiento, Optional<OfertaNormalizada> encontrada,
                                   String varianteActual, BigDecimal bajaMinimaPorcentaje) {
        boolean avisado = seguimiento.isObjetivoAvisado();
        if (encontrada.isEmpty()) {
            return new Decision(Seguimiento.Resultado.NO_ENCONTRADA, false, avisado, Optional.empty());
        }
        OfertaNormalizada oferta = encontrada.get();
        if (!oferta.precioVerificado() || oferta.getPrecio().signum() <= 0) {
            return new Decision(Seguimiento.Resultado.PRECIO_DUDOSO, false, avisado, Optional.empty());
        }
        if (seguimiento.getPrecioReferencia() == null) {
            return new Decision(Seguimiento.Resultado.REFERENCIA, true, avisado, Optional.empty());
        }
        if (!mismaOferta(seguimiento, oferta, varianteActual)) {
            return new Decision(Seguimiento.Resultado.VARIANTE_DISTINTA, false, avisado, Optional.empty());
        }
        // Si el precio venía en dólares, se compara en dólares. Sin precio original de referencia todavía, se guarda.
        BigDecimal referencia;
        BigDecimal actual;
        if (oferta.getMonedaOriginal() != null) {
            if (seguimiento.getPrecioOriginalReferencia() == null
                    || !oferta.getMonedaOriginal().equals(seguimiento.getMonedaOriginalReferencia())) {
                return new Decision(Seguimiento.Resultado.REFERENCIA, true, avisado, Optional.empty());
            }
            referencia = seguimiento.getPrecioOriginalReferencia();
            actual = oferta.getPrecioOriginal();
        } else {
            referencia = seguimiento.getPrecioReferencia();
            actual = oferta.getPrecio();
        }
        BigDecimal umbral = referencia.multiply(BigDecimal.ONE.subtract(
                bajaMinimaPorcentaje.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)));
        boolean bajo = actual.compareTo(referencia) < 0 && actual.compareTo(umbral) <= 0;
        Seguimiento.Resultado resultado = actual.compareTo(referencia) > 0 ? Seguimiento.Resultado.SUBIO
                : bajo ? Seguimiento.Resultado.BAJO : Seguimiento.Resultado.SIN_CAMBIO;

        if (seguimiento.getTipoAviso() == Seguimiento.TipoAviso.PRECIO_OBJETIVO) {
            BigDecimal objetivo = seguimiento.getPrecioObjetivo();
            if (objetivo != null && oferta.getPrecio().compareTo(objetivo) <= 0) {
                if (avisado) {
                    return new Decision(resultado, true, true, Optional.empty());
                }
                return new Decision(Seguimiento.Resultado.OBJETIVO_ALCANZADO, true, true, Optional.of(new Evento(
                        Seguimiento.TipoAviso.PRECIO_OBJETIVO, seguimiento.getPrecioReferencia(), oferta.getPrecio(),
                        oferta.getMoneda())));
            }
            // Volvió a quedar por encima del objetivo: si después baja otra vez, se vuelve a avisar.
            return new Decision(resultado, true, false, Optional.empty());
        }
        if (bajo) {
            return new Decision(Seguimiento.Resultado.BAJO, true, avisado, Optional.of(new Evento(
                    Seguimiento.TipoAviso.BAJA_PRECIO, seguimiento.getPrecioReferencia(), oferta.getPrecio(),
                    oferta.getMoneda())));
        }
        return new Decision(resultado, true, avisado, Optional.empty());
    }

    /** Mismo vendedor, misma condición, misma moneda y nada que diga que cambió la variante. */
    static boolean mismaOferta(Seguimiento seguimiento, OfertaNormalizada oferta, String varianteActual) {
        if (seguimiento.getMonedaReferencia() != null && !seguimiento.getMonedaReferencia().equals(oferta.getMoneda())) {
            return false;
        }
        if (!Objects.equals(normal(seguimiento.getVendedorReferencia()), normal(oferta.getVendedor()))) {
            return false;
        }
        if (!Objects.equals(normal(seguimiento.getCondicionReferencia()), normal(oferta.getCondicion()))) {
            return false;
        }
        return NormalizadorEspecificaciones.mismaVariante(seguimiento.getVarianteReferencia(), varianteActual);
    }

    private static String normal(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip().toLowerCase(Locale.ROOT);
    }
}
