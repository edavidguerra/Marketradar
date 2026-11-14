package co.edu.unicesar.marketradar.adapter.alibaba;

import co.edu.unicesar.marketradar.domain.EnlaceCompra;
import co.edu.unicesar.marketradar.domain.mayorista.EscalaPrecio;
import co.edu.unicesar.marketradar.domain.mayorista.OfertaMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.UnidadVenta;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte las respuestas de API.SH para Alibaba al modelo común. Los dos endpoints usan formatos distintos (revisado
 * en https://api.sh/openapi/alibaba.json el 2026-11-14):
 * <ul>
 *   <li>{@code /alibaba/v1/search}: {@code products[].quantity_prices[]} con {@code min_quantity}, {@code max_quantity},
 *       {@code price} y {@code unit}; {@code min_order} es un texto ("20 pieces"); {@code price} es un texto
 *       ("US $1.75-$2.00") y los números van en {@code price_min} y {@code price_max}.</li>
 *   <li>{@code /alibaba/v1/product}: {@code price.tiers[]} con {@code min_quantity}, {@code max_quantity}, {@code price}
 *       y {@code formatted}; {@code min_order} es un objeto ({@code quantity}, {@code unit}); {@code skus[]} con
 *       {@code price} y {@code options}.</li>
 * </ul>
 * Lo que falta queda en {@code null}: nunca se completa con un valor inventado.
 */
public final class LectorRespuestasAlibaba {

    /** Un tope mayor que esto es "sin tope" (algunas fuentes ponen 999999999 o -1). */
    static final long SIN_TOPE = 100_000_000L;

    private static final Pattern CANTIDAD_Y_UNIDAD = Pattern.compile("(\\d[\\d,.]*)\\s*([A-Za-z][A-Za-z ]*)");
    private static final Pattern PRESENTACION_POR = Pattern.compile(
            "(\\d+)\\s*(pieces|piece|pcs|pc|pairs|pair|units|unit)\\s*(?:/|per|a)\\s*(pack|box|set|bag|carton|package)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRESENTACION_DE = Pattern.compile(
            "(pack|box|set|bag|carton|package)\\s*\\(?\\s*(?:of\\s*)?(\\d+)\\s*(pieces|piece|pcs|pc|pairs|pair|units|unit)",
            Pattern.CASE_INSENSITIVE);

    private LectorRespuestasAlibaba() {
    }

    /**
     * Una página de la búsqueda.
     *
     * @param ofertas publicaciones
     * @param siguientePagina número de la siguiente página, o {@code null} si es la última
     * @param patrocinados cuántas son anuncios, según API.SH
     * @param actualizadoEn cuándo se consultó Alibaba
     * @param desdeCache si API.SH la sirvió de su caché
     */
    public record Pagina(List<OfertaMayorista> ofertas, Integer siguientePagina, int patrocinados,
                         OffsetDateTime actualizadoEn, boolean desdeCache) {
    }

    /**
     * @param raiz respuesta de {@code /alibaba/v1/search}
     * @return página normalizada
     */
    public static Pagina leerBusqueda(JsonNode raiz) {
        if (raiz == null || !raiz.path("products").isArray()) {
            throw new ApiShException(ApiShException.Tipo.RESPUESTA_INVALIDA, "La búsqueda no trae products", null);
        }
        OffsetDateTime actualizado = fecha(raiz.path("fetched_at"));
        List<OfertaMayorista> ofertas = new ArrayList<>();
        for (JsonNode producto : raiz.path("products")) {
            OfertaMayorista oferta = leerProductoDeBusqueda(producto, actualizado);
            if (oferta != null) {
                ofertas.add(oferta);
            }
        }
        JsonNode siguiente = raiz.path("next_page");
        return new Pagina(ofertas, siguiente.isNumber() || siguiente.isTextual() && siguiente.asText().matches("\\d+")
                ? siguiente.asInt() : null, raiz.path("sponsored_count").asInt(0), actualizado,
                raiz.path("cached").asBoolean(false));
    }

    static OfertaMayorista leerProductoDeBusqueda(JsonNode producto, OffsetDateTime actualizado) {
        String id = texto(producto.path("id"));
        String titulo = texto(producto.path("title"));
        String url = enlace(texto(producto.path("url")));
        if (id == null || titulo == null || url == null) {
            return null;
        }
        List<EscalaPrecio> escalas = new ArrayList<>();
        String unidadEscalas = null;
        for (JsonNode escala : producto.path("quantity_prices")) {
            EscalaPrecio leida = escala(escala);
            if (leida != null) {
                escalas.add(leida);
                unidadEscalas = unidadEscalas == null ? texto(escala.path("unit")) : unidadEscalas;
            }
        }
        String textoUnidad = unidadEscalas != null ? unidadEscalas : texto(producto.path("unit"));
        UnidadVenta unidad = UnidadVenta.leer(textoUnidad);
        JsonNode minimo = producto.path("min_order");
        Long pedidoMinimo = null;
        UnidadVenta unidadMinimo = UnidadVenta.DESCONOCIDA;
        String textoMinimo = null;
        if (minimo.isNumber()) {
            pedidoMinimo = minimo.asLong();
            unidadMinimo = unidad;
        } else if (minimo.isTextual()) {
            textoMinimo = minimo.asText();
            Matcher partes = CANTIDAD_Y_UNIDAD.matcher(textoMinimo);
            if (partes.find()) {
                pedidoMinimo = entero(partes.group(1));
                unidadMinimo = UnidadVenta.leer(partes.group(2));
            }
        }
        JsonNode proveedor = producto.path("supplier");
        return new OfertaMayorista(id, titulo, url, imagen(producto), texto(proveedor.path("name")),
                pais(proveedor), anios(proveedor.path("years")),
                proveedor.path("verified").asBoolean(false) || proveedor.path("verified_pro").asBoolean(false),
                producto.path("sponsored").asBoolean(false), pedidoMinimo, unidadMinimo, unidad,
                presentacion(textoUnidad, textoMinimo), ordenar(escalas), numero(producto.path("price_min")),
                numero(producto.path("price_max")), moneda(producto.path("currency"), texto(producto.path("price"))),
                texto(producto.path("price")), List.of(), actualizado, "busqueda");
    }

    /**
     * @param producto respuesta de {@code /alibaba/v1/product}
     * @return publicación normalizada con escalas, pedido mínimo y variantes
     */
    public static OfertaMayorista leerProducto(JsonNode producto) {
        String id = texto(producto.path("id"));
        String titulo = texto(producto.path("title"));
        String url = enlace(texto(producto.path("url")));
        if (id == null || titulo == null || url == null) {
            throw new ApiShException(ApiShException.Tipo.RESPUESTA_INVALIDA, "El detalle no trae id, title o url", null);
        }
        JsonNode precio = producto.path("price");
        List<EscalaPrecio> escalas = new ArrayList<>();
        for (JsonNode escala : precio.path("tiers")) {
            EscalaPrecio leida = escala(escala);
            if (leida != null) {
                escalas.add(leida);
            }
        }
        String textoUnidad = texto(precio.path("unit"));
        JsonNode minimo = producto.path("min_order");
        Long pedidoMinimo = minimo.isObject() ? entero(minimo.path("quantity")) : minimo.isNumber() ? minimo.asLong() : null;
        String textoUnidadMinimo = minimo.isObject() ? texto(minimo.path("unit")) : null;
        UnidadVenta unidad = UnidadVenta.leer(textoUnidad != null ? textoUnidad : textoUnidadMinimo);
        UnidadVenta unidadMinimo = textoUnidadMinimo != null ? UnidadVenta.leer(textoUnidadMinimo)
                : pedidoMinimo != null ? unidad : UnidadVenta.DESCONOCIDA;
        List<BigDecimal> precios = escalas.stream().map(EscalaPrecio::precio).toList();
        BigDecimal menor = precios.stream().min(BigDecimal::compareTo).orElse(null);
        BigDecimal mayor = precios.stream().max(BigDecimal::compareTo).orElse(null);
        String rango = texto(precio.path("range"));
        if (menor == null && rango != null) {
            List<BigDecimal> delRango = numerosDe(rango);
            if (!delRango.isEmpty()) {
                menor = delRango.stream().min(BigDecimal::compareTo).orElseThrow();
                mayor = delRango.stream().max(BigDecimal::compareTo).orElseThrow();
            }
        }
        JsonNode proveedor = producto.path("supplier");
        return new OfertaMayorista(id, titulo, url, imagen(producto), texto(proveedor.path("name")), pais(proveedor),
                anios(proveedor.path("years")), proveedor.path("verified").asBoolean(false), false, pedidoMinimo,
                unidadMinimo, unidad, presentacion(textoUnidad, textoUnidadMinimo), ordenar(escalas), menor, mayor,
                moneda(precio.path("currency"), rango), rango, variantes(producto), fecha(producto.path("fetched_at")),
                "detalle");
    }

    private static List<OfertaMayorista.Variante> variantes(JsonNode producto) {
        Map<String, Boolean> disponibles = new HashMap<>();
        for (JsonNode opcion : producto.path("options")) {
            for (JsonNode valor : opcion.path("values")) {
                String nombre = texto(valor.path("name"));
                if (nombre != null && valor.has("available")) {
                    disponibles.put(nombre.toLowerCase(Locale.ROOT), valor.path("available").asBoolean());
                }
            }
        }
        List<OfertaMayorista.Variante> variantes = new ArrayList<>();
        for (JsonNode sku : producto.path("skus")) {
            String id = texto(sku.path("id"));
            if (id == null) {
                continue;
            }
            List<String> opciones = new ArrayList<>();
            Boolean disponible = null;
            for (JsonNode opcion : sku.path("options")) {
                String nombre = texto(opcion);
                if (nombre == null) {
                    continue;
                }
                opciones.add(nombre);
                Boolean esta = disponibles.get(nombre.toLowerCase(Locale.ROOT));
                if (Boolean.FALSE.equals(esta)) {
                    disponible = false;
                } else if (Boolean.TRUE.equals(esta) && disponible == null) {
                    disponible = true;
                }
            }
            variantes.add(new OfertaMayorista.Variante(id, opciones.isEmpty() ? "Variante " + id
                    : String.join(" / ", opciones), numero(sku.path("price")), disponible));
            if (variantes.size() == 50) {
                break;
            }
        }
        return variantes;
    }

    private static EscalaPrecio escala(JsonNode escala) {
        Long minimo = entero(escala.path("min_quantity"));
        Long maximo = entero(escala.path("max_quantity"));
        BigDecimal precio = numero(escala.path("price"));
        if (minimo == null || minimo < 1 || precio == null || precio.signum() <= 0) {
            return null;
        }
        if (maximo != null && (maximo <= 0 || maximo >= SIN_TOPE)) {
            maximo = null;
        }
        if (maximo != null && maximo < minimo) {
            return null;
        }
        return new EscalaPrecio(minimo, maximo, precio);
    }

    private static List<EscalaPrecio> ordenar(List<EscalaPrecio> escalas) {
        return escalas.stream().sorted(java.util.Comparator.comparingLong(EscalaPrecio::minimo)).toList();
    }

    /** "10 pieces/pack" o "pack of 10 pieces": cuántas piezas trae cada paquete. Solo si lo dice el proveedor. */
    static OfertaMayorista.Presentacion presentacion(String... textos) {
        for (String texto : textos) {
            if (texto == null) {
                continue;
            }
            Matcher por = PRESENTACION_POR.matcher(texto);
            if (por.find()) {
                return new OfertaMayorista.Presentacion(Long.parseLong(por.group(1)), UnidadVenta.leer(por.group(2)));
            }
            Matcher de = PRESENTACION_DE.matcher(texto);
            if (de.find()) {
                return new OfertaMayorista.Presentacion(Long.parseLong(de.group(2)), UnidadVenta.leer(de.group(3)));
            }
        }
        return null;
    }

    private static String pais(JsonNode proveedor) {
        String nombre = texto(proveedor.path("country_name"));
        return nombre != null ? nombre : texto(proveedor.path("country"));
    }

    private static String imagen(JsonNode producto) {
        String imagen = enlace(texto(producto.path("image")));
        if (imagen == null && producto.path("images").isArray() && !producto.path("images").isEmpty()) {
            JsonNode primera = producto.path("images").get(0);
            imagen = enlace(primera.isTextual() ? primera.asText() : texto(primera.path("url")));
        }
        return imagen;
    }

    /** Alibaba a veces da enlaces sin protocolo ("//www.alibaba.com/..."). Solo se aceptan https seguros. */
    static String enlace(String url) {
        if (url == null) {
            return null;
        }
        String completo = url.startsWith("//") ? "https:" + url : url;
        return EnlaceCompra.esSeguro(completo) ? completo : null;
    }

    /** Moneda del campo {@code currency}; si no viene y el texto dice "US $", es dólar. Si no, se desconoce. */
    private static String moneda(JsonNode campo, String textoPrecio) {
        String moneda = texto(campo);
        if (moneda != null && moneda.strip().matches("[A-Za-z]{3}")) {
            return moneda.strip().toUpperCase(Locale.ROOT);
        }
        if (textoPrecio != null && textoPrecio.toUpperCase(Locale.ROOT).contains("US")) {
            return "USD";
        }
        return null;
    }

    private static List<BigDecimal> numerosDe(String texto) {
        List<BigDecimal> numeros = new ArrayList<>();
        Matcher numero = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(texto.replace(",", ""));
        while (numero.find()) {
            numeros.add(new BigDecimal(numero.group()));
        }
        return numeros;
    }

    private static OffsetDateTime fecha(JsonNode campo) {
        String texto = texto(campo);
        if (texto == null) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(texto);
        } catch (DateTimeParseException e) {
            return OffsetDateTime.now();
        }
    }

    private static String texto(JsonNode campo) {
        if (campo == null || campo.isMissingNode() || campo.isNull()) {
            return null;
        }
        String texto = campo.asText().strip();
        return texto.isEmpty() ? null : texto;
    }

    private static BigDecimal numero(JsonNode campo) {
        if (campo == null || campo.isNull() || campo.isMissingNode()) {
            return null;
        }
        if (campo.isNumber()) {
            return campo.decimalValue();
        }
        String texto = campo.asText().replace(",", "").strip();
        return texto.matches("\\d+(\\.\\d+)?") ? new BigDecimal(texto) : null;
    }

    private static Integer anios(JsonNode campo) {
        Long anios = entero(campo);
        return anios == null || anios < 0 || anios > 200 ? null : anios.intValue();
    }

    private static Long entero(JsonNode campo) {
        if (campo == null || campo.isNull() || campo.isMissingNode()) {
            return null;
        }
        if (campo.isIntegralNumber()) {
            return campo.asLong();
        }
        return entero(campo.asText());
    }

    private static Long entero(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.replace(",", "").replaceFirst("\\.0+$", "").strip();
        return limpio.matches("-?\\d{1,12}") ? Long.parseLong(limpio) : null;
    }
}
