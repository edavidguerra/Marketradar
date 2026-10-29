package co.edu.unicesar.marketradar.domain;

import java.util.Arrays;

/**
 * Criterios de ordenamiento de los resultados de búsqueda.
 */
public enum OrdenResultados {
    PRECIO_ASC("precio_asc"),
    PRECIO_DESC("precio_desc"),
    RELEVANCIA("relevancia");

    private final String valor;

    OrdenResultados(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    /**
     * Convierte el valor recibido en la URL; si viene vacío usa {@link #PRECIO_ASC}.
     *
     * @param valor texto del parámetro {@code orden}
     * @return criterio de ordenamiento
     * @throws IllegalArgumentException si el valor no es reconocido
     */
    public static OrdenResultados desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return PRECIO_ASC;
        }
        return Arrays.stream(values())
                .filter(orden -> orden.valor.equalsIgnoreCase(valor.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Orden no reconocido: " + valor));
    }
}
