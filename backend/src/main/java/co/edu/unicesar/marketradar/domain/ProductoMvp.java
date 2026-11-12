package co.edu.unicesar.marketradar.domain;

import java.util.List;
import java.util.Objects;

/**
 * La mejor opción de compra de una búsqueda ("producto MVP"), con su puntaje y las razones
 * en lenguaje sencillo para que la persona entienda por qué se recomienda.
 *
 * <p>El puntaje mide la conveniencia comparada con las demás ofertas que cumplen la búsqueda (no es una
 * probabilidad ni una garantía). La confianza dice qué tan completa es la información con que se calculó.</p>
 *
 * @param oferta oferta recomendada
 * @param puntaje puntaje de conveniencia de 0 a 100
 * @param razones frases cortas que explican la recomendación, con los datos que se usaron
 * @param confianza qué tan completa es la información
 * @param faltantes qué información falta ("el envío", "opiniones de compradores")
 * @param comparadas cuántas ofertas se compararon para dar el puntaje
 */
public record ProductoMvp(OfertaNormalizada oferta, int puntaje, List<String> razones, Confianza confianza,
                          List<String> faltantes, int comparadas) {

    /** Qué tan completa es la información de la recomendación. */
    public enum Confianza {
        /** No falta nada importante. */
        ALTA,
        /** Falta un dato. */
        MEDIA,
        /** Faltan dos datos o más. */
        BAJA
    }

    /**
     * Valida el puntaje y copia las listas.
     */
    public ProductoMvp {
        Objects.requireNonNull(oferta, "oferta");
        if (puntaje < 0 || puntaje > 100) {
            throw new IllegalArgumentException("El puntaje debe estar entre 0 y 100");
        }
        razones = List.copyOf(razones);
        faltantes = List.copyOf(faltantes);
        confianza = confianza == null ? Confianza.BAJA : confianza;
    }

    /**
     * Recomendación sin detalle de confianza, para las pruebas.
     *
     * @param oferta oferta recomendada
     * @param puntaje puntaje de 0 a 100
     * @param razones razones
     */
    public ProductoMvp(OfertaNormalizada oferta, int puntaje, List<String> razones) {
        this(oferta, puntaje, razones, Confianza.ALTA, List.of(), 1);
    }
}
