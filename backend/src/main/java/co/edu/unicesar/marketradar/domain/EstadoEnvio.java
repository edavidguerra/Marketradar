package co.edu.unicesar.marketradar.domain;

/**
 * Lo que se sabe del envío de una oferta. Solo {@link #GRATIS} y {@link #CONOCIDO} permiten hablar de un precio total
 * confirmado; un envío estimado o desconocido nunca se cuenta como cero.
 */
public enum EstadoEnvio {

    /** La tienda dice que el envío es gratis, sin condiciones (o la condición ya se cumple). */
    GRATIS("gratis"),
    /** La tienda dice cuánto cuesta el envío. */
    CONOCIDO("conocido"),
    /** La tienda da un valor aproximado o mínimo ("desde $ 9.900"): el envío puede costar más. */
    ESTIMADO("estimado"),
    /** La tienda no dice nada del envío, o lo dice sin un valor claro. */
    DESCONOCIDO("desconocido");

    private final String codigo;

    EstadoEnvio(String codigo) {
        this.codigo = codigo;
    }

    /** @return código para el frontend, por ejemplo {@code desconocido} */
    public String codigo() {
        return codigo;
    }

    /** @return {@code true} si el costo del envío está confirmado (gratis o con valor) */
    public boolean confirmado() {
        return this == GRATIS || this == CONOCIDO;
    }
}
