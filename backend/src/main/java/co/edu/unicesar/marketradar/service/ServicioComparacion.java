package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.MarketplaceDao;
import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.domain.CategoriaProducto;
import co.edu.unicesar.marketradar.domain.CondicionProducto;
import co.edu.unicesar.marketradar.domain.EnlaceCompra;
import co.edu.unicesar.marketradar.domain.Especificacion;
import co.edu.unicesar.marketradar.domain.Marketplace;
import co.edu.unicesar.marketradar.domain.NormalizadorEspecificaciones;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.UbicacionVendedor;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Arma la tabla para comparar de 2 a 4 ofertas de una misma búsqueda: productos en columnas y características en
 * filas. Nunca inventa un dato: lo que la tienda no informó dice "No disponible". Avisa cuando los productos no son
 * del mismo tipo (un celular y una funda) o no son el mismo modelo, y en ese caso no señala cuál es "más barato".
 */
@Service
public class ServicioComparacion {

    /** Mínimo y máximo de productos que se comparan a la vez. */
    public static final int MINIMO = 2;
    public static final int MAXIMO = 4;

    /** Lo que se muestra cuando falta un dato. */
    public static final String NO_DISPONIBLE = "No disponible";

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a",
            Locale.forLanguageTag("es-CO"));
    private static final Pattern EN_EXISTENCIA = Pattern.compile("(?i)\\b(en stock|disponible|in stock|hay existencias)\\b");
    private static final Pattern AGOTADO = Pattern.compile("(?i)\\b(agotado|sin stock|out of stock|no disponible)\\b");

    private final OfertaDao ofertaDao;
    private final MarketplaceDao marketplaceDao;

    public ServicioComparacion(OfertaDao ofertaDao, MarketplaceDao marketplaceDao) {
        this.ofertaDao = ofertaDao;
        this.marketplaceDao = marketplaceDao;
    }

    /** Un producto, en una columna. */
    public record Producto(long id, String titulo, String codigoTienda, String tienda, String urlImagen,
                           String urlDestino, EnlaceCompra enlace, CategoriaProducto categoria) {
    }

    /**
     * Valor de un producto en una fila.
     *
     * @param valor texto para mostrar, o {@link #NO_DISPONIBLE}
     * @param numero valor numérico para comparar, o {@code null}
     * @param disponible si se conoce el dato
     * @param fuente de dónde salió ({@code tienda}, {@code titulo}) o {@code null}
     */
    public record Celda(String valor, BigDecimal numero, boolean disponible, String fuente) {
        static Celda falta() {
            return new Celda(NO_DISPONIBLE, null, false, null);
        }
    }

    /**
     * Una característica para todos los productos.
     *
     * @param clave nombre interno
     * @param etiqueta nombre para mostrar
     * @param grupo {@code oferta} (datos de la tienda) o {@code especificaciones}
     * @param nota aclaración de la fila, o {@code null}
     * @param celdas un valor por producto, en el mismo orden
     * @param difiere si los productos no tienen todos el mismo valor
     */
    public record Fila(String clave, String etiqueta, String grupo, String nota, List<Celda> celdas, boolean difiere) {
    }

    /**
     * Comparación completa.
     *
     * @param productos columnas
     * @param filas filas
     * @param equivalentes si nada indica que sean productos distintos (tipo, modelo y versión)
     * @param masBaratoId oferta con el precio más bajo entre productos equivalentes y en la misma moneda, o {@code null}
     * @param avisos por qué no se deben comparar como iguales, en frases sencillas
     */
    public record Comparacion(List<Producto> productos, List<Fila> filas, boolean equivalentes, Long masBaratoId,
                              List<String> avisos) {
    }

    /**
     * Compara las ofertas pedidas.
     *
     * @param ids identificadores de 2 a 4 ofertas distintas
     * @return tabla de comparación
     */
    public Comparacion comparar(List<Long> ids) {
        List<Long> unicos = ids == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(ids));
        unicos.removeIf(Objects::isNull);
        if (unicos.size() < MINIMO || unicos.size() > MAXIMO) {
            throw new ValidacionException("Elija entre " + MINIMO + " y " + MAXIMO + " productos distintos para compararlos.");
        }
        List<Oferta> ofertas = new ArrayList<>();
        for (Long id : unicos) {
            ofertas.add(ofertaDao.buscarPorId(id).orElseThrow(() -> new RecursoNoEncontradoException(
                    "Uno de los productos que eligió ya no está disponible. Búsquelo otra vez.")));
        }
        Map<Short, String> codigos = new HashMap<>();
        List<Producto> productos = new ArrayList<>();
        List<List<Especificacion>> especificaciones = new ArrayList<>();
        for (Oferta oferta : ofertas) {
            String codigo = codigos.computeIfAbsent(oferta.getMarketplaceId(), this::codigoTienda);
            String tienda = "googleshopping".equals(codigo) && oferta.getVendedor() != null ? oferta.getVendedor()
                    : ServicioBusqueda.nombreVisible(codigo);
            productos.add(new Producto(oferta.getId(), oferta.getTitulo(), codigo, tienda,
                    EnlaceCompra.esSeguro(oferta.getUrlImagen()) ? oferta.getUrlImagen() : null,
                    oferta.getUrlDestino(), EnlaceCompra.de(oferta.getUrlDestino()),
                    CategoriaProducto.detectar(oferta.getTitulo())));
            especificaciones.add(NormalizadorEspecificaciones.extraer(oferta.getTitulo(), oferta.listaDetalles()));
        }

        List<Fila> filas = new ArrayList<>(filasDeOferta(ofertas, productos));
        filas.addAll(filasDeEspecificaciones(especificaciones));

        List<String> avisos = new ArrayList<>();
        boolean mismoTipo = mismoTipo(productos, avisos);
        boolean mismoModelo = mismoModelo(filas, avisos);
        boolean mismaMoneda = ofertas.stream().map(Oferta::getMoneda).distinct().count() == 1;
        if (!mismaMoneda) {
            avisos.add("Los precios están en monedas distintas: no se pueden comparar directamente.");
        }
        boolean equivalentes = mismoTipo && mismoModelo;
        // Un envío que la tienda no informa nunca cuenta como gratis: sin todos los totales, no hay "más barato".
        boolean totalesConocidos = ofertas.stream().allMatch(o -> o.totalConocido().isPresent());
        if (equivalentes && mismaMoneda && !totalesConocidos) {
            avisos.add("No marcamos cuál es más barato porque al menos una tienda no informa el envío.");
        }
        Long masBarato = equivalentes && mismaMoneda && totalesConocidos ? ofertas.stream()
                .min(java.util.Comparator.comparing(o -> o.totalConocido().orElseThrow())).map(Oferta::getId)
                .orElse(null) : null;
        return new Comparacion(productos, filas, equivalentes, masBarato, avisos);
    }

    private String codigoTienda(short marketplaceId) {
        return marketplaceDao.buscarPorId(marketplaceId).map(Marketplace::getCodigo).orElse("desconocida");
    }

    private static List<Fila> filasDeOferta(List<Oferta> ofertas, List<Producto> productos) {
        List<Fila> filas = new ArrayList<>();
        filas.add(fila("tienda", "Tienda", "oferta", null, productos.stream()
                .map(p -> new Celda(p.tienda(), null, true, null)).toList()));
        filas.add(fila("precio", "Precio del producto", "oferta", "Sin el envío.", ofertas.stream()
                .map(o -> new Celda(pesos(o.getPrecioActual(), o.getMoneda()), o.getPrecioActual(), true, null)).toList()));
        filas.add(fila("moneda", "Moneda", "oferta", null, ofertas.stream()
                .map(o -> new Celda(o.getMoneda(), null, true, null)).toList()));
        filas.add(fila("envio", "Envío", "oferta", "Cuando la tienda no informa el envío, no lo contamos como gratis.",
                ofertas.stream().map(ServicioComparacion::envio).toList()));
        filas.add(fila("condicion", "Condición", "oferta", null, ofertas.stream()
                .map(o -> CondicionProducto.deCodigo(o.getCondicion())
                        .map(c -> new Celda(c.texto(), null, true, null)).orElse(Celda.falta())).toList()));
        filas.add(fila("disponibilidad", "Disponibilidad", "oferta", "Según lo que informó la tienda.", ofertas.stream()
                .map(ServicioComparacion::disponibilidad).toList()));
        filas.add(fila("valoracion", "Valoración del producto", "oferta",
                "Opiniones de compradores sobre el producto, no sobre la tienda que lo vende.",
                ofertas.stream().map(ServicioComparacion::valoracion).toList()));
        filas.add(fila("ubicacion", "Vende desde", "oferta", null, ofertas.stream().map(o -> {
            UbicacionVendedor ubicacion = UbicacionVendedor.de(o.getVendedor(), o.getUrlDestino());
            return ubicacion.texto() == null ? Celda.falta() : new Celda(ubicacion.texto(), null, true, null);
        }).toList()));
        filas.add(fila("consultado", "Precio consultado", "oferta", "El precio puede haber cambiado en la tienda.",
                ofertas.stream().map(o -> o.getConsultadoEn() == null ? Celda.falta()
                        : new Celda(FECHA.format(o.getConsultadoEn().atZoneSameInstant(ZONA)), null, true, null)).toList()));
        return filas;
    }

    private static List<Fila> filasDeEspecificaciones(List<List<Especificacion>> porProducto) {
        Set<String> claves = new LinkedHashSet<>();
        for (String clave : NormalizadorEspecificaciones.ORDEN) {
            if (porProducto.stream().anyMatch(lista -> lista.stream().anyMatch(e -> e.clave().equals(clave)))) {
                claves.add(clave);
            }
        }
        List<Fila> filas = new ArrayList<>();
        for (String clave : claves) {
            List<Celda> celdas = porProducto.stream().map(lista -> lista.stream()
                    .filter(e -> e.clave().equals(clave)).findFirst()
                    .map(e -> new Celda(e.valor(), e.numero(), true, e.fuente()))
                    .orElse(Celda.falta())).toList();
            filas.add(fila(clave, NormalizadorEspecificaciones.etiqueta(clave), "especificaciones", null, celdas));
        }
        return filas;
    }

    private static Fila fila(String clave, String etiqueta, String grupo, String nota, List<Celda> celdas) {
        long distintos = celdas.stream()
                .map(c -> c.disponible() ? c.valor().toLowerCase(Locale.ROOT) : "\u0000falta").distinct().count();
        return new Fila(clave, etiqueta, grupo, nota, List.copyOf(celdas), distintos > 1);
    }

    private boolean mismoTipo(List<Producto> productos, List<String> avisos) {
        for (int i = 0; i < productos.size(); i++) {
            for (int j = i + 1; j < productos.size(); j++) {
                CategoriaProducto a = productos.get(i).categoria();
                CategoriaProducto b = productos.get(j).categoria();
                if (!a.compatibleCon(b)) {
                    avisos.add("No son el mismo tipo de producto (" + a.texto().toLowerCase(Locale.ROOT) + " y "
                            + b.texto().toLowerCase(Locale.ROOT) + "). Los mostramos lado a lado, pero no son "
                            + "equivalentes: no compare sus precios como si fueran el mismo producto.");
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean mismoModelo(List<Fila> filas, List<String> avisos) {
        for (String clave : List.of("modelo", "version", "almacenamiento", "ram", "pantalla", "capacidad")) {
            Optional<Fila> fila = filas.stream().filter(f -> f.clave().equals(clave)).findFirst();
            if (fila.isEmpty()) {
                continue;
            }
            List<String> conocidos = fila.get().celdas().stream().filter(Celda::disponible)
                    .map(c -> c.valor().toLowerCase(Locale.ROOT)).distinct().toList();
            boolean versionFaltante = "version".equals(clave)
                    && fila.get().celdas().stream().anyMatch(c -> !c.disponible());
            if (conocidos.size() > 1 || versionFaltante && !conocidos.isEmpty()) {
                avisos.add("No son el mismo modelo o variante: cambia " + fila.get().etiqueta().toLowerCase(Locale.ROOT)
                        + ". Revise esa fila antes de decidir por el precio.");
                return false;
            }
        }
        return true;
    }

    private static Celda envio(Oferta oferta) {
        if (oferta.getCostoEnvio() == null) {
            return new Celda("No disponible (la tienda no lo informa)", null, false, null);
        }
        if (oferta.getCostoEnvio().signum() == 0) {
            return new Celda("Gratis", BigDecimal.ZERO, true, null);
        }
        return new Celda(pesos(oferta.getCostoEnvio(), oferta.getMoneda()), oferta.getCostoEnvio(), true, null);
    }

    private static Celda disponibilidad(Oferta oferta) {
        for (String detalle : oferta.listaDetalles()) {
            if (AGOTADO.matcher(detalle).find()) {
                return new Celda("Agotado", null, true, Especificacion.FUENTE_TIENDA);
            }
            if (EN_EXISTENCIA.matcher(detalle).find()) {
                return new Celda("En existencia", null, true, Especificacion.FUENTE_TIENDA);
            }
        }
        return Celda.falta();
    }

    private static Celda valoracion(Oferta oferta) {
        if (oferta.getCalificacion() == null || oferta.getTotalResenas() == null || oferta.getTotalResenas() <= 0) {
            return Celda.falta();
        }
        String estrellas = oferta.getCalificacion().stripTrailingZeros().toPlainString().replace('.', ',');
        return new Celda(estrellas + " de 5 (" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO"))
                .format(oferta.getTotalResenas()) + " opiniones)", oferta.getCalificacion(), true, null);
    }

    static String pesos(BigDecimal valor, String moneda) {
        NumberFormat formato = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
        formato.setMaximumFractionDigits("COP".equals(moneda) ? 0 : 2);
        formato.setMinimumFractionDigits(0);
        return ("COP".equals(moneda) ? "$ " : moneda + " ") + formato.format(valor);
    }
}
