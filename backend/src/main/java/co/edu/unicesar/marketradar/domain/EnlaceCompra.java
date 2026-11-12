package co.edu.unicesar.marketradar.domain;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A dónde lleva de verdad el enlace de una oferta, para que el botón diga eso y no otra cosa.
 *
 * <ul>
 *   <li>{@link Tipo#DIRECTO}: la página de la tienda.</li>
 *   <li>{@link Tipo#REDIRECCION}: un enlace de Google que pasa a la tienda (por ejemplo {@code google.com/url?url=}).
 *       El botón nombra la tienda de destino. Los parámetros no se tocan, porque pueden identificar la oferta.</li>
 *   <li>{@link Tipo#PAGINA_GOOGLE}: la ficha del producto en Google Shopping, donde están las tiendas que lo venden.</li>
 *   <li>{@link Tipo#BUSQUEDA_GOOGLE}: una búsqueda en Google con el nombre de la oferta. No es la oferta.</li>
 * </ul>
 *
 * @param tipo qué clase de enlace es
 * @param dominio servidor al que se llega (el de la tienda en una redirección), sin {@code www.}
 */
public record EnlaceCompra(Tipo tipo, String dominio) {

    /** Qué clase de enlace es. */
    public enum Tipo { DIRECTO, REDIRECCION, PAGINA_GOOGLE, BUSQUEDA_GOOGLE }

    /** Largo máximo de una dirección que se acepta. */
    public static final int LARGO_MAXIMO = 2048;

    private static final Pattern GOOGLE = Pattern.compile("(^|\\.)google\\.[a-z]{2,3}(\\.[a-z]{2})?$");
    private static final Pattern IP = Pattern.compile("^[0-9.]+$|^\\[.*]$|:");

    /**
     * Revisa que una dirección se pueda abrir sin riesgo: {@code https}, con un servidor con nombre (no una IP ni
     * {@code localhost}), sin usuario o clave antes del servidor ({@code https://banco.com@otro.com}) y de largo
     * razonable. Así nunca se abre {@code javascript:} ni otra cosa que no sea una página web.
     *
     * @param url dirección
     * @return {@code true} si se puede ofrecer como enlace
     */
    public static boolean esSeguro(String url) {
        if (url == null || url.length() > LARGO_MAXIMO || !url.startsWith("https://")) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String servidor = uri.getHost();
            return servidor != null && servidor.contains(".") && !IP.matcher(servidor).find()
                    && uri.getRawUserInfo() == null && !servidor.endsWith(".");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Clasifica una dirección que ya pasó {@link #esSeguro(String)}.
     *
     * @param url dirección de la oferta
     * @return tipo y servidor de destino
     */
    public static EnlaceCompra de(String url) {
        URI uri = URI.create(url);
        String servidor = sinWww(uri.getHost());
        if (!GOOGLE.matcher(servidor).find()) {
            return new EnlaceCompra(Tipo.DIRECTO, servidor);
        }
        String camino = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (camino.equals("/url") || camino.startsWith("/aclk")) {
            Optional<String> destino = parametro(uri.getRawQuery(), "url")
                    .or(() -> parametro(uri.getRawQuery(), "q"))
                    .or(() -> parametro(uri.getRawQuery(), "adurl"))
                    .filter(EnlaceCompra::esSeguro);
            if (destino.isPresent()) {
                String llegada = sinWww(URI.create(destino.get()).getHost());
                if (!GOOGLE.matcher(llegada).find()) {
                    return new EnlaceCompra(Tipo.REDIRECCION, llegada);
                }
            }
            return new EnlaceCompra(Tipo.BUSQUEDA_GOOGLE, servidor);
        }
        if (camino.startsWith("/shopping/product") || camino.startsWith("/products")) {
            return new EnlaceCompra(Tipo.PAGINA_GOOGLE, servidor);
        }
        return new EnlaceCompra(Tipo.BUSQUEDA_GOOGLE, servidor);
    }

    /**
     * Texto del botón, que dice a dónde lleva.
     *
     * @return por ejemplo "Ver la oferta en falabella.com.co" o "Buscar esta oferta en Google"
     */
    public String textoBoton() {
        return switch (tipo) {
            case DIRECTO, REDIRECCION -> "Ver la oferta en " + dominio;
            case PAGINA_GOOGLE -> "Ver las tiendas en Google Shopping";
            case BUSQUEDA_GOOGLE -> "Buscar esta oferta en Google";
        };
    }

    /** @return {@code true} si el enlace abre la oferta en la tienda (directo o pasando por Google) */
    public boolean llevaALaTienda() {
        return tipo == Tipo.DIRECTO || tipo == Tipo.REDIRECCION;
    }

    private static Optional<String> parametro(String consulta, String nombre) {
        if (consulta == null) {
            return Optional.empty();
        }
        for (String parte : consulta.split("&")) {
            int igual = parte.indexOf('=');
            if (igual > 0 && parte.substring(0, igual).equals(nombre)) {
                try {
                    return Optional.of(URLDecoder.decode(parte.substring(igual + 1), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static String sinWww(String servidor) {
        return servidor == null ? "" : servidor.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    }
}
