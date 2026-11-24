package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplace;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.OrdenResultados;
import co.edu.unicesar.marketradar.domain.ProductoMvp;
import co.edu.unicesar.marketradar.domain.RequisitosBusqueda;
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
import java.util.Optional;
import java.util.Set;
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
    private ServicioIABusqueda ia;

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

        // Revisión final, en cada búsqueda y también cuando la respuesta sale de la caché: la caché guarda candidatos
        // de las tiendas, no la decisión. Lo que pidió esta persona (su texto original) decide qué es coincidencia,
        // qué es una alternativa y qué es otro producto. Ni la IA ni el filtro de palabras pueden saltarse esto.
        RequisitosBusqueda requisitos = consulta.requisitos();
        Clasificacion clasificacion = clasificar(ordenar(completo.ofertas(), consulta.getOrden()), requisitos,
                completo.exactasIa());
        List<OfertaNormalizada> cumplen = clasificacion.cumplen();
        List<ResultadoBusqueda.Alternativa> alternativas = clasificacion.alternativas();

        // El rango de precio se aplica sobre lo que ya está en caché: cambiarlo no gasta búsquedas en las tiendas.
        // Compara el costo conocido (precio más el envío que informa la tienda); la página avisa cuáles no lo informan.
        List<OfertaNormalizada> ordenadas = consulta.tieneRangoDePrecio()
                ? cumplen.stream().filter(oferta -> consulta.precioEnRango(oferta.costoConocido())).toList()
                : cumplen;
        List<ResultadoBusqueda.Alternativa> alternativasEnRango = consulta.tieneRangoDePrecio()
                ? alternativas.stream().filter(a -> consulta.precioEnRango(a.oferta().costoConocido())).toList()
                : alternativas;
        int desde = Math.min((consulta.getPagina() - 1) * consulta.getTamano(), ordenadas.size());
        int hasta = Math.min(desde + consulta.getTamano(), ordenadas.size());
        // Solo puede ser la recomendación una oferta con identidad confirmada, que cumple todo y con precio admisible.
        List<OfertaNormalizada> candidatas = ordenadas.stream().filter(OfertaNormalizada::precioVerificado).toList();
        Optional<OfertaNormalizada> masBarata = candidatas.stream()
                .min(Comparator.comparing(OfertaNormalizada::costoConocido));
        BigDecimal mejorPrecio = masBarata.map(OfertaNormalizada::costoConocido).orElse(null);
        ProductoMvp mvp = seleccionMvp.elegir(candidatas).orElse(null);
        // Filtrar por precio es mirar de otra forma la misma búsqueda: no se repite en el historial.
        if (registrar && consulta.getPagina() == 1 && !consulta.tieneRangoDePrecio()) {
            // En el historial queda lo que escribió la persona (sin saludos), no la corrección: así "nuevo" o "128 GB"
            // no se pierden al repetir la búsqueda.
            registrarBusquedaSinInterrumpir(ServicioSugerencias.limpiarFrase(consulta.getTextoOriginal()),
                    cumplen.size(), inicio, usuarioId, imagenRepresentativa(mvp, ordenadas.isEmpty()
                            ? alternativas.stream().map(ResultadoBusqueda.Alternativa::oferta).toList() : ordenadas));
        }
        // El resumen de la IA se escribió antes de esta revisión: solo se muestra si todo lo que eligió sigue arriba,
        // si sus precios son los de lo que se ve y si no nombra ningún producto descartado.
        String resumen = ordenadas.size() == completo.ofertas().size()
                && resumenValido(completo.resumenIa(), ordenadas, clasificacion.descartadas()) ? completo.resumenIa()
                : null;
        int descartadas = Math.max(0, completo.recibidas() - cumplen.size() - alternativas.size());
        ResultadoBusqueda.Detalle detalle = new ResultadoBusqueda.Detalle(
                consulta.getPagina() == 1 ? alternativasEnRango : List.of(), requisitos.etiquetas(),
                motivoSinRecomendacion(mvp, clasificacion, ordenadas, completo.recibidas(), descartadas, requisitos),
                (int) ordenadas.stream().filter(o -> o.totalConfirmado().isEmpty()).count(),
                masBarata.map(o -> o.totalConfirmado().isPresent()).orElse(true), descartadas);
        return new ResultadoBusqueda(ordenadas.subList(desde, hasta), completo.advertencias(), ordenadas.size(),
                completo.idsPersistidos(), mejorPrecio, mvp, resumen, cumplen.size(), detalle);
    }

    /**
     * Resultado de la revisión final de una búsqueda.
     *
     * @param cumplen ofertas con la identidad confirmada que cumplen todo lo pedido, con alerta de precio si aplica
     * @param alternativas el mismo tipo de producto, pero sin confirmar o con algo distinto (marca, variante, paquete)
     * @param descartadas otro producto: no se muestran ni como alternativa
     */
    record Clasificacion(List<OfertaNormalizada> cumplen, List<ResultadoBusqueda.Alternativa> alternativas,
                         List<OfertaNormalizada> descartadas) {
    }

    /**
     * Clasifica cada candidata según lo que pidió la persona. Lo que la IA marcó como "parecido" nunca pasa a
     * coincidencia exacta, aunque el título lo parezca. Los precios atípicos se marcan con la mediana de las que
     * cumplen, que son el mismo producto.
     *
     * @param ofertas candidatas, en el orden en que se muestran
     * @param requisitos lo que pidió la persona
     * @param exactasIa identificadores de lo que la IA eligió como exacto, o {@code null} si la IA no eligió
     * @return la clasificación
     */
    static Clasificacion clasificar(List<OfertaNormalizada> ofertas, RequisitosBusqueda requisitos,
                                    Set<String> exactasIa) {
        Map<OfertaNormalizada, RequisitosBusqueda.Evaluacion> evaluaciones = new java.util.LinkedHashMap<>();
        for (OfertaNormalizada oferta : ofertas) {
            RequisitosBusqueda.Evaluacion evaluacion = requisitos.evaluar(oferta.getTitulo(), oferta.condicionProducto());
            if (evaluacion.nivel() == RequisitosBusqueda.Nivel.CUMPLE && exactasIa != null
                    && !exactasIa.contains(clave(oferta))) {
                evaluacion = new RequisitosBusqueda.Evaluacion(RequisitosBusqueda.Nivel.SIN_CONFIRMAR,
                        List.of("Puede no ser exactamente el producto que buscó"));
            }
            evaluaciones.put(oferta, evaluacion);
        }
        List<OfertaNormalizada> marcadas = marcarPreciosAtipicos(ofertas, evaluaciones);
        List<OfertaNormalizada> cumplen = new ArrayList<>();
        List<ResultadoBusqueda.Alternativa> porConfirmar = new ArrayList<>();
        List<ResultadoBusqueda.Alternativa> incumplen = new ArrayList<>();
        List<OfertaNormalizada> descartadas = new ArrayList<>();
        for (int i = 0; i < ofertas.size(); i++) {
            OfertaNormalizada oferta = marcadas.get(i);
            RequisitosBusqueda.Evaluacion evaluacion = evaluaciones.get(ofertas.get(i));
            if (evaluacion.ajena()) {
                descartadas.add(oferta);
                continue;
            }
            switch (evaluacion.nivel()) {
                case CUMPLE -> cumplen.add(oferta);
                case SIN_CONFIRMAR -> porConfirmar.add(new ResultadoBusqueda.Alternativa(oferta, evaluacion.nivel(),
                        evaluacion.motivos()));
                case INCUMPLE -> incumplen.add(new ResultadoBusqueda.Alternativa(oferta, evaluacion.nivel(),
                        evaluacion.motivos()));
            }
        }
        List<ResultadoBusqueda.Alternativa> alternativas = new ArrayList<>(porConfirmar);
        alternativas.addAll(incumplen);
        return new Clasificacion(cumplen, alternativas, descartadas);
    }

    private static String clave(OfertaNormalizada oferta) {
        return oferta.getCodigoMarketplace() + "|" + oferta.getIdExterno() + "|" + oferta.getTitulo();
    }

    /**
     * El resumen de la IA vale si cada precio que nombra es de una oferta que se muestra y si no menciona ninguna
     * palabra propia de un producto descartado ("Q21", "X5"). Revisar solo los montos no basta: una consola Q21 y una
     * PS5 pueden costar lo mismo.
     */
    static boolean resumenValido(String resumen, List<OfertaNormalizada> mostradas, List<OfertaNormalizada> descartadas) {
        if (resumen == null || resumen.isBlank()) {
            return false;
        }
        if (!ServicioIABusqueda.preciosCoinciden(resumen, mostradas)) {
            return false;
        }
        return !nombraOtras(resumen, mostradas, descartadas);
    }

    private static boolean nombraOtras(String resumen, List<OfertaNormalizada> mostradas,
                                       List<OfertaNormalizada> descartadas) {
        if (descartadas.isEmpty()) {
            return false;
        }
        java.util.Set<String> vistas = new java.util.HashSet<>();
        mostradas.forEach(o -> vistas.addAll(co.edu.unicesar.marketradar.domain.TextoProducto.palabras(o.getTitulo())));
        java.util.Set<String> delResumen = new java.util.HashSet<>(
                co.edu.unicesar.marketradar.domain.TextoProducto.palabras(resumen));
        for (OfertaNormalizada descartada : descartadas) {
            for (String palabra : co.edu.unicesar.marketradar.domain.TextoProducto.palabrasClave(descartada.getTitulo())) {
                // Solo palabras que distinguen al producto descartado: con letras, y que no están en lo que se muestra.
                if (palabra.length() >= 2 && palabra.matches(".*[a-z].*") && !vistas.contains(palabra)
                        && delResumen.contains(palabra)) {
                    LOG.info("Se descartó el resumen de la IA porque nombra \"{}\", de una oferta descartada", palabra);
                    return true;
                }
            }
        }
        return false;
    }

    /** Una oferta cuyo precio es más de 4 veces la mediana es sospechosa, y también si es menos de un cuarto. */
    static final BigDecimal FACTOR_ATIPICO = new BigDecimal("4");
    static final String ALERTA_ATIPICO = "Su precio es muy distinto al de las demás ofertas de este producto: puede ser "
            + "un error de la tienda o de otra moneda. Revíselo antes de comprar.";

    /**
     * Marca los precios que no se parecen a los demás del mismo producto, sin cambiarlos: se muestran con un aviso y
     * no se usan para recomendar ni para decir cuál es más barata. Se compara con la mediana de las ofertas que cumplen
     * todo (el mismo producto, con la identidad confirmada), si hay al menos 3. Las alternativas también se marcan con
     * esa referencia; lo que es otro producto no cuenta ni se marca.
     *
     * @param ofertas ofertas de la búsqueda
     * @param evaluaciones la evaluación de cada oferta
     * @return las mismas ofertas, en el mismo orden, con alerta en las de precio atípico
     */
    static List<OfertaNormalizada> marcarPreciosAtipicos(List<OfertaNormalizada> ofertas, RequisitosBusqueda requisitos) {
        Map<OfertaNormalizada, RequisitosBusqueda.Evaluacion> evaluaciones = new java.util.HashMap<>();
        ofertas.forEach(o -> evaluaciones.put(o, requisitos.evaluar(o.getTitulo(), o.condicionProducto())));
        return marcarPreciosAtipicos(ofertas, evaluaciones);
    }

    /**
     * Igual que {@link #marcarPreciosAtipicos(List, RequisitosBusqueda)}, con las evaluaciones ya hechas.
     *
     * @param ofertas ofertas de la búsqueda
     * @param evaluaciones la evaluación de cada oferta
     * @return las mismas ofertas, en el mismo orden, con alerta en las de precio atípico
     */
    static List<OfertaNormalizada> marcarPreciosAtipicos(List<OfertaNormalizada> ofertas,
                                                         Map<OfertaNormalizada, RequisitosBusqueda.Evaluacion> evaluaciones) {
        List<BigDecimal> precios = ofertas.stream()
                .filter(o -> evaluaciones.get(o).nivel() == RequisitosBusqueda.Nivel.CUMPLE)
                .map(OfertaNormalizada::getPrecio).sorted().toList();
        if (precios.size() < 3) {
            return ofertas;
        }
        BigDecimal mediana = precios.size() % 2 == 1 ? precios.get(precios.size() / 2)
                : precios.get(precios.size() / 2 - 1).add(precios.get(precios.size() / 2))
                .divide(BigDecimal.valueOf(2), java.math.RoundingMode.HALF_UP);
        BigDecimal techo = mediana.multiply(FACTOR_ATIPICO);
        BigDecimal piso = mediana.divide(FACTOR_ATIPICO, java.math.RoundingMode.HALF_UP);
        List<OfertaNormalizada> marcadas = new ArrayList<>();
        for (OfertaNormalizada oferta : ofertas) {
            RequisitosBusqueda.Evaluacion evaluacion = evaluaciones.get(oferta);
            boolean comparable = !evaluacion.ajena() && evaluacion.nivel() != RequisitosBusqueda.Nivel.INCUMPLE;
            boolean atipica = oferta.getPrecio().compareTo(techo) > 0 || oferta.getPrecio().compareTo(piso) < 0;
            if (comparable && atipica && oferta.precioVerificado()) {
                LOG.info("Precio atípico en \"{}\": {} {} (texto original \"{}\"; mediana {})", oferta.getTitulo(),
                        oferta.getPrecio(), oferta.getMoneda(), oferta.getPrecioTexto(), mediana);
                marcadas.add(oferta.conAlertaPrecio(ALERTA_ATIPICO));
            } else {
                marcadas.add(oferta);
            }
        }
        return marcadas;
    }

    private static String motivoSinRecomendacion(ProductoMvp mvp, Clasificacion clasificacion,
                                                 List<OfertaNormalizada> enRango, int recibidas, int descartadas,
                                                 RequisitosBusqueda requisitos) {
        if (mvp != null || recibidas == 0) {
            return null;
        }
        if (clasificacion.cumplen().isEmpty() && clasificacion.alternativas().isEmpty()) {
            return "Las tiendas mostraron " + recibidas + (recibidas == 1 ? " oferta" : " ofertas")
                    + ", pero ninguna es lo que buscó: " + (recibidas == 1 ? "era otro producto" : "eran otros productos")
                    + " o accesorios. No recomendamos ninguna. Pruebe con otras palabras o revise cómo se escribe.";
        }
        if (clasificacion.cumplen().isEmpty()) {
            return requisitos.estaVacio()
                    ? "Ninguna oferta confirma que sea el producto que buscó, así que no recomendamos ninguna. Abajo "
                    + "están las que se parecen."
                    : "Ninguna oferta confirma todo lo que usted pidió (" + String.join(", ", requisitos.etiquetas())
                    + "), así que no recomendamos ninguna. Abajo están las que se parecen, con lo que les falta.";
        }
        if (enRango.isEmpty()) {
            return "Ninguna de las ofertas que cumplen su búsqueda está en el rango de precio que eligió.";
        }
        return "No recomendamos ninguna porque los precios de las ofertas que cumplen no se pudieron verificar.";
    }

    /**
     * Con Gemini configurado, la IA elige los productos que de verdad son lo buscado y escribe un resumen.
     *
     * @param ia búsqueda con IA
     */
    @Autowired(required = false)
    public void usarIA(ServicioIABusqueda ia) {
        this.ia = ia;
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

        // Las cajas vacías y lo que no trae el producto no se muestran nunca, ni se le pasan a la IA.
        List<OfertaNormalizada> conProducto = FiltroComplementos.quitarSinProducto(consulta.getTermino(), ofertas);

        // Con IA: Gemini deja solo lo que de verdad es lo buscado y escribe el resumen. Sin IA, o si no eligió
        // nada que sirva, se deja lo que más se parece a lo buscado, sin juegos, fundas ni paquetes digitales.
        Optional<ServicioIABusqueda.Analisis> analisis = ia == null || conProducto.isEmpty() ? Optional.empty()
                : ia.analizar(consulta.getTermino(), conProducto);
        Optional<List<OfertaNormalizada>> elegidasPorIA = analisis
                .map(elegido -> FiltroRelevancia.revisarEleccion(consulta.getTermino(), elegido.elegidas(), conProducto))
                .filter(elegidas -> !elegidas.isEmpty());
        if (analisis.isPresent() && elegidasPorIA.isEmpty()) {
            LOG.info("La IA no eligió productos que sirvan para \"{}\"; se usan los filtros propios", consulta.getTermino());
        }
        List<OfertaNormalizada> relevantes = elegidasPorIA
                .orElseGet(() -> FiltroRelevancia.filtrar(consulta.getTermino(), conProducto));
        Map<String, Long> ids = guardarSinInterrumpir(relevantes);
        // Lo que la IA eligió como exacto se recuerda aparte: lo que marcó como parecido nunca pasa a exacto.
        Set<String> exactasIa = elegidasPorIA.isPresent()
                ? analisis.get().exactas().stream().map(ServicioBusqueda::clave)
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
                : null;
        String resumen = elegidasPorIA.isPresent() ? analisis.get().resumen() : null;
        return new ResultadoCompleto(relevantes, advertencias, ids, huboFallas[0], resumen, exactasIa, ofertas.size());
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
        Comparator<OfertaNormalizada> porPrecio = Comparator.comparing(OfertaNormalizada::costoConocido);
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

    /**
     * Lo que se guarda en caché: candidatos de las tiendas, no la decisión final (esa se toma en cada búsqueda).
     *
     * @param exactasIa claves de lo que la IA eligió como exacto, o {@code null} si no se usó su elección
     * @param recibidas cuántas ofertas mandaron las tiendas, antes de cualquier filtro
     */
    private record ResultadoCompleto(List<OfertaNormalizada> ofertas, List<String> advertencias,
                                     Map<String, Long> idsPersistidos, boolean huboFallas, String resumenIa,
                                     Set<String> exactasIa, int recibidas) {
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
