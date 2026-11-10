package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplace;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.OrdenResultados;
import co.edu.unicesar.marketradar.domain.ProductoMvp;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;
import co.edu.unicesar.marketradar.service.mvp.EstrategiaSeleccionMvp;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Orquesta la búsqueda: consulta la caché, ejecuta los adaptadores en paralelo con timeout y
 * circuit breaker, guarda los resultados y los ordena. Depende solo de la interfaz
 * {@link AdaptadorMarketplace} (Low Coupling, DIP), por lo que un marketplace nuevo no exige
 * modificar esta clase (OCP).
 */
@Service
public class ServicioBusqueda {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioBusqueda.class);

    /**
     * Si una tienda falló o no hay tiendas, el resultado se recuerda solo un momento: lo justo para no repetir la
     * consulta en la misma búsqueda, pero sin dejar la búsqueda vacía durante la hora completa de la caché.
     */
    static final Duration VIGENCIA_CON_FALLAS = Duration.ofSeconds(30);

    private final Map<String, AdaptadorMarketplace> adaptadoresPorCodigo = new HashMap<>();
    private final ExecutorService ejecutor;
    private final CircuitBreakerRegistry circuitBreakers;
    private final ServicioPersistenciaOfertas persistencia;
    private final Cache<String, ResultadoCompleto> cache;
    private final int timeoutSegundos;
    private final EstrategiaSeleccionMvp seleccionMvp;

    @Autowired
    public ServicioBusqueda(List<AdaptadorMarketplace> adaptadores, ExecutorService ejecutor,
                            CircuitBreakerRegistry circuitBreakers, ServicioPersistenciaOfertas persistencia,
                            PropiedadesMarketradar propiedades, EstrategiaSeleccionMvp seleccionMvp) {
        this(adaptadores, ejecutor, circuitBreakers, persistencia, propiedades, seleccionMvp, Ticker.systemTicker());
    }

    /** Para las pruebas: el reloj de la caché se puede adelantar. */
    ServicioBusqueda(List<AdaptadorMarketplace> adaptadores, ExecutorService ejecutor,
                     CircuitBreakerRegistry circuitBreakers, ServicioPersistenciaOfertas persistencia,
                     PropiedadesMarketradar propiedades, EstrategiaSeleccionMvp seleccionMvp, Ticker reloj) {
        adaptadores.forEach(adaptador -> adaptadoresPorCodigo.put(adaptador.codigo(), adaptador));
        this.ejecutor = ejecutor;
        this.circuitBreakers = circuitBreakers;
        this.persistencia = persistencia;
        this.timeoutSegundos = propiedades.busqueda().timeoutSegundos();
        this.seleccionMvp = seleccionMvp;
        long vigenciaNormal = Duration.ofMinutes(propiedades.busqueda().cacheMinutos()).toNanos();
        this.cache = Caffeine.newBuilder()
                .expireAfter(new Expiry<String, ResultadoCompleto>() {
                    @Override
                    public long expireAfterCreate(String clave, ResultadoCompleto completo, long ahora) {
                        return completo.huboFallas() ? VIGENCIA_CON_FALLAS.toNanos() : vigenciaNormal;
                    }

                    @Override
                    public long expireAfterUpdate(String clave, ResultadoCompleto completo, long ahora, long restante) {
                        return expireAfterCreate(clave, completo, ahora);
                    }

                    @Override
                    public long expireAfterRead(String clave, ResultadoCompleto completo, long ahora, long restante) {
                        return restante;
                    }
                })
                .ticker(reloj)
                .maximumSize(500)
                .build();
    }

    /**
     * Busca un producto en los marketplaces habilitados, sin asociar la búsqueda a una persona.
     *
     * @param consulta parámetros validados
     * @return ofertas de la página pedida, advertencias y total
     */
    public ResultadoBusqueda buscar(ConsultaBusqueda consulta) {
        return buscar(consulta, null);
    }

    /**
     * Busca un producto en los marketplaces habilitados y la guarda en el historial de la persona.
     * Solo la primera página cuenta como búsqueda nueva, para no repetirla al paginar.
     *
     * @param consulta parámetros validados
     * @param usuarioId persona con sesión, o {@code null}
     * @return ofertas de la página pedida, advertencias, total y producto MVP
     */
    public ResultadoBusqueda buscar(ConsultaBusqueda consulta, Long usuarioId) {
        return buscar(consulta, usuarioId, true);
    }

    /**
     * Busca sin guardar nada en el historial. Sirve para probar una búsqueda antes de decidir cuál se muestra;
     * el resultado queda en caché, así que repetirla después con {@link #buscar(ConsultaBusqueda, Long)} no
     * vuelve a consultar las tiendas.
     *
     * @param consulta parámetros validados
     * @return ofertas de la página pedida, advertencias, total y producto MVP
     */
    public ResultadoBusqueda consultar(ConsultaBusqueda consulta) {
        return buscar(consulta, null, false);
    }

    private ResultadoBusqueda buscar(ConsultaBusqueda consulta, Long usuarioId, boolean registrar) {
        long inicio = System.nanoTime();
        ResultadoCompleto completo = cache.get(consulta.claveCache(), clave -> consultarMarketplaces(consulta));
        List<OfertaNormalizada> ordenadas = ordenar(completo.ofertas(), consulta.getOrden());
        int desde = Math.min((consulta.getPagina() - 1) * consulta.getTamano(), ordenadas.size());
        int hasta = Math.min(desde + consulta.getTamano(), ordenadas.size());
        BigDecimal mejorPrecio = ordenadas.stream().map(OfertaNormalizada::precioTotal)
                .min(Comparator.naturalOrder()).orElse(null);
        ProductoMvp mvp = seleccionMvp.elegir(ordenadas).orElse(null);
        if (registrar && consulta.getPagina() == 1) {
            registrarBusquedaSinInterrumpir(consulta.getTermino(), ordenadas.size(), inicio, usuarioId,
                    imagenRepresentativa(mvp, ordenadas));
        }
        return new ResultadoBusqueda(ordenadas.subList(desde, hasta), completo.advertencias(), ordenadas.size(),
                completo.idsPersistidos(), mejorPrecio, mvp);
    }

    /**
     * Devuelve los adaptadores registrados, por código.
     *
     * @return copia del registro de adaptadores
     */
    public Map<String, AdaptadorMarketplace> adaptadoresRegistrados() {
        return new HashMap<>(adaptadoresPorCodigo);
    }

    private ResultadoCompleto consultarMarketplaces(ConsultaBusqueda consulta) {
        long inicio = System.nanoTime();
        List<AdaptadorMarketplace> seleccionados = adaptadoresPorCodigo.values().stream()
                .filter(AdaptadorMarketplace::estaHabilitado)
                .filter(adaptador -> consulta.getMarketplaces().isEmpty()
                        || consulta.getMarketplaces().contains(adaptador.codigo()))
                .toList();

        List<String> advertencias = new ArrayList<>();
        boolean[] huboFallas = {seleccionados.isEmpty()};
        if (seleccionados.isEmpty()) {
            advertencias.add("Por ahora no hay tiendas disponibles para buscar. Intente más tarde.");
        }

        Map<String, CompletableFuture<List<OfertaNormalizada>>> tareas = new HashMap<>();
        for (AdaptadorMarketplace adaptador : seleccionados) {
            tareas.put(adaptador.codigo(), consultarAsincrono(adaptador, consulta));
        }

        List<OfertaNormalizada> ofertas = new ArrayList<>();
        tareas.forEach((codigo, tarea) -> {
            try {
                ofertas.addAll(tarea.join());
            } catch (RuntimeException e) {
                LOG.warn("El marketplace {} no respondió: {}", codigo, e.getMessage());
                huboFallas[0] = true;
                advertencias.add("La tienda " + nombreVisible(codigo) + " no respondió a tiempo. Mostramos los resultados de las demás.");
            }
        });

        Map<String, Long> ids = guardarSinInterrumpir(ofertas);
        return new ResultadoCompleto(ofertas, advertencias, ids, huboFallas[0]);
    }

    private CompletableFuture<List<OfertaNormalizada>> consultarAsincrono(AdaptadorMarketplace adaptador,
                                                                          ConsultaBusqueda consulta) {
        var circuitBreaker = circuitBreakers.circuitBreaker(adaptador.codigo());
        return CompletableFuture
                .supplyAsync(() -> {
                    long inicio = System.nanoTime();
                    boolean exitoso = false;
                    try {
                        List<OfertaNormalizada> resultado = circuitBreaker.executeSupplier(() -> adaptador.buscar(consulta));
                        exitoso = true;
                        return resultado;
                    } finally {
                        registrarConsumoSinInterrumpir(adaptador.codigo(), exitoso, inicio);
                    }
                }, ejecutor)
                .orTimeout(timeoutSegundos, TimeUnit.SECONDS);
    }

    private Map<String, Long> guardarSinInterrumpir(List<OfertaNormalizada> ofertas) {
        try {
            return persistencia.guardarOfertas(ofertas);
        } catch (AccesoDatosException e) {
            LOG.error("No se pudieron guardar los resultados de la búsqueda", e);
            return Map.of();
        }
    }

    /**
     * Foto que representa una búsqueda en el historial: la del producto MVP o, si no tiene, la de la primera
     * oferta que tenga una. Solo se aceptan fotos con {@code https}, igual que en los resultados.
     *
     * @param mvp mejor opción de compra, o {@code null}
     * @param ordenadas ofertas en el orden en que se muestran
     * @return dirección de la foto, o {@code null} si ninguna oferta tiene
     */
    static String imagenRepresentativa(ProductoMvp mvp, List<OfertaNormalizada> ordenadas) {
        Stream<OfertaNormalizada> candidatas = Stream.concat(
                mvp == null ? Stream.empty() : Stream.of(mvp.oferta()), ordenadas.stream());
        return candidatas.map(OfertaNormalizada::getUrlImagen)
                .filter(url -> url != null && url.startsWith("https://") && url.length() <= 2048)
                .findFirst().orElse(null);
    }

    private void registrarBusquedaSinInterrumpir(String termino, int total, long inicio, Long usuarioId,
                                                 String urlImagen) {
        try {
            persistencia.registrarBusqueda(termino, total, milisegundosDesde(inicio), usuarioId, urlImagen);
        } catch (AccesoDatosException e) {
            LOG.error("No se pudo registrar la búsqueda", e);
        }
    }

    private void registrarConsumoSinInterrumpir(String codigo, boolean exitoso, long inicio) {
        try {
            persistencia.registrarConsumoApi(codigo, exitoso, milisegundosDesde(inicio));
        } catch (AccesoDatosException e) {
            LOG.error("No se pudo registrar el consumo de la API de {}", codigo, e);
        }
    }

    private static List<OfertaNormalizada> ordenar(List<OfertaNormalizada> ofertas, OrdenResultados orden) {
        List<OfertaNormalizada> copia = new ArrayList<>(ofertas);
        Comparator<OfertaNormalizada> porPrecio = Comparator.comparing(OfertaNormalizada::precioTotal);
        switch (orden) {
            case PRECIO_ASC -> copia.sort(porPrecio);
            case PRECIO_DESC -> copia.sort(porPrecio.reversed());
            case RELEVANCIA -> { }
        }
        return copia;
    }

    private static int milisegundosDesde(long inicioNanos) {
        return (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNanos);
    }

    private record ResultadoCompleto(List<OfertaNormalizada> ofertas, List<String> advertencias,
                                     Map<String, Long> idsPersistidos, boolean huboFallas) {
    }

    /**
     * Nombre de la tienda tal como lo ve la persona, por ejemplo {@code Mercado Libre} para {@code mercadolibre}.
     *
     * @param codigo código del marketplace
     * @return nombre para mostrar
     */
    public static String nombreVisible(String codigo) {
        return switch (codigo) {
            case "aliexpress" -> "AliExpress";
            case "shopify" -> "Shopify";
            case "mercadolibre" -> "Mercado Libre";
            case "googleshopping" -> "Google Shopping";
            default -> codigo;
        };
    }
}
