package co.edu.unicesar.marketradar.service.mayorista;

import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.adapter.alibaba.ApiShException;
import co.edu.unicesar.marketradar.adapter.alibaba.ClienteAlibabaApiSh;
import co.edu.unicesar.marketradar.adapter.alibaba.LectorRespuestasAlibaba;
import co.edu.unicesar.marketradar.config.PropiedadesMayoristas;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import co.edu.unicesar.marketradar.domain.mayorista.CalculadoraMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.OfertaMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.UnidadVenta;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ServicioNoDisponibleException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Comparador al por mayor con Alibaba (API.SH). Busca, quita repetidos y resultados que no tienen que ver, marca los
 * anuncios, consulta el detalle cuando los precios de la búsqueda no cuadran y calcula el subtotal de la mercancía para
 * la cantidad pedida. Solo entran al orden por precio las ofertas con un precio claro para esa cantidad.
 *
 * <p>Nunca calcula envío, impuestos, disponibilidad para Colombia ni fechas de entrega: Alibaba no los da en estas
 * respuestas. La compra se hace en Alibaba con el enlace original.</p>
 */
@Service
public class ServicioMayoristas {

    /** Texto que acompaña siempre al subtotal. */
    public static final String NOTA_SUBTOTAL = "Subtotal de mercancía; envío e impuestos pendientes";

    static final long CANTIDAD_MAXIMA = 10_000_000L;
    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final Pattern ID_ALIBABA = Pattern.compile("\\d{6,20}");
    private static final Pattern URL_ALIBABA = Pattern.compile(
            "https://(?:[a-z0-9-]+\\.)*alibaba\\.com/[^\\s]{1,400}", Pattern.CASE_INSENSITIVE);
    private static final Set<String> PALABRAS_VACIAS = Set.of("de", "la", "el", "los", "las", "para", "con", "por",
            "del", "en", "y", "un", "una", "the", "for", "and", "with", "of");

    private final ClienteAlibabaApiSh cliente;
    private final PropiedadesMayoristas propiedades;
    private final ConversorMoneda conversor;
    private final Clock reloj;
    private final Cache<String, LectorRespuestasAlibaba.Pagina> busquedas;
    private final Cache<String, OfertaMayorista> detalles;

    private LocalDate diaConsumo;
    private int consultasHoy;
    private Instant bloqueadoHasta = Instant.EPOCH;

    /**
     * @param cliente cliente de API.SH
     * @param propiedades límites y caché
     * @param conversor tasas de cambio, para el valor aproximado en pesos
     */
    @Autowired
    public ServicioMayoristas(ClienteAlibabaApiSh cliente, PropiedadesMayoristas propiedades, ConversorMoneda conversor) {
        this(cliente, propiedades, conversor, Clock.systemUTC());
    }

    ServicioMayoristas(ClienteAlibabaApiSh cliente, PropiedadesMayoristas propiedades, ConversorMoneda conversor,
                       Clock reloj) {
        this.cliente = cliente;
        this.propiedades = propiedades;
        this.conversor = conversor;
        this.reloj = reloj;
        Duration vigencia = Duration.ofHours(Math.max(1, propiedades.cacheHoras()));
        this.busquedas = Caffeine.newBuilder().maximumSize(300).expireAfterWrite(vigencia).build();
        this.detalles = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(vigencia).build();
    }

    /**
     * Una oferta con su cálculo.
     *
     * @param oferta publicación (la del detalle, si se consultó)
     * @param calculo precio para la cantidad pedida
     * @param posicion lugar en el orden por subtotal, o {@code null} si queda fuera del orden
     * @param motivoFuera por qué no entra al orden, o {@code null}
     * @param consultoDetalle si se consultó el detalle para aclarar el precio
     * @param aproximadoPesos subtotal aproximado en pesos, o {@code null}
     * @param tasa tasa con que se calculó el valor en pesos, o {@code null}
     */
    public record Resultado(OfertaMayorista oferta, CalculadoraMayorista.Calculo calculo, Integer posicion,
                            String motivoFuera, boolean consultoDetalle, BigDecimal aproximadoPesos, TasaCambio tasa) {
    }

    /**
     * Respuesta de una búsqueda.
     *
     * @param termino lo que se buscó
     * @param pagina página
     * @param siguientePagina siguiente página, o {@code null} si no hay más (o se llegó al tope)
     * @param cantidad cantidad pedida
     * @param unidad unidad pedida
     * @param resultados ofertas: primero las ordenadas por subtotal, luego las que quedan fuera
     * @param descartados cuántos resultados se quitaron por repetidos o por no tener que ver
     * @param avisos avisos para la persona
     * @param consultadoEn cuándo se consultó Alibaba
     */
    public record Busqueda(String termino, int pagina, Integer siguientePagina, long cantidad, UnidadVenta unidad,
                           List<Resultado> resultados, int descartados, List<String> avisos, Instant consultadoEn) {
    }

    /** @return {@code true} si hay clave de API.SH */
    public boolean estaConfigurado() {
        return propiedades.configurado() && cliente.estaConfigurado();
    }

    /**
     * Busca en Alibaba y calcula el subtotal de la mercancía para la cantidad pedida.
     *
     * @param termino producto a buscar
     * @param cantidad cantidad deseada
     * @param unidad unidad en que la pide (piezas, pares, paquetes)
     * @param pagina página, desde 1
     * @return resultados
     */
    public Busqueda buscar(String termino, long cantidad, UnidadVenta unidad, int pagina) {
        String limpio = limpiarTermino(termino);
        validarCantidad(cantidad, unidad);
        if (pagina < 1 || pagina > propiedades.paginasMaximas()) {
            throw new ValidacionException("Se pueden ver hasta " + propiedades.paginasMaximas() + " páginas por búsqueda.");
        }
        exigirConfiguracion();
        String clave = limpio.toLowerCase(Locale.ROOT) + "|" + pagina;
        LectorRespuestasAlibaba.Pagina leida = busquedas.getIfPresent(clave);
        if (leida == null) {
            leida = consultar(() -> cliente.buscar(limpio, pagina));
            busquedas.put(clave, leida);
        }

        List<String> avisos = new ArrayList<>();
        List<OfertaMayorista> unicas = sinRepetidos(leida.ofertas());
        List<OfertaMayorista> relevantes = relevantes(limpio, unicas);
        if (!unicas.isEmpty() && !algunaCoincide(limpio, unicas)) {
            avisos.add("Ningún resultado menciona lo que buscó. Alibaba funciona mejor con palabras en inglés.");
        } else if (relevantes == unicas && !todasCoinciden(limpio, unicas)) {
            avisos.add("Algunos resultados podrían no ser lo que busca. Revise el título antes de comprar.");
        }
        int descartados = leida.ofertas().size() - relevantes.size();

        int detallesDisponibles = propiedades.detallesPorBusqueda();
        List<Resultado> resultados = new ArrayList<>();
        for (OfertaMayorista oferta : relevantes) {
            boolean consultoDetalle = false;
            OfertaMayorista usada = oferta;
            String contradiccion = null;
            List<String> problemas = CalculadoraMayorista.discrepancias(oferta);
            if (!problemas.isEmpty() && detallesDisponibles > 0) {
                Optional<OfertaMayorista> detalle = detalleSiSePuede(oferta.id(), oferta.url());
                if (detalle.isPresent()) {
                    detallesDisponibles--;
                    consultoDetalle = true;
                    if (CalculadoraMayorista.seContradicen(oferta, detalle.get())) {
                        contradiccion = "La búsqueda y la ficha del producto muestran escalas de precio distintas.";
                    }
                    usada = combinar(oferta, detalle.get());
                }
            }
            CalculadoraMayorista.Calculo calculo = CalculadoraMayorista.calcular(usada, cantidad, unidad, null);
            String fuera = contradiccion != null ? contradiccion : calculo.aplicable() ? null : calculo.motivo();
            resultados.add(new Resultado(usada, calculo, null, fuera, consultoDetalle, null, null));
        }
        if (detallesDisponibles == 0 && resultados.stream().anyMatch(r -> r.motivoFuera() != null && !r.consultoDetalle())) {
            avisos.add("Algunas ofertas no se aclararon con su ficha para no gastar consultas. Ábralas para ver más.");
        }
        long patrocinados = resultados.stream().filter(r -> r.oferta().patrocinado()).count();
        if (patrocinados > 0) {
            avisos.add(patrocinados == 1 ? "1 resultado es un anuncio pagado y se marca como tal."
                    : patrocinados + " resultados son anuncios pagados y se marcan como tales.");
        }
        List<Resultado> ordenados = ordenar(resultados, avisos);
        Integer siguiente = leida.siguientePagina() != null && leida.siguientePagina() <= propiedades.paginasMaximas()
                ? leida.siguientePagina() : null;
        Instant consultado = leida.actualizadoEn() != null ? leida.actualizadoEn().toInstant() : reloj.instant();
        return new Busqueda(limpio, pagina, siguiente, cantidad, unidad, ordenados, descartados, avisos, consultado);
    }

    /**
     * Detalle de una publicación con el cálculo para una cantidad y una variante.
     *
     * @param referencia enlace de Alibaba o identificador numérico
     * @param cantidad cantidad deseada
     * @param unidad unidad
     * @param varianteId variante elegida, o {@code null}
     * @return resultado
     */
    public Resultado detalle(String referencia, long cantidad, UnidadVenta unidad, String varianteId) {
        String ref = referencia == null ? "" : referencia.strip();
        if (!ID_ALIBABA.matcher(ref).matches() && !URL_ALIBABA.matcher(ref).matches()) {
            throw new ValidacionException("Indique el enlace de Alibaba o el número del producto.");
        }
        validarCantidad(cantidad, unidad);
        if (varianteId != null && (varianteId.isBlank() || varianteId.length() > 60)) {
            varianteId = null;
        }
        exigirConfiguracion();
        String id = ID_ALIBABA.matcher(ref).matches() ? ref : idDeUrl(ref);
        OfertaMayorista oferta = id != null ? detalles.getIfPresent(id) : null;
        if (oferta == null) {
            try {
                oferta = consultar(() -> LectorRespuestasAlibaba.leerProducto(cliente.producto(ref)));
            } catch (ApiShException e) {
                if (e.getTipo() == ApiShException.Tipo.NO_ENCONTRADO) {
                    throw new RecursoNoEncontradoException("El producto ya no está publicado en Alibaba.");
                }
                throw e;
            }
            detalles.put(oferta.id(), oferta);
        }
        CalculadoraMayorista.Calculo calculo = CalculadoraMayorista.calcular(oferta, cantidad, unidad, varianteId);
        return conPesos(new Resultado(oferta, calculo, null, calculo.aplicable() ? null : calculo.motivo(), true,
                null, null));
    }

    private Optional<OfertaMayorista> detalleSiSePuede(String id, String url) {
        OfertaMayorista guardado = detalles.getIfPresent(id);
        if (guardado != null) {
            return Optional.of(guardado);
        }
        try {
            OfertaMayorista leido = consultar(() -> LectorRespuestasAlibaba.leerProducto(cliente.producto(url)));
            detalles.put(id, leido);
            return Optional.of(leido);
        } catch (ApiShException | ServicioNoDisponibleException e) {
            // Sin detalle, la oferta sigue con lo que dio la búsqueda y queda fuera del orden si el precio no es claro.
            return Optional.empty();
        }
    }

    /** El detalle manda en precios y variantes; la búsqueda aporta lo que el detalle no trae. */
    static OfertaMayorista combinar(OfertaMayorista busqueda, OfertaMayorista detalle) {
        return new OfertaMayorista(busqueda.id(), detalle.titulo() != null ? detalle.titulo() : busqueda.titulo(),
                busqueda.url(), detalle.imagen() != null ? detalle.imagen() : busqueda.imagen(),
                detalle.proveedor() != null ? detalle.proveedor() : busqueda.proveedor(),
                detalle.paisProveedor() != null ? detalle.paisProveedor() : busqueda.paisProveedor(),
                detalle.aniosProveedor() != null ? detalle.aniosProveedor() : busqueda.aniosProveedor(),
                busqueda.proveedorVerificado() || detalle.proveedorVerificado(), busqueda.patrocinado(),
                detalle.pedidoMinimo() != null ? detalle.pedidoMinimo() : busqueda.pedidoMinimo(),
                detalle.pedidoMinimo() != null ? detalle.unidadPedidoMinimo() : busqueda.unidadPedidoMinimo(),
                detalle.unidad() != UnidadVenta.DESCONOCIDA ? detalle.unidad() : busqueda.unidad(),
                detalle.presentacion() != null ? detalle.presentacion() : busqueda.presentacion(),
                detalle.escalas().isEmpty() ? busqueda.escalas() : detalle.escalas(),
                detalle.precioMinimo() != null ? detalle.precioMinimo() : busqueda.precioMinimo(),
                detalle.precioMaximo() != null ? detalle.precioMaximo() : busqueda.precioMaximo(),
                detalle.moneda() != null ? detalle.moneda() : busqueda.moneda(),
                detalle.textoPrecio() != null ? detalle.textoPrecio() : busqueda.textoPrecio(), detalle.variantes(),
                detalle.actualizadoEn() != null ? detalle.actualizadoEn() : busqueda.actualizadoEn(), "detalle");
    }

    /** Ordena por subtotal las que tienen precio claro y la misma moneda; las demás van después, con su motivo. */
    private List<Resultado> ordenar(List<Resultado> resultados, List<String> avisos) {
        Map<String, Long> porMoneda = new LinkedHashMap<>();
        resultados.stream().filter(r -> r.motivoFuera() == null)
                .forEach(r -> porMoneda.merge(String.valueOf(r.oferta().moneda()), 1L, Long::sum));
        String monedaPrincipal = porMoneda.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
        List<Resultado> enOrden = new ArrayList<>();
        List<Resultado> fuera = new ArrayList<>();
        for (Resultado r : resultados) {
            if (r.motivoFuera() == null && String.valueOf(r.oferta().moneda()).equals(monedaPrincipal)
                    && r.oferta().moneda() != null) {
                enOrden.add(r);
            } else if (r.motivoFuera() == null) {
                fuera.add(new Resultado(r.oferta(), r.calculo(), null, "Está en otra moneda ("
                        + (r.oferta().moneda() == null ? "sin moneda" : r.oferta().moneda())
                        + ") y no se mezcla con las demás.", r.consultoDetalle(), null, null));
            } else {
                fuera.add(r);
            }
        }
        enOrden.sort(Comparator.comparing((Resultado r) -> r.calculo().subtotal())
                .thenComparing(r -> r.oferta().patrocinado()));
        List<Resultado> todos = new ArrayList<>();
        for (int i = 0; i < enOrden.size(); i++) {
            Resultado r = enOrden.get(i);
            todos.add(conPesos(new Resultado(r.oferta(), r.calculo(), i + 1, null, r.consultoDetalle(), null, null)));
        }
        fuera.forEach(r -> todos.add(conPesos(r)));
        if (!fuera.isEmpty()) {
            avisos.add(fuera.size() == 1 ? "1 oferta queda fuera del orden porque su precio no es claro para esa cantidad."
                    : fuera.size() + " ofertas quedan fuera del orden porque su precio no es claro para esa cantidad.");
        }
        return todos;
    }

    private Resultado conPesos(Resultado r) {
        if (r.calculo().subtotal() == null || r.oferta().moneda() == null
                || r.oferta().moneda().equals(conversor.getMonedaDestino())) {
            return r;
        }
        Optional<TasaCambio> tasa = conversor.tasaPara(r.oferta().moneda());
        return tasa.map(t -> new Resultado(r.oferta(), r.calculo(), r.posicion(), r.motivoFuera(), r.consultoDetalle(),
                ConversorMoneda.convertir(r.calculo().subtotal(), t), t)).orElse(r);
    }

    /** Quita repetidos: primero por id y después por título y proveedor iguales. */
    static List<OfertaMayorista> sinRepetidos(List<OfertaMayorista> ofertas) {
        Set<String> ids = new HashSet<>();
        Set<String> titulos = new HashSet<>();
        List<OfertaMayorista> unicas = new ArrayList<>();
        for (OfertaMayorista oferta : ofertas) {
            String porTitulo = normalizar(oferta.titulo()) + "|" + normalizar(oferta.proveedor());
            if (ids.add(oferta.id()) && titulos.add(porTitulo)) {
                unicas.add(oferta);
            }
        }
        return unicas;
    }

    /**
     * Quita los resultados que no mencionan ninguna palabra de la búsqueda, solo si al menos un 30 % sí lo hace (si la
     * persona escribió en español, Alibaba responde en inglés y casi nada coincide: en ese caso no se quita nada).
     */
    static List<OfertaMayorista> relevantes(String termino, List<OfertaMayorista> ofertas) {
        List<String> palabras = palabras(termino);
        if (palabras.isEmpty() || ofertas.isEmpty()) {
            return ofertas;
        }
        List<OfertaMayorista> coinciden = ofertas.stream().filter(o -> coincide(palabras, o.titulo())).toList();
        return coinciden.size() * 10 >= ofertas.size() * 3 ? coinciden : ofertas;
    }

    private static boolean algunaCoincide(String termino, List<OfertaMayorista> ofertas) {
        List<String> palabras = palabras(termino);
        return palabras.isEmpty() || ofertas.stream().anyMatch(o -> coincide(palabras, o.titulo()));
    }

    private static boolean todasCoinciden(String termino, List<OfertaMayorista> ofertas) {
        List<String> palabras = palabras(termino);
        return palabras.isEmpty() || ofertas.stream().allMatch(o -> coincide(palabras, o.titulo()));
    }

    private static boolean coincide(List<String> palabras, String titulo) {
        String normal = " " + normalizar(titulo) + " ";
        return palabras.stream().anyMatch(p -> normal.contains(" " + p));
    }

    private static List<String> palabras(String termino) {
        List<String> palabras = new ArrayList<>();
        for (String palabra : normalizar(termino).split(" ")) {
            if (palabra.length() >= 3 && !PALABRAS_VACIAS.contains(palabra)) {
                // Sin la "s" final, para que "cables" coincida con "cable".
                palabras.add(palabra.length() > 4 && palabra.endsWith("s") ? palabra.substring(0, palabra.length() - 1)
                        : palabra);
            }
        }
        return palabras;
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").strip();
    }

    private static String idDeUrl(String url) {
        java.util.regex.Matcher m = Pattern.compile("(\\d{6,20})\\.html").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    static String limpiarTermino(String termino) {
        String limpio = termino == null ? "" : termino.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").strip();
        if (limpio.length() < 2 || limpio.length() > 100) {
            throw new ValidacionException("Escriba qué producto busca (entre 2 y 100 letras).");
        }
        return limpio;
    }

    private static void validarCantidad(long cantidad, UnidadVenta unidad) {
        if (cantidad < 1 || cantidad > CANTIDAD_MAXIMA) {
            throw new ValidacionException("La cantidad debe estar entre 1 y " + CANTIDAD_MAXIMA + ".");
        }
        if (unidad == null || unidad == UnidadVenta.DESCONOCIDA) {
            throw new ValidacionException("Elija la unidad: piezas, pares, paquetes...");
        }
    }

    private void exigirConfiguracion() {
        if (!estaConfigurado()) {
            throw new ServicioNoDisponibleException("La búsqueda al por mayor no está configurada todavía en este "
                    + "servidor (falta la clave de API.SH).");
        }
    }

    /** Aplica el tope diario y la espera de {@code retry-after}, y traduce los errores a mensajes para la persona. */
    private <T> T consultar(java.util.function.Supplier<T> consulta) {
        synchronized (this) {
            Instant ahora = reloj.instant();
            if (ahora.isBefore(bloqueadoHasta)) {
                throw new ServicioNoDisponibleException("Alibaba está recibiendo muchas consultas. Intente de nuevo en "
                        + "unos minutos.");
            }
            LocalDate hoy = LocalDate.ofInstant(ahora, COLOMBIA);
            if (!hoy.equals(diaConsumo)) {
                diaConsumo = hoy;
                consultasHoy = 0;
            }
            if (consultasHoy >= propiedades.consultasPorDia()) {
                throw new ServicioNoDisponibleException("Se alcanzó el número de consultas al por mayor de hoy. "
                        + "Vuelva a intentarlo mañana.");
            }
            consultasHoy++;
        }
        try {
            return consulta.get();
        } catch (ApiShException e) {
            switch (e.getTipo()) {
                case LIMITE -> {
                    synchronized (this) {
                        bloqueadoHasta = reloj.instant().plusSeconds(
                                e.getReintentarEnSegundos() == null ? 60 : e.getReintentarEnSegundos());
                    }
                    throw new ServicioNoDisponibleException("Alibaba está recibiendo muchas consultas. Intente de "
                            + "nuevo en unos minutos.");
                }
                case SIN_CREDITOS, CLAVE_INVALIDA -> throw new ServicioNoDisponibleException(
                        "La búsqueda al por mayor no está disponible ahora. El administrador debe revisar la cuenta de "
                                + "API.SH.");
                case NO_DISPONIBLE, RESPUESTA_INVALIDA -> throw new ServicioNoDisponibleException(
                        "Alibaba no respondió bien. Intente de nuevo en unos minutos.");
                case PARAMETRO_INVALIDO -> throw new ValidacionException("Alibaba no aceptó la búsqueda. Pruebe con otras palabras.");
                default -> throw e;
            }
        }
    }

    /** @return consultas hechas hoy (para pruebas y para el administrador) */
    synchronized int consultasHoy() {
        return consultasHoy;
    }
}
