package co.edu.unicesar.marketradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de las compras al por mayor con Alibaba a través de API.SH. La clave solo vive en el servidor
 * ({@code APISH_KEY}); nunca se manda al navegador ni se escribe en los registros.
 *
 * @param apiKey clave de API.SH; vacía apaga el apartado
 * @param urlApi dirección base, por defecto {@code https://api.api.sh}
 * @param cacheHoras cuántas horas se reutiliza una respuesta (API.SH cobra igual las repetidas)
 * @param paginasMaximas cuántas páginas de 48 resultados se pueden pedir en una búsqueda
 * @param detallesPorBusqueda cuántos detalles se consultan, como máximo, para aclarar precios dudosos
 * @param consultasPorDia tope diario de consultas a API.SH (cada una cuesta un crédito)
 * @param timeoutSegundos cuánto se espera la respuesta (API.SH recomienda paciencia: Alibaba puede tardar)
 */
@ConfigurationProperties("marketradar.mayoristas")
public record PropiedadesMayoristas(String apiKey, String urlApi, Integer cacheHoras, Integer paginasMaximas,
                                    Integer detallesPorBusqueda, Integer consultasPorDia, Integer timeoutSegundos) {

    /** Completa los valores por defecto. */
    public PropiedadesMayoristas {
        apiKey = apiKey == null ? "" : apiKey.strip();
        urlApi = urlApi == null || urlApi.isBlank() ? "https://api.api.sh" : urlApi.strip().replaceAll("/+$", "");
        cacheHoras = cacheHoras == null || cacheHoras < 0 ? 6 : Math.min(cacheHoras, 24);
        paginasMaximas = paginasMaximas == null || paginasMaximas < 1 ? 3 : Math.min(paginasMaximas, 10);
        detallesPorBusqueda = detallesPorBusqueda == null || detallesPorBusqueda < 0 ? 4 : Math.min(detallesPorBusqueda, 10);
        consultasPorDia = consultasPorDia == null || consultasPorDia < 0 ? 200 : consultasPorDia;
        timeoutSegundos = timeoutSegundos == null || timeoutSegundos < 5 ? 60 : Math.min(timeoutSegundos, 180);
    }

    /** @return {@code true} si hay clave */
    public boolean configurado() {
        return !apiKey.isEmpty();
    }

    /** No muestra la clave. */
    @Override
    public String toString() {
        return "PropiedadesMayoristas[urlApi=" + urlApi + ", apiKey=" + (configurado() ? "****" : "(vacía)")
                + ", cacheHoras=" + cacheHoras + ", paginasMaximas=" + paginasMaximas + "]";
    }
}
