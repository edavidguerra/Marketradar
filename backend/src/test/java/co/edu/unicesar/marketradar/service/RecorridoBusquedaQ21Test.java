package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.adapter.googleshopping.AdaptadorGoogleShopping;
import co.edu.unicesar.marketradar.adapter.googleshopping.GoogleShoppingSimulado;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.dto.RespuestaBusquedaDto;
import co.edu.unicesar.marketradar.service.mvp.SeleccionPonderadaMvp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Recorrido completo con la Q21 de la captura: respuesta de SerpApi (simulada) → adaptador de Google Shopping →
 * filtros → evaluación de identidad → DTO en JSON → recomendación. No usa la red: la respuesta del proveedor es un
 * JSON fijo construido a partir de la descripción del caso (la captura original no venía adjunta).
 */
class RecorridoBusquedaQ21Test {

    /** Marcador técnico en el nombre del vendedor, como el de la captura. */
    static final String MARCADOR = "mc_9f3a77c1e2b44d0a8 · merchant_id=8841377";

    static final String RESPUESTA = """
            {"shopping_results":[
              {"title":"Consola De Videojuegos Portátil Q21 Pantalla 3.5 Pulgadas 10000 Juegos Retro","product_id":"q21",
               "source":"%s","price":"$ 89.900","extracted_price":89900,"delivery":"Envío gratis"},
              {"title":"Consola PlayStation 5 Slim 1 TB Estándar","product_id":"ps5",
               "source":"Alkosto","price":"$ 2.599.900","extracted_price":2599900,"delivery":"Envío gratis"},
              {"title":"Control DualSense PS5 Inalámbrico Blanco","product_id":"control",
               "source":"Éxito","price":"$ 289.900","extracted_price":289900,"delivery":"Envío gratis"},
              {"title":"Juego EA Sports FC 25 PS5","product_id":"juego",
               "source":"Falabella","price":"$ 199.900","extracted_price":199900,"delivery":"Envío gratis"},
              {"title":"Sony PlayStation 5 Slim 1TB Edición Digital","product_id":"digital",
               "source":"Ktronix","price":"$ 2.199.900","extracted_price":2199900,"delivery":"Envío gratis"},
              {"title":"Caja Vacía PS5 Original","product_id":"caja",
               "source":"Mercado Libre","price":"$ 50.000","extracted_price":50000},
              {"title":"Consola Retro X5 Portátil Bluetooth 5.0","product_id":"x5",
               "source":"Linio","price":"$ 120.000","extracted_price":120000},
              {"title":"Consola PS5 Slim 1 TB + 2 Juegos","product_id":"combo",
               "source":"Mercado Libre","price":"$ 2.999.900","extracted_price":2999900,"delivery":"Envío gratis"}
            ]}""".formatted(MARCADOR);

    private final ObjectMapper json = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).registerModule(new JavaTimeModule());
    private ExecutorService ejecutor;
    private ServicioPersistenciaOfertas persistencia;

    @BeforeEach
    void preparar() {
        ejecutor = Executors.newFixedThreadPool(2);
        persistencia = mock(ServicioPersistenciaOfertas.class);
        when(persistencia.guardarOfertas(any())).thenReturn(Map.of());
    }

    @AfterEach
    void cerrar() {
        ejecutor.shutdownNow();
    }

    private AdaptadorGoogleShopping proveedor(String respuesta) {
        ConversorMoneda conversor = new ConversorMoneda(PropiedadesPrueba.crear(), moneda -> Optional.empty());
        return GoogleShoppingSimulado.crear(conversor, respuesta);
    }

    private ServicioBusqueda servicio(String respuesta) {
        return new ServicioBusqueda(List.of(proveedor(respuesta)), ejecutor, CircuitBreakerRegistry.ofDefaults(),
                persistencia, PropiedadesPrueba.crear(), new SeleccionPonderadaMvp(PropiedadesPrueba.crear()));
    }

    private JsonNode buscar(ServicioBusqueda servicio, String termino) throws Exception {
        ConsultaBusqueda consulta = ConsultaBusqueda.crear(termino, null, Set.of(), 1, 10);
        return json.readTree(json.writeValueAsString(RespuestaBusquedaDto.desde(consulta, servicio.buscar(consulta))));
    }

    private static List<String> titulos(JsonNode ofertas) {
        List<String> titulos = new ArrayList<>();
        ofertas.forEach(o -> titulos.add(o.has("oferta") ? o.path("oferta").path("titulo").asText()
                : o.path("titulo").asText()));
        return titulos;
    }

    @Test
    void laQ21NoEsCoincidenciaNiAlternativaNiRecomendacion() throws Exception {
        JsonNode respuesta = buscar(servicio(RESPUESTA), "playstation 5");

        assertEquals(List.of("Consola PlayStation 5 Slim 1 TB Estándar"), titulos(respuesta.path("ofertas")));
        assertEquals(1, respuesta.path("total_resultados").asInt());
        JsonNode mvp = respuesta.path("producto_mvp");
        assertEquals("Consola PlayStation 5 Slim 1 TB Estándar", mvp.path("oferta").path("titulo").asText());
        // Alternativas: solo productos que sí son la consola, identificados como otra variante o paquete.
        List<String> alternativas = titulos(respuesta.path("alternativas"));
        assertEquals(List.of("Sony PlayStation 5 Slim 1TB Edición Digital", "Consola PS5 Slim 1 TB + 2 Juegos"),
                alternativas);
        String texto = respuesta.toString();
        assertFalse(texto.contains("Q21"), "la Q21 no aparece en ninguna parte de la respuesta");
        assertFalse(texto.contains("X5"), "la X5 tampoco");
        assertFalse(texto.contains("DualSense"), "el control no se presenta como alternativa");
        assertFalse(texto.contains("merchant_id"), "el marcador técnico del vendedor no llega a la interfaz");
        assertTrue(respuesta.path("descartadas").asInt() >= 4, respuesta.path("descartadas").toString());
    }

    @Test
    void sinCoincidenciasNoHayRecomendacionYSeExplicaPorQue() throws Exception {
        String soloOtros = """
                {"shopping_results":[
                  {"title":"Consola De Videojuegos Portátil Q21 Pantalla 3.5 Pulgadas 10000 Juegos Retro",
                   "product_id":"q21","source":"%s","price":"$ 89.900","extracted_price":89900},
                  {"title":"Control DualSense PS5 Inalámbrico Blanco","product_id":"control",
                   "source":"Éxito","price":"$ 289.900","extracted_price":289900}
                ]}""".formatted(MARCADOR);

        JsonNode respuesta = buscar(servicio(soloOtros), "playstation 5");

        assertEquals(0, respuesta.path("total_resultados").asInt());
        assertTrue(respuesta.path("producto_mvp").isNull());
        assertEquals(0, respuesta.path("alternativas").size());
        assertEquals(2, respuesta.path("descartadas").asInt());
        String motivo = respuesta.path("motivo_sin_recomendacion").asText();
        assertTrue(motivo.contains("ninguna es lo que buscó"), motivo);
    }

    @Test
    void siGeminiEligeLaQ21ComoExactaLaValidacionFinalLaQuita() throws Exception {
        ServicioBusqueda servicio = servicio(RESPUESTA);
        ServicioIABusqueda ia = mock(ServicioIABusqueda.class);
        when(ia.analizar(any(), any())).thenAnswer(invocacion -> {
            List<OfertaNormalizada> recibidas = invocacion.getArgument(1);
            // Gemini mezcla: la Q21 y la PS5 como exactas, el control como parecido.
            OfertaNormalizada q21 = recibidas.stream().filter(o -> o.getTitulo().contains("Q21")).findFirst().orElseThrow();
            OfertaNormalizada ps5 = recibidas.stream().filter(o -> o.getTitulo().contains("Estándar")).findFirst()
                    .orElseThrow();
            OfertaNormalizada control = recibidas.stream().filter(o -> o.getTitulo().contains("DualSense")).findFirst()
                    .orElseThrow();
            return Optional.of(new ServicioIABusqueda.Analisis(List.of(q21, ps5), List.of(control),
                    "La más barata es la consola Q21 a $ 89.900; la PS5 Slim cuesta $ 2.599.900 en Alkosto."));
        });
        servicio.usarIA(ia);

        JsonNode respuesta = buscar(servicio, "playstation 5");

        assertEquals(List.of("Consola PlayStation 5 Slim 1 TB Estándar"), titulos(respuesta.path("ofertas")));
        assertNotNull(respuesta.path("producto_mvp").path("oferta"));
        assertFalse(respuesta.toString().contains("Q21"), respuesta.toString());
        assertTrue(respuesta.path("resumen_ia").isNull(), "el resumen nombra un producto descartado");
    }
}
