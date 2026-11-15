package co.edu.unicesar.marketradar.service.mayorista;

import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.adapter.alibaba.ApiShException;
import co.edu.unicesar.marketradar.adapter.alibaba.ClienteAlibabaApiSh;
import co.edu.unicesar.marketradar.adapter.alibaba.LectorRespuestasAlibaba;
import co.edu.unicesar.marketradar.config.PropiedadesMayoristas;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import co.edu.unicesar.marketradar.domain.mayorista.CalculadoraMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.OfertaMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.RelevanciaMayorista;
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
    /** Tiempo total para las consultas externas de una búsqueda; después se devuelve lo que haya. */
    static final Duration PRESUPUESTO_BUSQUEDA = Duration.ofSeconds(45);
    /** Fallos seguidos al pedir fichas tras los que se deja de pedir más en esa búsqueda. */
    static final int MAX_FALLOS_SEGUIDOS = 2;
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
    /** Lo que mostró la búsqueda de cada oferta, para contrastarlo con la ficha cuando se abre el detalle. */
    private final Cache<String, OfertaMayorista> indice;

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
        this.indice = Caffeine.newBuilder().maximumSize(3000).expireAfterWrite(vigencia).build();
    }

    /**
     * Una oferta con su cálculo.
     *
     * @param oferta publicación (la de la ficha, si se consultó)
     * @param calculo precio para la cantidad pedida
     * @param posicion lugar en el orden por subtotal (las empatadas comparten lugar), o {@code null} si queda fuera
     * @param motivoFuera por qué no entra al orden por precio, o {@code null}
     * @param consultoDetalle si se usó la ficha del producto
     * @param aproximadoPesos subtotal aproximado en pesos, o {@code null}
     * @param tasa tasa con que se calculó el valor en pesos, o {@code null}
     * @param relevancia si es lo que se buscó, si es dudosa, o {@code null} si no se evaluó (detalle directo)
     * @param motivoRelevancia por qué es dudosa, o {@code null}
     * @param empate si comparte el mismo subtotal con otra oferta del orden
     */
    public record Resultado(OfertaMayorista oferta, CalculadoraMayorista.Calculo calculo, Integer posicion,
                            String motivoFuera, boolean consultoDetalle, BigDecimal aproximadoPesos, TasaCambio tasa,
                            RelevanciaMayorista.Nivel relevancia, String motivoRelevancia, boolean empate) {

        /** Sin datos de relevancia ni empate. */
        public Resultado(OfertaMayorista oferta, CalculadoraMayorista.Calculo calculo, Integer posicion,
                         String motivoFuera, boolean consultoDetalle, BigDecimal aproximadoPesos, TasaCambio tasa) {
            this(oferta, calculo, posicion, motivoFuera, consultoDetalle, aproximadoPesos, tasa, null, null, false);
        }

        Resultado conPosicion(Integer nuevaPosicion, boolean nuevoEmpate) {
            return new Resultado(oferta, calculo, nuevaPosicion, motivoFuera, consultoDetalle, aproximadoPesos, tasa,
                    relevancia, motivoRelevancia, nuevoEmpate);
        }

        Resultado conMotivo(String motivo) {
            return new Resultado(oferta, calculo, null, motivo, consultoDetalle, aproximadoPesos, tasa, relevancia,
                    motivoRelevancia, false);
        }

        Resultado conPesos(BigDecimal valor, TasaCambio nuevaTasa) {
            return new Resultado(oferta, calculo, posicion, motivoFuera, consultoDetalle, valor, nuevaTasa, relevancia,
                    motivoRelevancia, empate);
        }
    }

    /**
     * Respuesta de una búsqueda: todas las páginas cargadas hasta {@code pagina}, sin repetidos y en un solo orden.
     *
     * @param termino lo que se buscó
     * @param pagina última página cargada
     * @param siguientePagina siguiente página, o {@code null} si no hay más (o se llegó al tope)
     * @param cantidad cantidad pedida
     * @param unidad unidad pedida
     * @param resultados ofertas: primero las ordenadas por subtotal, luego las que quedan fuera del orden y al final
     *                   las coincidencias dudosas
     * @param descartados cuántos resultados se quitaron por repetidos o por ser de otro tipo o material
     * @param avisos avisos para la persona
     * @param consultadoEn cuándo se consultó Alibaba
     * @param comparables cuántas ofertas entraron al orden por precio
     * @param parcial si faltó algo por errores, límites o tiempo
     */
    public record Busqueda(String termino, int pagina, Integer siguientePagina, long cantidad, UnidadVenta unidad,
                           List<Resultado> resultados, int descartados, List<String> avisos, Instant consultadoEn,
                           int comparables, boolean parcial) {
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
     * @param pagina hasta qué página cargar, desde 1
     * @return resultados
     */
    public Busqueda buscar(String termino, long cantidad, UnidadVenta unidad, int pagina) {
        return buscar(termino, cantidad, unidad, pagina, Map.of());
    }

    /**
     * Carga las páginas 1 a {@code hastaPagina} (las ya consultadas salen de la caché y no gastan créditos), quita los
     * repetidos, separa lo que no es lo buscado y ordena por subtotal <b>todas</b> las ofertas cargadas.
     *
     * @param termino producto a buscar
     * @param cantidad cantidad deseada
     * @param unidad unidad
     * @param hastaPagina última página a cargar
     * @param variantes variante elegida por oferta (id de la oferta → id de la variante)
     * @return resultados
     */
    public Busqueda buscar(String termino, long cantidad, UnidadVenta unidad, int hastaPagina,
                           Map<String, String> variantes) {
        String limpio = limpiarTermino(termino);
        validarCantidad(cantidad, unidad);
        if (hastaPagina < 1 || hastaPagina > propiedades.paginasMaximas()) {
            throw new ValidacionException("Se pueden ver hasta " + propiedades.paginasMaximas() + " páginas por búsqueda.");
        }
        exigirConfiguracion();
        Instant limite = reloj.instant().plus(PRESUPUESTO_BUSQUEDA);
        List<String> avisos = new ArrayList<>();
        boolean parcial = false;

        List<OfertaMayorista> todas = new ArrayList<>();
        Integer siguiente = null;
        Instant consultado = null;
        int cargada = 0;
        for (int numero = 1; numero <= hastaPagina; numero++) {
            String clave = limpio.toLowerCase(Locale.ROOT) + "|" + numero;
            LectorRespuestasAlibaba.Pagina leida = busquedas.getIfPresent(clave);
            if (leida == null) {
                if (numero > 1 && reloj.instant().isAfter(limite)) {
                    avisos.add("Se acabó el tiempo de la búsqueda antes de cargar la página " + numero + ".");
                    parcial = true;
                    // Se ofrece otra vez esa página con "Ver más".
                    siguiente = numero;
                    break;
                }
                int pedida = numero;
                try {
                    leida = consultarConReintento(() -> cliente.buscar(limpio, pedida), limite);
                } catch (ServicioNoDisponibleException e) {
                    if (numero == 1) {
                        throw e;
                    }
                    avisos.add("No se pudo cargar la página " + numero + ": " + e.getMessage()
                            + " Se muestran las páginas anteriores.");
                    parcial = true;
                    siguiente = numero;
                    break;
                }
                busquedas.put(clave, leida);
            }
            cargada = numero;
            todas.addAll(leida.ofertas());
            leida.ofertas().forEach(o -> indice.put(o.id(), o));
            siguiente = leida.siguientePagina();
            if (consultado == null && leida.actualizadoEn() != null) {
                consultado = leida.actualizadoEn().toInstant();
            }
            if (siguiente == null) {
                break;
            }
        }

        List<OfertaMayorista> unicas = sinRepetidos(todas);
        int repetidos = todas.size() - unicas.size();
        int incompatibles = 0;
        List<OfertaMayorista> evaluadas = new ArrayList<>();
        Map<String, RelevanciaMayorista.Evaluacion> relevancias = new LinkedHashMap<>();
        for (OfertaMayorista oferta : unicas) {
            RelevanciaMayorista.Evaluacion evaluacion = RelevanciaMayorista.evaluar(limpio, oferta.titulo());
            if (evaluacion.nivel() == RelevanciaMayorista.Nivel.INCOMPATIBLE) {
                incompatibles++;
                continue;
            }
            relevancias.put(oferta.id(), evaluacion);
            evaluadas.add(oferta);
        }
        if (incompatibles > 0) {
            avisos.add(incompatibles == 1 ? "Se quitó 1 resultado que es otro tipo de producto o de otro material."
                    : "Se quitaron " + incompatibles + " resultados que son otro tipo de producto o de otro material.");
        }
        if (repetidos > 0) {
            avisos.add(repetidos == 1 ? "Se quitó 1 resultado repetido." : "Se quitaron " + repetidos
                    + " resultados repetidos.");
        }

        int detallesDisponibles = propiedades.detallesPorBusqueda();
        int fallosSeguidos = 0;
        boolean detallesCortados = false;
        List<Resultado> resultados = new ArrayList<>();
        for (OfertaMayorista oferta : evaluadas) {
            OfertaMayorista detalle = detalles.getIfPresent(oferta.id());
            boolean necesita = !CalculadoraMayorista.discrepancias(oferta).isEmpty()
                    || variantes.containsKey(oferta.id());
            if (detalle == null && necesita) {
                if (detallesDisponibles <= 0 || fallosSeguidos >= MAX_FALLOS_SEGUIDOS
                        || reloj.instant().isAfter(limite)) {
                    detallesCortados = true;
                } else {
                    // Cada intento externo cuenta, salga bien o mal; leer de la caché no cuenta.
                    detallesDisponibles--;
                    try {
                        detalle = consultar(() -> LectorRespuestasAlibaba.leerProducto(cliente.producto(oferta.url())));
                        detalles.put(oferta.id(), detalle);
                        fallosSeguidos = 0;
                    } catch (ApiShException | ServicioNoDisponibleException e) {
                        fallosSeguidos++;
                        parcial = true;
                    }
                }
            }
            Resultado resultado = evaluar(oferta, detalle, cantidad, unidad, variantes.get(oferta.id()),
                    relevancias.get(oferta.id()));
            List<String> sinAclarar = detalle == null ? contradiccionesPropias(oferta) : List.of();
            if (!sinAclarar.isEmpty() && resultado.motivoFuera() == null) {
                // La búsqueda se contradice sola y no hubo ficha que lo aclarara: no se adivina cuál precio vale.
                String motivo = "No pudimos confirmar el precio con la ficha del producto: " + String.join("; ", sinAclarar)
                        + ".";
                resultado = new Resultado(resultado.oferta(), new CalculadoraMayorista.Calculo(
                        CalculadoraMayorista.Estado.PRECIO_AMBIGUO, motivo, resultado.calculo().cantidadCalculada(),
                        resultado.calculo().unidad(), null, null, null, resultado.calculo().ajustes()), null, motivo,
                        false, null, null, resultado.relevancia(), resultado.motivoRelevancia(), false);
            }
            resultados.add(resultado);
        }
        if (fallosSeguidos >= MAX_FALLOS_SEGUIDOS) {
            avisos.add("Alibaba falló varias veces seguidas al pedir fichas de producto: se dejaron de pedir para no "
                    + "gastar consultas. Los precios sin aclarar quedan fuera del orden.");
        } else if (detallesCortados) {
            avisos.add("Algunas ofertas no se aclararon con su ficha para no gastar consultas o tiempo. Ábralas para "
                    + "ver más.");
        }
        long patrocinados = resultados.stream().filter(r -> r.oferta().patrocinado()).count();
        if (patrocinados > 0) {
            avisos.add(patrocinados == 1 ? "1 resultado es un anuncio pagado y se marca como tal."
                    : patrocinados + " resultados son anuncios pagados y se marcan como tales.");
        }
        List<Resultado> ordenados = ordenar(resultados, avisos);
        int comparables = (int) ordenados.stream().filter(r -> r.posicion() != null).count();
        // Una ficha que falló no impide cargar más páginas; una página que falló se puede pedir otra vez.
        Integer siguienteValida = siguiente != null && siguiente <= propiedades.paginasMaximas() ? siguiente : null;
        return new Busqueda(limpio, Math.max(cargada, 1), siguienteValida, cantidad, unidad, ordenados,
                repetidos + incompatibles, avisos, consultado != null ? consultado : reloj.instant(), comparables,
                parcial);
    }

    /** Discrepancias de la búsqueda que son contradicciones (no solo datos que faltan). */
    static List<String> contradiccionesPropias(OfertaMayorista oferta) {
        return CalculadoraMayorista.discrepancias(oferta).stream()
                .filter(d -> !d.equals("sin escalas de precio") && !d.equals("sin pedido mínimo")
                        && !d.equals("unidad desconocida"))
                .toList();
    }

    /** Calcula una oferta con lo que se sabe: búsqueda y, si la hay, ficha conciliada. */
    private static Resultado evaluar(OfertaMayorista busqueda, OfertaMayorista detalle, long cantidad,
                                     UnidadVenta unidad, String variante, RelevanciaMayorista.Evaluacion relevancia) {
        OfertaMayorista usada = busqueda;
        String contradiccion = null;
        if (detalle != null) {
            CalculadoraMayorista.Conciliacion conciliacion = CalculadoraMayorista.conciliar(busqueda, detalle);
            usada = conciliacion.oferta();
            if (conciliacion.hayContradiccion()) {
                contradiccion = "Los datos no cuadran: " + String.join("; ", conciliacion.contradicciones())
                        + ". No la ponemos en el orden por precio hasta que el proveedor lo aclare.";
            }
        }
        CalculadoraMayorista.Calculo calculo = CalculadoraMayorista.calcular(usada, cantidad, unidad, variante);
        String fuera = contradiccion != null ? contradiccion : calculo.aplicable() ? null : calculo.motivo();
        if (contradiccion != null) {
            // No se muestra un subtotal que la ficha contradice.
            calculo = new CalculadoraMayorista.Calculo(CalculadoraMayorista.Estado.PRECIO_AMBIGUO, contradiccion,
                    calculo.cantidadCalculada(), calculo.unidad(), null, null, null, calculo.ajustes());
        }
        return new Resultado(usada, calculo, null, fuera, detalle != null, null, null,
                relevancia == null ? null : relevancia.nivel(), relevancia == null ? null : relevancia.motivo(), false);
    }

    /**
     * Detalle de una publicación con el cálculo para una cantidad y una variante. Usa las mismas reglas que la lista:
     * si la búsqueda mostró otra cosa, se contrasta con la ficha.
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
        OfertaMayorista deBusqueda = indice.getIfPresent(oferta.id());
        Resultado resultado;
        if (deBusqueda != null) {
            resultado = evaluar(deBusqueda, oferta, cantidad, unidad, varianteId, null);
        } else {
            CalculadoraMayorista.Conciliacion sola = CalculadoraMayorista.conciliar(null, oferta);
            CalculadoraMayorista.Calculo calculo = CalculadoraMayorista.calcular(oferta, cantidad, unidad, varianteId);
            if (sola.hayContradiccion()) {
                String motivo = "Los datos no cuadran: " + String.join("; ", sola.contradicciones()) + ".";
                calculo = new CalculadoraMayorista.Calculo(CalculadoraMayorista.Estado.PRECIO_AMBIGUO, motivo,
                        calculo.cantidadCalculada(), calculo.unidad(), null, null, null, calculo.ajustes());
            }
            resultado = new Resultado(oferta, calculo, null, calculo.aplicable() ? null : calculo.motivo(), true,
                    null, null);
        }
        return conPesos(resultado);
    }

    /**
     * Ordena por subtotal las que coinciden con lo buscado, tienen precio claro y la moneda más común. Las que tienen el
     * mismo subtotal comparten puesto y quedan marcadas como empate. Después van las que quedan fuera del orden y, al
     * final, las coincidencias dudosas.
     */
    private List<Resultado> ordenar(List<Resultado> resultados, List<String> avisos) {
        Map<String, Long> porMoneda = new LinkedHashMap<>();
        resultados.stream().filter(r -> r.motivoFuera() == null && r.oferta().moneda() != null
                        && r.relevancia() != RelevanciaMayorista.Nivel.DUDOSA)
                .forEach(r -> porMoneda.merge(r.oferta().moneda(), 1L, Long::sum));
        String monedaPrincipal = porMoneda.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
        List<Resultado> enOrden = new ArrayList<>();
        List<Resultado> fuera = new ArrayList<>();
        List<Resultado> dudosas = new ArrayList<>();
        for (Resultado r : resultados) {
            if (r.relevancia() == RelevanciaMayorista.Nivel.DUDOSA) {
                dudosas.add(r.conPosicion(null, false));
            } else if (r.motivoFuera() == null && r.oferta().moneda() != null
                    && r.oferta().moneda().equals(monedaPrincipal)) {
                enOrden.add(r);
            } else if (r.motivoFuera() == null) {
                fuera.add(r.conMotivo("Está en otra moneda (" + (r.oferta().moneda() == null ? "sin moneda"
                        : r.oferta().moneda()) + ") y no se mezcla con las demás."));
            } else {
                fuera.add(r);
            }
        }
        enOrden.sort(Comparator.comparing((Resultado r) -> r.calculo().subtotal())
                .thenComparing(r -> r.oferta().patrocinado()).thenComparing(r -> r.oferta().id()));
        List<Resultado> todos = new ArrayList<>();
        int puesto = 0;
        for (int i = 0; i < enOrden.size(); i++) {
            Resultado r = enOrden.get(i);
            BigDecimal subtotal = r.calculo().subtotal();
            if (i == 0 || subtotal.compareTo(enOrden.get(i - 1).calculo().subtotal()) != 0) {
                puesto = i + 1;
            }
            boolean empate = i > 0 && subtotal.compareTo(enOrden.get(i - 1).calculo().subtotal()) == 0
                    || i + 1 < enOrden.size() && subtotal.compareTo(enOrden.get(i + 1).calculo().subtotal()) == 0;
            todos.add(conPesos(r.conPosicion(puesto, empate)));
        }
        fuera.forEach(r -> todos.add(conPesos(r)));
        dudosas.forEach(r -> todos.add(conPesos(r)));
        if (!fuera.isEmpty()) {
            avisos.add(fuera.size() == 1 ? "1 oferta queda fuera del orden porque su precio no es claro para esa cantidad."
                    : fuera.size() + " ofertas quedan fuera del orden porque su precio no es claro para esa cantidad.");
        }
        if (!dudosas.isEmpty()) {
            avisos.add(dudosas.size() == 1 ? "1 resultado podría no ser lo que busca: va aparte y fuera del orden."
                    : dudosas.size() + " resultados podrían no ser lo que busca: van aparte y fuera del orden.");
        }
        return todos;
    }

    private Resultado conPesos(Resultado r) {
        if (r.calculo().subtotal() == null || r.oferta().moneda() == null
                || r.oferta().moneda().equals(conversor.getMonedaDestino())) {
            return r;
        }
        Optional<TasaCambio> tasa = conversor.tasaPara(r.oferta().moneda());
        return tasa.map(t -> r.conPesos(ConversorMoneda.convertir(r.calculo().subtotal(), t), t)).orElse(r);
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

    /**
     * Como {@link #consultar}, pero si Alibaba no respondió (error pasajero) lo intenta una vez más, solo si queda
     * tiempo. Cada intento cuenta en el tope diario.
     */
    private <T> T consultarConReintento(java.util.function.Supplier<T> consulta, Instant limite) {
        try {
            return consultar(consulta);
        } catch (ServicioNoDisponibleException e) {
            if (!e.getMessage().startsWith("Alibaba no respondió") || reloj.instant().isAfter(limite)) {
                throw e;
            }
            return consultar(consulta);
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
