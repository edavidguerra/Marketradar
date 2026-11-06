package co.edu.unicesar.marketradar.adapter.demostracion;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplaceBase;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tienda de demostración: responde con productos de ejemplo guardados en
 * {@code demostracion/catalogo.json}, sin llamar a ninguna API.
 *
 * <p>Sirve para mostrar y probar Marketradar mientras las tiendas reales no tienen claves. Se prende con
 * {@code DEMOSTRACION_HABILITADO=true}. Los enlaces llevan a {@code tienda-demostracion.html}, que le
 * explica a la persona que el producto es de ejemplo y no se vende.</p>
 */
@Component
public class AdaptadorDemostracion extends AdaptadorMarketplaceBase {

    public static final String CODIGO = "demostracion";

    static final int LIMITE_RESULTADOS = 20;

    private static final Set<String> PALABRAS_VACIAS = Set.of(
            "de", "del", "la", "las", "el", "los", "un", "una", "unos", "unas", "para", "con", "sin", "y", "o",
            "en", "por", "que", "mas", "barato", "barata", "baratos", "baratas", "precio", "comprar");

    private final boolean habilitado;
    private final String urlBase;
    private final List<Producto> catalogo;

    /**
     * Constructor que usa Spring.
     *
     * @param conversor conversor de moneda
     * @param habilitado si la tienda de demostración se consulta en las búsquedas
     * @param urlBase dirección pública de Marketradar, para armar los enlaces de los productos
     */
    @Autowired
    public AdaptadorDemostracion(ConversorMoneda conversor,
                                 @Value("${marketradar.marketplaces.demostracion.habilitado:false}") boolean habilitado,
                                 @Value("${marketradar.marketplaces.demostracion.url-base:http://localhost:8080}") String urlBase) {
        this(conversor, habilitado, urlBase, leerCatalogo());
    }

    /**
     * Constructor para las pruebas, con el catálogo ya cargado.
     *
     * @param conversor conversor de moneda
     * @param habilitado si se consulta
     * @param urlBase dirección pública de Marketradar
     * @param catalogo productos de ejemplo
     */
    AdaptadorDemostracion(ConversorMoneda conversor, boolean habilitado, String urlBase, List<Producto> catalogo) {
        super(CODIGO, conversor, 50);
        this.habilitado = habilitado;
        this.urlBase = quitarBarraFinal(urlBase);
        this.catalogo = List.copyOf(catalogo);
    }

    @Override
    public boolean estaHabilitado() {
        return habilitado && !catalogo.isEmpty();
    }

    @Override
    protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
        List<String> palabras = palabrasClave(consulta.getTermino());
        if (palabras.isEmpty()) {
            return List.of();
        }
        return catalogo.stream()
                .filter(producto -> coincide(producto, palabras))
                .limit(LIMITE_RESULTADOS)
                .map(this::aOferta)
                .toList();
    }

    /**
     * Producto del catálogo de ejemplo.
     *
     * @param id identificador, por ejemplo {@code DEMO-001}
     * @param titulo nombre que ve la persona
     * @param precio precio en pesos colombianos
     * @param envio costo del envío; 0 si es gratis
     * @param condicion nuevo, usado o reacondicionado
     * @param vendedor vendedor de ejemplo
     * @param calificacion promedio de opiniones, de 1 a 5
     * @param resenas cantidad de opiniones
     * @param palabras otras palabras con las que se puede encontrar, sin tildes
     */
    record Producto(String id, String titulo, BigDecimal precio, BigDecimal envio, String condicion,
                    String vendedor, BigDecimal calificacion, Integer resenas, String palabras) {
    }

    private OfertaNormalizada aOferta(Producto producto) {
        return OfertaNormalizada.builder()
                .codigoMarketplace(CODIGO)
                .idExterno(producto.id())
                .titulo(producto.titulo())
                .precio(producto.precio())
                .moneda("COP")
                .costoEnvio(producto.envio())
                .condicion(producto.condicion())
                .vendedor(producto.vendedor())
                .urlDestino(urlBase + "/tienda-demostracion.html?producto=" + producto.id())
                .calificacion(producto.calificacion())
                .totalResenas(producto.resenas())
                .build();
    }

    static boolean coincide(Producto producto, List<String> palabras) {
        String texto = " " + String.join(" ", palabrasClave(producto.titulo() + " " + producto.palabras())) + " ";
        return palabras.stream().allMatch(palabra -> contiene(texto, palabra));
    }

    private static boolean contiene(String texto, String palabra) {
        if (texto.contains(" " + palabra)) {
            return true;
        }
        if (palabra.length() > 4 && palabra.endsWith("es") && texto.contains(" " + palabra.substring(0, palabra.length() - 2))) {
            return true;
        }
        return palabra.length() > 3 && palabra.endsWith("s") && texto.contains(" " + palabra.substring(0, palabra.length() - 1));
    }

    /**
     * Pasa el texto a minúsculas, le quita las tildes y las palabras que no ayudan a buscar.
     *
     * @param texto lo que escribió la persona
     * @return palabras para comparar
     */
    static List<String> palabrasClave(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(sinTildes.split("[^a-z0-9]+"))
                .filter(palabra -> palabra.length() > 1 && !PALABRAS_VACIAS.contains(palabra))
                .toList();
    }

    static List<Producto> leerCatalogo() {
        try (InputStream entrada = AdaptadorDemostracion.class.getResourceAsStream("/demostracion/catalogo.json")) {
            if (entrada == null) {
                return List.of();
            }
            return new ObjectMapper().readValue(entrada, new TypeReference<List<Producto>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el catálogo de la tienda de demostración", e);
        }
    }

    private static String quitarBarraFinal(String url) {
        String limpia = url == null || url.isBlank() ? "http://localhost:8080" : url.strip();
        return limpia.endsWith("/") ? limpia.substring(0, limpia.length() - 1) : limpia;
    }
}
