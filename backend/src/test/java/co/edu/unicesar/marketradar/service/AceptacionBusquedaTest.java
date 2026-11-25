package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplace;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.RequisitosBusqueda;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.service.mvp.SeleccionPonderadaMvp;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Casos de aceptación de la búsqueda, cada uno en cuatro modos: sin IA, con una IA simulada que da todo por exacto
 * (incluidos productos incompatibles), con una IA simulada que da todo por "parecido" (una degradación sin motivo), y
 * con la respuesta recuperada de la caché después de otra consulta con la misma búsqueda. El resultado tiene que ser
 * el mismo en los cuatro: las reglas deciden. No usa la red ni el catálogo de un proveedor real.
 */
class AceptacionBusquedaTest {

    enum Modo { SIN_IA, IA_TODO_EXACTO, IA_TODO_PARECIDO, DESDE_CACHE }

    /**
     * @param busqueda lo que escribió la persona
     * @param ofertas id → título, en el orden de la tienda (el precio sube con el id)
     * @param cumplen ids que deben quedar como coincidencias
     * @param alternativas ids que deben quedar como alternativas, con su nivel
     * @param mvp id de la recomendación, o {@code null} si no debe haber
     */
    record Caso(String nombre, String busqueda, Map<String, String> ofertas, List<String> cumplen,
                Map<String, RequisitosBusqueda.Nivel> alternativas, String mvp) {
        @Override
        public String toString() {
            return nombre;
        }
    }

    private static final RequisitosBusqueda.Nivel SIN_CONFIRMAR = RequisitosBusqueda.Nivel.SIN_CONFIRMAR;
    private static final RequisitosBusqueda.Nivel INCUMPLE = RequisitosBusqueda.Nivel.INCUMPLE;
    private static final RequisitosBusqueda.Nivel OTRA_VARIANTE = RequisitosBusqueda.Nivel.OTRA_VARIANTE;

    static List<Caso> casos() {
        return List.of(
                new Caso("olla arrocera Oster frente a licuadora Oster", "olla arrocera oster",
                        ordenadas("1", "Licuadora Oster 600 W", "2", "Olla Arrocera Oster 1,8 L"),
                        List.of("2"), Map.of(), "2"),
                new Caso("audífonos inalámbricos: con cable, sin dato y bluetooth", "audífonos inalámbricos",
                        ordenadas("1", "Audífonos con cable de 3.5 mm", "2", "Audífonos Sony WH-CH520",
                                "3", "Audífonos Bluetooth JBL Tune 520BT"),
                        List.of("3"), ordenadas2("2", SIN_CONFIRMAR, "1", INCUMPLE), "3"),
                new Caso("freidora de aire frente a freidora de aceite", "freidora de aire",
                        ordenadas("1", "Freidora de aceite 3 litros", "2", "Air Fryer Oster 4 L"),
                        List.of("2"), ordenadas2("1", INCUMPLE), "2"),
                new Caso("licuadora roja frente a blanca", "licuadora oster roja",
                        ordenadas("1", "Licuadora Oster blanca", "2", "Licuadora Oster Roja 600 W"),
                        List.of("2"), ordenadas2("1", INCUMPLE), "2"),
                new Caso("microSD frente a tarjeta microSD", "microSD 64GB",
                        ordenadas("1", "Tarjeta microSD SanDisk 64GB", "2", "Tarjeta Regalo PlayStation Store"),
                        List.of("1"), Map.of(), "1"),
                new Caso("mando PS5 frente a control compatible", "mando ps5",
                        ordenadas("1", "Control Sony DualSense PS5", "2", "Funda Protectora Para Control PS5",
                                "3", "Consola PlayStation 5 Slim 1 TB"),
                        List.of("1"), Map.of(), "1"),
                new Caso("licuadora con vaso de repuesto frente al vaso solo", "licuadora oster",
                        ordenadas("1", "Licuadora Oster con vaso de repuesto", "2", "Vaso de repuesto para licuadora Oster"),
                        List.of("1"), Map.of(), "1"),
                new Caso("A16 128 GB confirmado junto a A16 con solo RAM", "celular samsung a16 128gb",
                        ordenadas("1", "Samsung Galaxy A16 128GB", "2", "Celular Samsung A16 4GB RAM"),
                        List.of("1"), ordenadas2("2", SIN_CONFIRMAR), "1"),
                new Caso("cero coincidencias válidas", "olla arrocera oster",
                        ordenadas("1", "Licuadora Oster 600 W", "2", "Batidora Oster de mano"),
                        List.of(), Map.of(), null),
                new Caso("dos PS5 Slim iguales y la edición Digital", "playstation 5",
                        ordenadas("1", "Consola Playstation 5 Slim 1TB", "2", "Consola Playstation 5 Slim 1TB",
                                "3", "Consola PlayStation 5 Slim Edición Digital"),
                        List.of("1", "2"), ordenadas2("3", OTRA_VARIANTE), "1"));
    }

    static Stream<Arguments> casosPorModo() {
        return casos().stream().flatMap(c -> Stream.of(Modo.values()).map(m -> Arguments.of(c, m)));
    }

    private static Map<String, String> ordenadas(String... pares) {
        Map<String, String> mapa = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put(pares[i], pares[i + 1]);
        }
        return mapa;
    }

    private static Map<String, RequisitosBusqueda.Nivel> ordenadas2(Object... pares) {
        Map<String, RequisitosBusqueda.Nivel> mapa = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            mapa.put((String) pares[i], (RequisitosBusqueda.Nivel) pares[i + 1]);
        }
        return mapa;
    }

    private ExecutorService ejecutor;

    @BeforeEach
    void preparar() {
        ejecutor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void cerrar() {
        ejecutor.shutdownNow();
    }

    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("casosPorModo")
    void lasReglasDecidenEnTodosLosModos(Caso caso, Modo modo) {
        List<OfertaNormalizada> ofertas = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> oferta : caso.ofertas().entrySet()) {
            ofertas.add(OfertaNormalizada.builder().codigoMarketplace("a").idExterno(oferta.getKey())
                    .titulo(oferta.getValue()).precio(new BigDecimal(100000 + 1000 * i++)).moneda("COP")
                    .costoEnvio(BigDecimal.ZERO).condicion("nuevo").vendedor("Tienda")
                    .urlDestino("https://ejemplo.com/" + oferta.getKey()).build());
        }
        AdaptadorMarketplace tienda = mock(AdaptadorMarketplace.class);
        when(tienda.codigo()).thenReturn("a");
        when(tienda.estaHabilitado()).thenReturn(true);
        when(tienda.buscar(any())).thenReturn(ofertas);
        ServicioPersistenciaOfertas persistencia = mock(ServicioPersistenciaOfertas.class);
        when(persistencia.guardarOfertas(any())).thenReturn(Map.of());
        ServicioBusqueda servicio = new ServicioBusqueda(List.of(tienda), ejecutor, CircuitBreakerRegistry.ofDefaults(),
                persistencia, PropiedadesPrueba.crear(), new SeleccionPonderadaMvp(PropiedadesPrueba.crear()));
        if (modo == Modo.IA_TODO_EXACTO || modo == Modo.IA_TODO_PARECIDO) {
            ServicioIABusqueda ia = mock(ServicioIABusqueda.class);
            when(ia.analizar(any(), any())).thenAnswer(invocacion -> {
                List<OfertaNormalizada> recibidas = invocacion.getArgument(1);
                return Optional.of(modo == Modo.IA_TODO_EXACTO
                        ? new ServicioIABusqueda.Analisis(recibidas, List.of(), null)
                        : new ServicioIABusqueda.Analisis(List.of(), recibidas, null));
            });
            servicio.usarIA(ia);
        }
        ConsultaBusqueda consulta = ConsultaBusqueda.crear(caso.busqueda(), null, Set.of(), 1, 10);
        if (modo == Modo.DESDE_CACHE) {
            // Otra consulta con la misma búsqueda en la tienda deja la respuesta en caché.
            servicio.buscar(consulta.conRangoDePrecio(null, BigDecimal.ONE));
        }

        ResultadoBusqueda resultado = servicio.buscar(consulta);

        verify(tienda, times(1)).buscar(any());
        assertEquals(caso.cumplen(), resultado.getOfertas().stream().map(OfertaNormalizada::getIdExterno).toList(),
                "coincidencias; alternativas: " + resultado.getDetalle().alternativas());
        Map<String, RequisitosBusqueda.Nivel> alternativas = new java.util.LinkedHashMap<>();
        resultado.getDetalle().alternativas().forEach(a -> alternativas.put(a.oferta().getIdExterno(), a.nivel()));
        assertEquals(caso.alternativas(), alternativas);
        assertTrue(resultado.getDetalle().alternativas().stream().allMatch(a -> !a.motivos().isEmpty()
                        && a.motivos().stream().noneMatch(m -> m.contains("Puede no ser exactamente"))),
                "cada alternativa dice qué dato la separa");
        if (caso.mvp() == null) {
            assertTrue(resultado.getProductoMvp().isEmpty());
            assertTrue(resultado.getDetalle().motivoSinRecomendacion().contains("ninguna es lo que buscó"),
                    resultado.getDetalle().motivoSinRecomendacion());
        } else {
            assertEquals(caso.mvp(), resultado.getProductoMvp().orElseThrow().oferta().getIdExterno());
            assertNull(resultado.getDetalle().motivoSinRecomendacion());
        }
        assertEquals(caso.ofertas().size() - caso.cumplen().size() - caso.alternativas().size(),
                resultado.getDetalle().descartadas());
    }
}
