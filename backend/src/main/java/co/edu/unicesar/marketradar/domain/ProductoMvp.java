package co.edu.unicesar.marketradar.domain;

import java.util.List;
import java.util.Objects;

/**
 * La mejor opción de compra de una búsqueda ("producto MVP"), con su puntaje y las razones
 * en lenguaje sencillo para que la persona entienda por qué se recomienda.
 *
 * @param oferta oferta recomendada; su enlace lleva directo al producto en la tienda
 * @param puntaje puntaje de 0 a 100
 * @param razones frases cortas que explican la recomendación
 */
public record ProductoMvp(OfertaNormalizada oferta, int puntaje, List<String> razones) {

    /**
     * Valida el puntaje y copia las razones.
     */
    public ProductoMvp {
        Objects.requireNonNull(oferta, "oferta");
        if (puntaje < 0 || puntaje > 100) {
            throw new IllegalArgumentException("El puntaje debe estar entre 0 y 100");
        }
        razones = List.copyOf(razones);
    }
}
