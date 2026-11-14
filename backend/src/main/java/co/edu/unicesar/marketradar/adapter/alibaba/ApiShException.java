package co.edu.unicesar.marketradar.adapter.alibaba;

/**
 * API.SH respondió con un error o no respondió. El mensaje nunca trae la clave.
 */
public class ApiShException extends RuntimeException {

    /** Qué pasó, según el código HTTP que documenta API.SH. */
    public enum Tipo {
        /** 400: un parámetro no es válido. */
        PARAMETRO_INVALIDO,
        /** 401 o 403: la clave falta, no es válida o no puede usar este endpoint. */
        CLAVE_INVALIDA,
        /** 402: no hay créditos o se alcanzó el límite de gasto. */
        SIN_CREDITOS,
        /** 404: el producto no existe o ya no es público. */
        NO_ENCONTRADO,
        /** 429: demasiadas consultas; se reintenta después de {@code retry-after}. */
        LIMITE,
        /** 502 o 503, o sin respuesta: Alibaba o API.SH no están disponibles ahora. */
        NO_DISPONIBLE,
        /** La respuesta no tiene el formato esperado. */
        RESPUESTA_INVALIDA
    }

    private final Tipo tipo;
    private final Long reintentarEnSegundos;

    /**
     * @param tipo qué pasó
     * @param mensaje detalle técnico, sin la clave
     * @param reintentarEnSegundos valor de {@code retry-after}, o {@code null}
     */
    public ApiShException(Tipo tipo, String mensaje, Long reintentarEnSegundos) {
        super(mensaje);
        this.tipo = tipo;
        this.reintentarEnSegundos = reintentarEnSegundos;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public Long getReintentarEnSegundos() {
        return reintentarEnSegundos;
    }
}
