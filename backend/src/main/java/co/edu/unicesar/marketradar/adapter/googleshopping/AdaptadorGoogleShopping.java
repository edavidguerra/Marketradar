package co.edu.unicesar.marketradar.adapter.googleshopping;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplaceBase;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.domain.CondicionProducto;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.EnlaceCompra;
import co.edu.unicesar.marketradar.domain.EstadoEnvio;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ResenasProducto;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Precios reales de tiendas colombianas a través de Google Shopping, con la API de SerpApi
 * ({@code GET https://serpapi.com/search.json?engine=google_shopping}).
 *
 * <p>Una sola consulta trae productos de muchas tiendas (Éxito, Falabella, Alkosto, Mercado Libre...).
 * La tienda real de cada producto queda en {@code vendedor}. La clave se crea en https://serpapi.com y va en
 * {@code SERPAPI_API_KEY}; nunca en el código.</p>
 */
@Component
public class AdaptadorGoogleShopping extends AdaptadorMarketplaceBase {

    public static final String CODIGO = "googleshopping";

    /** Las claves de reseñas de SerpApi son largas, pero no más que esto. */
    static final int LARGO_TOKEN = 4096;

    /** Opiniones escritas que se muestran de cada producto. */
    static final int MAXIMO_RESENAS = 10;

    /** Se piden más de los que caben en una página, para que el filtro de complementos tenga de dónde elegir. */
    static final int LIMITE_RESULTADOS = 40;
    /** Búsqueda de Google Shopping Colombia, para los productos que llegan sin enlace. */
    static final String BUSQUEDA_GOOGLE = "https://www.google.com.co/search?tbm=shop&gl=co&hl=es&q=";
    private static final Logger LOG = LoggerFactory.getLogger(AdaptadorGoogleShopping.class);

    private final boolean habilitado;
    private final String apiKey;
    private final RestClient cliente;

    /**
     * Constructor que usa Spring: crea el cliente HTTP con tiempos de espera.
     *
     * @param conversor conversor de moneda
     * @param builder constructor de clientes HTTP de Spring
     * @param habilitado si se consulta en las búsquedas
     * @param apiKey clave privada de SerpApi
     * @param urlApi dirección base de SerpApi
     */
    @Autowired
    public AdaptadorGoogleShopping(ConversorMoneda conversor, RestClient.Builder builder,
                                   @Value("${marketradar.marketplaces.google-shopping.habilitado:false}") boolean habilitado,
                                   @Value("${marketradar.marketplaces.google-shopping.api-key:}") String apiKey,
                                   @Value("${marketradar.marketplaces.google-shopping.url-api:https://serpapi.com}") String urlApi) {
        this(conversor, builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(Duration.ofSeconds(3))
                                .withReadTimeout(Duration.ofSeconds(8))))
                .baseUrl(urlApi)
                .build(), habilitado, apiKey);
    }

    /**
     * Constructor para las pruebas, con el cliente HTTP ya creado.
     *
     * @param conversor conversor de moneda
     * @param cliente cliente HTTP con la dirección base de SerpApi
     * @param habilitado si se consulta
     * @param apiKey clave de SerpApi
     */
    AdaptadorGoogleShopping(ConversorMoneda conversor, RestClient cliente, boolean habilitado, String apiKey) {
        super(CODIGO, conversor, 2);
        this.cliente = cliente;
        this.habilitado = habilitado;
        this.apiKey = apiKey;
    }

    @Override
    public boolean estaHabilitado() {
        return habilitado && tieneValor(apiKey);
    }

    @Override
    protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
        Respuesta respuesta;
        try {
            respuesta = cliente.get()
                    // Los valores van como variables para que se codifiquen completos ("+" como %2B, no como espacio).
                    .uri(uri -> uri.path("/search.json")
                            .queryParam("engine", "google_shopping")
                            .queryParam("q", "{q}")
                            .queryParam("gl", "co")
                            .queryParam("hl", "es")
                            .queryParam("google_domain", "google.com.co")
                            .queryParam("api_key", "{clave}")
                            .build(Map.of("q", consulta.getTermino(), "clave", apiKey)))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Respuesta.class);
        } catch (RestClientResponseException e) {
            throw errorHttp(e);
        } catch (RestClientException e) {
            // No se encadena la causa: su mensaje trae la URL completa, con la clave de SerpApi.
            throw new MarketplaceException(CODIGO, "No se pudo consultar SerpApi: " + e.getClass().getSimpleName());
        }
        if (respuesta == null) {
            return List.of();
        }
        if (respuesta.error() != null) {
            // SerpApi responde 200 con este texto cuando Google no encontró nada.
            if (respuesta.error().toLowerCase(Locale.ROOT).contains("hasn't returned any results")) {
                return List.of();
            }
            throw new MarketplaceException(CODIGO, "SerpApi respondió: " + respuesta.error());
        }
        Map<String, OfertaNormalizada> ofertas = new LinkedHashMap<>();
        List<Resultado> todos = todosLosResultados(respuesta);
        int sinEnlacePropio = 0;
        int sinMoneda = 0;
        for (Resultado resultado : todos) {
            if (resultado.precio() != null && monedaDe(resultado.precioTexto()).isEmpty()) {
                // Un precio en una moneda que no se reconoce no se puede mostrar en pesos: se deja por fuera.
                sinMoneda++;
                LOG.info("Google Shopping: se omitió \"{}\" porque su precio \"{}\" está en una moneda desconocida",
                        recortar(String.valueOf(resultado.titulo()), 80), recortar(resultado.precioTexto(), 40));
                continue;
            }
            OfertaNormalizada oferta = aOferta(resultado);
            if (oferta != null) {
                ofertas.putIfAbsent(oferta.getIdExterno(), oferta);
                if (oferta.getUrlDestino().startsWith(BUSQUEDA_GOOGLE)) {
                    sinEnlacePropio++;
                }
            }
            if (ofertas.size() == LIMITE_RESULTADOS) {
                break;
            }
        }
        // Sirve para revisar en los registros de Render qué llegó, sin mostrar la clave.
        LOG.info("Google Shopping \"{}\": {} productos recibidos, {} usados ({} con enlace a una búsqueda de Google, "
                + "{} en moneda desconocida)", consulta.getTermino(), todos.size(), ofertas.size(), sinEnlacePropio,
                sinMoneda);
        return List.copyOf(ofertas.values());
    }

    /**
     * Google no siempre entrega los productos en la misma lista. Con productos populares ("playstation 5",
     * "iphone 15") los agrupa por categoría y {@code shopping_results} llega vacío o lleno de juegos y
     * accesorios; arriba de la página a veces hay otros productos destacados. Se juntan todas las listas, sin
     * repetir, y van primero las categorías, que es donde Google pone el producto en sí (la consola).
     */
    private static List<Resultado> todosLosResultados(Respuesta respuesta) {
        List<Resultado> todos = new ArrayList<>();
        if (respuesta.porCategoria() != null) {
            for (Categoria categoria : respuesta.porCategoria()) {
                if (categoria != null && categoria.resultados() != null) {
                    todos.addAll(categoria.resultados());
                }
            }
        }
        if (respuesta.resultados() != null) {
            todos.addAll(respuesta.resultados());
        }
        if (respuesta.destacados() != null) {
            todos.addAll(respuesta.destacados());
        }
        todos.removeIf(java.util.Objects::isNull);
        return todos;
    }

    private static OfertaNormalizada aOferta(Resultado resultado) {
        if (!tieneValor(resultado.titulo()) || resultado.precio() == null || resultado.precio().signum() <= 0) {
            return null;
        }
        // Se prefiere el enlace a la tienda; si no viene, la ficha del producto en Google Shopping. En Colombia
        // casi ningún producto trae esos enlaces, así que si faltan se ofrece buscar la oferta en Google, y el botón
        // lo dice así (ver EnlaceCompra). Solo se aceptan enlaces https sin usuario ni IP.
        String enlace = EnlaceCompra.esSeguro(resultado.link()) ? resultado.link()
                : EnlaceCompra.esSeguro(resultado.productLink()) ? resultado.productLink()
                : enlaceBusqueda(resultado.titulo(), resultado.tienda());
        String id = tieneValor(resultado.productId()) ? resultado.productId()
                : Integer.toHexString(enlace.hashCode());
        String moneda = monedaDe(resultado.precioTexto()).orElse("COP");
        BigDecimal precio = resultado.precio().setScale(2, RoundingMode.HALF_UP);
        Envio envio = envio(resultado.envio(), moneda, precio);
        Optional<CondicionProducto> condicion = CondicionProducto.detectar(resultado.condicionUsado(),
                resultado.etiqueta(), resultado.extensiones() == null ? null : String.join(" ", resultado.extensiones()),
                resultado.titulo());
        return OfertaNormalizada.builder()
                .codigoMarketplace(CODIGO)
                .idExterno(recortar(id, 120))
                .titulo(recortar(resultado.titulo(), 400))
                .precio(precio)
                .moneda(moneda)
                .precioTexto(resultado.precioTexto() == null ? null : recortar(resultado.precioTexto(), 60))
                .alertaPrecio(alertaPrecio(resultado.precioTexto(), precio))
                .costoEnvio(envio.costo())
                .estadoEnvio(envio.estado())
                .condicion(condicion.map(CondicionProducto::codigo).orElse(null))
                .vendedor(tieneValor(resultado.tienda()) ? recortar(resultado.tienda(), 150) : null)
                .urlDestino(enlace)
                .urlImagen(imagenSegura(resultado.imagen()))
                .calificacion(calificacion(resultado.calificacion()))
                .totalResenas(resultado.resenas() != null && resultado.resenas() > 0 ? resultado.resenas() : null)
                .tokenResenas(tieneValor(resultado.tokenResenas()) && resultado.tokenResenas().length() <= LARGO_TOKEN
                        ? resultado.tokenResenas() : null)
                .build();
    }

    /** Precios que no son el valor completo del producto: "desde", por cuotas o por mes. */
    private static final Pattern PRECIO_PARCIAL = Pattern.compile(
            "desde|a partir|cuota|/\\s?mes|al mes|mensual|\\bx\\s?\\d+\\b|per month|/mo\\b|from\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Revisa que el precio que se va a usar sea el precio completo y el mismo que muestra la tienda.
     *
     * @param texto precio como lo escribe Google ({@code price})
     * @param precio precio ya leído por SerpApi ({@code extracted_price})
     * @return por qué no se puede confiar en el precio, o {@code null} si está bien
     */
    static String alertaPrecio(String texto, BigDecimal precio) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        if (PRECIO_PARCIAL.matcher(texto).find()) {
            return "Es un precio \"desde\" o por cuotas, no el precio completo";
        }
        Matcher monto = MONTO_LIBRE.matcher(texto);
        if (monto.find()) {
            BigDecimal leido = numero(monto.group());
            if (leido != null && leido.signum() > 0
                    && leido.subtract(precio).abs().compareTo(precio.multiply(new BigDecimal("0.01"))) > 0) {
                return "El precio que leímos no coincide con el que muestra la tienda";
            }
        }
        return null;
    }

    /** Primer número del texto del precio, con sus puntos y comas. */
    private static final Pattern MONTO_LIBRE = Pattern.compile("\\d[\\d.,]*\\d|\\d");

    /**
     * Reseñas de un producto con la API de producto de Google de SerpApi
     * ({@code engine=google_immersive_product}). Cada llamada gasta una búsqueda del plan, por eso solo se hace
     * cuando la persona presiona "Ver reseñas" y el resultado queda en caché.
     *
     * @param token {@code immersive_product_page_token} que vino con el producto en la búsqueda
     * @return calificación, cuántas opiniones hay de cada número de estrellas y algunas opiniones escritas
     */
    public ResenasProducto resenas(String token) {
        RespuestaProducto respuesta;
        try {
            respuesta = cliente.get()
                    // La clave del producto viene en base64 y casi siempre trae "+", "/" o "=": si no se codifican,
                    // SerpApi recibe otra clave y responde que no hay resultados.
                    .uri(uri -> uri.path("/search.json")
                            .queryParam("engine", "google_immersive_product")
                            .queryParam("page_token", "{token}")
                            .queryParam("api_key", "{clave}")
                            .build(Map.of("token", token, "clave", apiKey)))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(RespuestaProducto.class);
        } catch (RestClientResponseException e) {
            throw errorHttp(e);
        } catch (RestClientException e) {
            // Igual que en la búsqueda: la causa trae la URL con la clave, así que no se encadena.
            throw new MarketplaceException(CODIGO, "No se pudo consultar SerpApi: " + e.getClass().getSimpleName());
        }
        if (respuesta == null || respuesta.producto() == null) {
            if (respuesta != null && respuesta.error() != null
                    && !respuesta.error().toLowerCase(Locale.ROOT).contains("hasn't returned any results")) {
                throw new MarketplaceException(CODIGO, "SerpApi respondió: " + respuesta.error());
            }
            return new ResenasProducto(null, null, List.of(), List.of());
        }
        Producto producto = respuesta.producto();
        List<ResenasProducto.Estrellas> distribucion = new ArrayList<>();
        if (producto.estrellas() != null) {
            producto.estrellas().stream()
                    .filter(e -> e != null && e.estrellas() != null && e.estrellas() >= 1 && e.estrellas() <= 5
                            && e.cantidad() != null && e.cantidad() >= 0)
                    .sorted((a, b) -> b.estrellas() - a.estrellas())
                    .forEach(e -> distribucion.add(new ResenasProducto.Estrellas(e.estrellas(), e.cantidad())));
        }
        List<ResenasProducto.Resena> resenas = new ArrayList<>();
        java.util.Set<String> vistas = new java.util.HashSet<>();
        if (producto.opiniones() != null) {
            for (Opinion opinion : producto.opiniones()) {
                if (opinion == null || !tieneValor(opinion.texto())) {
                    continue;
                }
                // La misma opinión publicada en dos sitios cuenta una sola vez.
                String clave = (opinion.autor() == null ? "" : opinion.autor().strip().toLowerCase(Locale.ROOT)) + "|"
                        + opinion.texto().replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
                if (!vistas.add(clave)) {
                    continue;
                }
                resenas.add(new ResenasProducto.Resena(recortarONulo(opinion.titulo(), 150),
                        recortar(opinion.texto().strip(), 800), recortarONulo(opinion.autor(), 80),
                        recortarONulo(opinion.fuente(), 80), calificacion(opinion.calificacion()),
                        recortarONulo(opinion.fecha(), 40)));
                if (resenas.size() == MAXIMO_RESENAS) {
                    break;
                }
            }
        }
        Integer total = producto.totalResenas() != null && producto.totalResenas() > 0 ? producto.totalResenas() : null;
        return new ResenasProducto(calificacion(producto.calificacion()), total, distribucion, resenas,
                recortarONulo(producto.titulo(), 400));
    }

    private static String recortarONulo(String texto, int maximo) {
        return tieneValor(texto) ? recortar(texto.strip(), maximo) : null;
    }

    /** Con {@code gl=co} los precios llegan en pesos; si el texto dice US$ o USD, vienen en dólares. */
    static String moneda(String precioTexto) {
        return monedaDe(precioTexto).orElse("COP");
    }

    /** Símbolos de moneda que se reconocen, del más largo al más corto. */
    private static final Map<String, String> SIMBOLOS = new LinkedHashMap<>();

    static {
        SIMBOLOS.put("US$", "USD");
        SIMBOLOS.put("USD", "USD");
        SIMBOLOS.put("COL$", "COP");
        SIMBOLOS.put("COP", "COP");
        SIMBOLOS.put("MX$", "MXN");
        SIMBOLOS.put("MXN", "MXN");
        SIMBOLOS.put("CA$", "CAD");
        SIMBOLOS.put("CAD", "CAD");
        SIMBOLOS.put("R$", "BRL");
        SIMBOLOS.put("BRL", "BRL");
        SIMBOLOS.put("EUR", "EUR");
        SIMBOLOS.put("€", "EUR");
        SIMBOLOS.put("GBP", "GBP");
        SIMBOLOS.put("£", "GBP");
    }

    /** Letras pegadas antes de un "$" que no reconocemos, como "A$" o "NZ$": es otra moneda. */
    private static final Pattern OTRO_DOLAR = Pattern.compile("[A-Z]{1,3}\\$");

    /**
     * Moneda del precio según su texto. Con {@code gl=co}, "$" solo es peso colombiano. Si el texto trae otra
     * moneda que no se reconoce ("A$", "S/", "¥"), no se adivina.
     *
     * @param precioTexto precio como lo escribe Google, o {@code null}
     * @return código ISO 4217, o vacío si la moneda no se reconoce
     */
    static Optional<String> monedaDe(String precioTexto) {
        if (precioTexto == null || precioTexto.isBlank()) {
            return Optional.of("COP");
        }
        String texto = precioTexto.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, String> simbolo : SIMBOLOS.entrySet()) {
            if (texto.contains(simbolo.getKey())) {
                return Optional.of(simbolo.getValue());
            }
        }
        if (OTRO_DOLAR.matcher(texto).find() || texto.contains("¥") || texto.contains("S/")) {
            return Optional.empty();
        }
        return Optional.of("COP");
    }

    /** Un valor con su moneda: "$ 12.900", "COP 12.900", "US$ 9,99", "USD 9.99". */
    private static final Pattern MONTO = Pattern.compile("(US\\$|USD|COP|\\$)\\s?(\\d[\\d.,]*)",
            Pattern.CASE_INSENSITIVE);

    /** "Envío gratis en compras de más de $ 100.000" o "Free delivery on $45+" no es envío gratis siempre. */
    private static final Pattern GRATIS_CON_CONDICION = Pattern.compile(
            "compras|superior|desde|m[aá]s de|mayor|m[ií]nim|\\bon\\b|\\bover\\b|\\+");

    static BigDecimal costoEnvio(String envio) {
        return costoEnvio(envio, "COP");
    }

    /**
     * Costo del envío confirmado, o {@code null} si no se sabe (ver {@link #envio(String, String, BigDecimal)}).
     *
     * @param envio texto de la entrega, o {@code null}
     * @param monedaPrecio moneda del precio del producto
     * @return costo del envío, 0 si es gratis, o {@code null} si no se sabe
     */
    static BigDecimal costoEnvio(String envio, String monedaPrecio) {
        Envio leido = envio(envio, monedaPrecio, null);
        return leido.estado().confirmado() ? leido.costo() : null;
    }

    /** "Envío desde $ 9.900", "aprox. $ 12.000": el valor no es seguro. */
    private static final Pattern ENVIO_APROXIMADO = Pattern.compile("desde|a partir|aprox|estimad|from|approx",
            Pattern.CASE_INSENSITIVE);
    /** Lo que el envío gratis exige: "en compras superiores a $ 100.000". */
    private static final Pattern MINIMO_ENVIO_GRATIS = Pattern.compile(
            "(?:compras|pedidos|superior(?:es)?|m[aá]s de|mayor(?:es)?|m[ií]nim[oa]|desde|over|on)[^\\d$]*"
                    + "(?:US\\$|USD|COP|\\$)?\\s?(\\d[\\d.,]*)", Pattern.CASE_INSENSITIVE);

    /**
     * Lo que dice Google del envío ({@code delivery}):
     * <ul>
     *   <li>"Envío gratis" o "Free delivery": gratis.</li>
     *   <li>"Envío gratis en compras superiores a $ 100.000": gratis solo si el precio llega a ese valor; si no, o si
     *       no se puede leer el valor, desconocido.</li>
     *   <li>"Envío: $ 12.900" o "Get it today ($17)": conocido, en la moneda del precio.</li>
     *   <li>"Envío desde $ 9.900": estimado, porque puede costar más.</li>
     *   <li>Cualquier otra cosa, o un valor en otra moneda: desconocido. Nunca se cuenta como cero.</li>
     * </ul>
     *
     * @param envio texto de la entrega, o {@code null}
     * @param monedaPrecio moneda del precio del producto
     * @param precio precio del producto, para revisar el mínimo del envío gratis; puede ser {@code null}
     * @return estado y costo
     */
    static Envio envio(String envio, String monedaPrecio, BigDecimal precio) {
        if (envio == null || envio.isBlank()) {
            return Envio.DESCONOCIDO;
        }
        String texto = envio.toLowerCase(Locale.ROOT);
        boolean gratis = texto.contains("gratis") || texto.contains("free") || texto.contains("sin costo");
        if (gratis) {
            if (!GRATIS_CON_CONDICION.matcher(texto).find()) {
                return new Envio(EstadoEnvio.GRATIS, BigDecimal.ZERO);
            }
            Matcher minimo = MINIMO_ENVIO_GRATIS.matcher(envio);
            BigDecimal valorMinimo = minimo.find() ? numero(minimo.group(1)) : null;
            return precio != null && valorMinimo != null && precio.compareTo(valorMinimo) >= 0
                    ? new Envio(EstadoEnvio.GRATIS, BigDecimal.ZERO) : Envio.DESCONOCIDO;
        }
        Matcher monto = MONTO.matcher(envio);
        if (!monto.find()) {
            return Envio.DESCONOCIDO;
        }
        String simbolo = monto.group(1).toUpperCase(Locale.ROOT);
        String moneda = simbolo.equals("US$") || simbolo.equals("USD") ? "USD"
                : simbolo.equals("COP") ? "COP" : monedaPrecio;
        if (!moneda.equals(monedaPrecio)) {
            return Envio.DESCONOCIDO;
        }
        BigDecimal valor = numero(monto.group(2));
        if (valor == null || valor.signum() <= 0) {
            return Envio.DESCONOCIDO;
        }
        return new Envio(ENVIO_APROXIMADO.matcher(texto).find() ? EstadoEnvio.ESTIMADO : EstadoEnvio.CONOCIDO, valor);
    }

    /**
     * Envío leído del texto de Google.
     *
     * @param estado qué se sabe
     * @param costo valor en la moneda del precio; {@code null} si es desconocido
     */
    record Envio(EstadoEnvio estado, BigDecimal costo) {
        static final Envio DESCONOCIDO = new Envio(EstadoEnvio.DESCONOCIDO, null);
    }

    /**
     * Lee "12.900", "12,900", "1.299,50" o "1,299.50". Si después del último punto o coma hay una o dos cifras,
     * son centavos; si hay tres, son miles.
     */
    static BigDecimal numero(String texto) {
        String limpio = texto.replaceAll("[.,]+$", "");
        int ultimo = Math.max(limpio.lastIndexOf('.'), limpio.lastIndexOf(','));
        String entero;
        String decimales = "";
        if (ultimo >= 0 && limpio.length() - ultimo - 1 <= 2) {
            entero = limpio.substring(0, ultimo);
            decimales = limpio.substring(ultimo + 1);
        } else {
            entero = limpio;
        }
        entero = entero.replaceAll("[.,]", "");
        if (entero.isEmpty() || entero.length() > 12) {
            return null;
        }
        return new BigDecimal(decimales.isEmpty() ? entero : entero + "." + decimales);
    }

    private static BigDecimal calificacion(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0 || valor.compareTo(BigDecimal.valueOf(5)) > 0) {
            return null;
        }
        return valor.setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Búsqueda del producto en Google Shopping Colombia, para cuando SerpApi no trae ningún enlace.
     *
     * @param titulo nombre del producto
     * @param tienda tienda que lo vende, o {@code null}
     * @return enlace https
     */
    static String enlaceBusqueda(String titulo, String tienda) {
        String texto = tieneValor(tienda) ? titulo.strip() + " " + tienda.strip() : titulo.strip();
        return BUSQUEDA_GOOGLE + URLEncoder.encode(recortar(texto, 300), StandardCharsets.UTF_8);
    }

    static boolean esSeguro(String url) {
        return EnlaceCompra.esSeguro(url);
    }

    /** Las imágenes que llegan por http se piden por https; las que no son web se descartan. */
    static String imagenSegura(String url) {
        if (url != null && url.startsWith("http://")) {
            url = "https://" + url.substring("http://".length());
        }
        return esSeguro(url) ? url : null;
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    private static MarketplaceException errorHttp(RestClientResponseException e) {
        int codigo = e.getStatusCode().value();
        if (codigo == 401) {
            return new MarketplaceException(CODIGO, "SerpApi rechazó la clave (401). Revise SERPAPI_API_KEY.", e);
        }
        if (codigo == 429) {
            return new MarketplaceException(CODIGO,
                    "Se acabaron las búsquedas del plan de SerpApi o se superó el límite por hora (429).", e);
        }
        return new MarketplaceException(CODIGO, "SerpApi respondió con error " + codigo, e);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(@JsonProperty("shopping_results") List<Resultado> resultados,
                     @JsonProperty("categorized_shopping_results") List<Categoria> porCategoria,
                     @JsonProperty("inline_shopping_results") List<Resultado> destacados,
                     @JsonProperty("error") String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Categoria(@JsonProperty("title") String titulo,
                     @JsonProperty("shopping_results") List<Resultado> resultados) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultado(@JsonProperty("title") String titulo,
                     @JsonProperty("product_id") String productId,
                     @JsonProperty("product_link") String productLink,
                     @JsonProperty("link") String link,
                     @JsonProperty("source") String tienda,
                     @JsonProperty("price") String precioTexto,
                     @JsonProperty("extracted_price") BigDecimal precio,
                     @JsonProperty("rating") BigDecimal calificacion,
                     @JsonProperty("reviews") Integer resenas,
                     @JsonProperty("thumbnail") String imagen,
                     @JsonProperty("delivery") String envio,
                     @JsonProperty("immersive_product_page_token") String tokenResenas,
                     @JsonProperty("second_hand_condition") String condicionUsado,
                     @JsonProperty("tag") String etiqueta,
                     @JsonProperty("extensions") List<String> extensiones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaProducto(@JsonProperty("product_results") Producto producto,
                             @JsonProperty("error") String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Producto(@JsonProperty("title") String titulo,
                    @JsonProperty("rating") BigDecimal calificacion,
                    @JsonProperty("reviews") Integer totalResenas,
                    @JsonProperty("ratings") List<Estrella> estrellas,
                    @JsonProperty("user_reviews") List<Opinion> opiniones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Estrella(@JsonProperty("stars") Integer estrellas, @JsonProperty("amount") Integer cantidad) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Opinion(@JsonProperty("title") String titulo,
                   @JsonProperty("text") String texto,
                   @JsonProperty("user_name") String autor,
                   @JsonProperty("source") String fuente,
                   @JsonProperty("rating") BigDecimal calificacion,
                   @JsonProperty("date") String fecha) {
    }
}
