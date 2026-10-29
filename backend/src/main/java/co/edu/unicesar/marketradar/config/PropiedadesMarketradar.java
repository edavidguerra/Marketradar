package co.edu.unicesar.marketradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Configuración de Marketradar leída de {@code application.yml} y de variables de entorno.
 * Las credenciales nunca se escriben en el repositorio.
 *
 * @param modoDesarrollo si es {@code true}, los errores incluyen el detalle técnico
 * @param busqueda parámetros de la búsqueda
 * @param cors orígenes permitidos
 * @param moneda conversión de moneda
 * @param marketplaces credenciales de cada marketplace
 * @param asistente configuración del chatbot de IA
 * @param administrador cuenta de administrador que se crea al arrancar
 */
@ConfigurationProperties(prefix = "marketradar")
public record PropiedadesMarketradar(
        boolean modoDesarrollo,
        Busqueda busqueda,
        Cors cors,
        Moneda moneda,
        Marketplaces marketplaces,
        Asistente asistente,
        Administrador administrador) {

    /**
     * @param timeoutSegundos tiempo máximo de espera por marketplace
     * @param cacheMinutos vigencia de los resultados en caché
     * @param tamanoMaximo resultados máximos por página
     * @param hilos tamaño del pool de hilos para consultar en paralelo
     */
    public record Busqueda(int timeoutSegundos, int cacheMinutos, int tamanoMaximo, int hilos) {
    }

    /** @param origenesPermitidos orígenes que pueden llamar a la API desde el navegador */
    public record Cors(List<String> origenesPermitidos) {
    }

    /**
     * @param destino moneda en la que se muestran los precios (COP)
     * @param tasaUsdCop pesos colombianos por dólar
     * @param fechaTasa fecha en que se tomó la tasa
     */
    public record Moneda(String destino, BigDecimal tasaUsdCop, LocalDate fechaTasa) {
    }

    /**
     * @param aliexpress credenciales de AliExpress
     * @param shopify credenciales de Shopify
     * @param mercadolibre credenciales de Mercado Libre
     */
    public record Marketplaces(AliExpress aliexpress, Shopify shopify, MercadoLibre mercadolibre) {
    }

    /**
     * @param habilitado si se consulta en las búsquedas
     * @param appKey App Key del programa de afiliados
     * @param appSecret App Secret
     * @param trackingId Tracking ID de afiliado
     */
    public record AliExpress(boolean habilitado, String appKey, String appSecret, String trackingId) {
    }

    /**
     * @param habilitado si se consulta en las búsquedas
     * @param token token de acceso
     */
    public record Shopify(boolean habilitado, String token) {
    }

    /**
     * @param habilitado si se consulta en las búsquedas
     * @param clientId identificador de la aplicación OAuth
     * @param clientSecret secreto de la aplicación OAuth
     */
    public record MercadoLibre(boolean habilitado, String clientId, String clientSecret) {
    }

    /**
     * @param proveedor proveedor de IA del chatbot ({@code ninguno} mientras no se decida)
     * @param apiKey clave de la API del proveedor
     */
    public record Asistente(String proveedor, String apiKey) {
    }

    /**
     * @param correo correo del administrador inicial; vacío para no crearlo
     * @param clave contraseña inicial, que debe cumplir la política de contraseñas
     * @param nombres nombres
     * @param apellidos apellidos
     */
    public record Administrador(String correo, String clave, String nombres, String apellidos) {

        @Override
        public String toString() {
            return "Administrador[correo=" + correo + "]";
        }
    }
}
