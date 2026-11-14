package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;

/**
 * Una característica de un producto, ya con nombre y unidad comunes para alinearla con la de otros productos
 * ("256GB", "256 gb" y "256 Gigas" quedan como "256 GB").
 *
 * @param clave nombre interno de la característica, por ejemplo {@code almacenamiento}
 * @param etiqueta nombre para mostrar, por ejemplo "Almacenamiento"
 * @param valor valor para mostrar, por ejemplo "256 GB"
 * @param numero valor numérico en la unidad común (GB, pulgadas, litros, W, kg), o {@code null} si no es un número
 * @param fuente de dónde salió: {@code tienda} (características que informó la tienda) o {@code titulo}
 */
public record Especificacion(String clave, String etiqueta, String valor, BigDecimal numero, String fuente) {

    /** Salió de las características que informó la tienda. */
    public static final String FUENTE_TIENDA = "tienda";
    /** Salió del título de la publicación. */
    public static final String FUENTE_TITULO = "titulo";
}
