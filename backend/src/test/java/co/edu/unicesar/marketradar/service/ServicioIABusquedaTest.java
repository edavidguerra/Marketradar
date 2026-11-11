package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.ia.ClienteGemini;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioIABusquedaTest {

    private final ObjectMapper json = new ObjectMapper();
    private ClienteGemini gemini;
    private ServicioIABusqueda ia;

    private final List<OfertaNormalizada> ofertas = List.of(
            oferta("Case Glitter iPhone 11", "ICase Colombia", "30000"),
            oferta("Apple iPhone 15 128 GB Negro", "Falabella", "3899900"),
            oferta("Vidrio templado iPhone 15", "Phone Fox", "20000"),
            oferta("iPhone 15 Pro 256 GB", "Éxito", "5599900"),
            oferta("Apple iPhone 15 128 GB Azul", "Alkosto", "3799900"));

    @BeforeEach
    void preparar() {
        gemini = mock(ClienteGemini.class);
        when(gemini.estaHabilitado()).thenReturn(true);
        when(gemini.nuevoEsquema()).thenAnswer(invocacion -> json.createObjectNode());
        ia = new ServicioIABusqueda(gemini);
    }

    private void responde(String respuesta) throws Exception {
        JsonNode nodo = json.readTree(respuesta);
        when(gemini.pedirJson(anyString(), anyString(), any(ObjectNode.class))).thenReturn(Optional.of(nodo));
    }

    @Test
    void entiendeLaFraseYLaRecuerda() throws Exception {
        responde("{\"consulta\": \"  PlayStation 5 \"}");

        assertEquals("playstation 5", ia.interpretar("quiero un ps5 pa mi nieto").orElseThrow());
        assertEquals("playstation 5", ia.interpretar("Quiero un PS5 pa mi nieto").orElseThrow());
        verify(gemini, times(1)).pedirJson(anyString(), anyString(), any());
    }

    @Test
    void dejaSoloLosProductosQueSonLoBuscadoYElResumen() throws Exception {
        responde("""
                {"exactos": [5, 2, 99, 0], "parecidos": [4],
                 "resumen": "Encontré el iPhone 15 desde $ 3.799.900 en Alkosto.\\nRevise que sea nuevo."}""");

        ServicioIABusqueda.Analisis analisis = ia.analizar("iphone 15", ofertas).orElseThrow();

        // Pocos exactos: se suman los parecidos. Se respeta el orden de la tienda y se ignoran números inválidos.
        assertEquals(List.of("Apple iPhone 15 128 GB Negro", "iPhone 15 Pro 256 GB", "Apple iPhone 15 128 GB Azul"),
                analisis.elegidas().stream().map(OfertaNormalizada::getTitulo).toList());
        assertEquals("Encontré el iPhone 15 desde $ 3.799.900 en Alkosto. Revise que sea nuevo.", analisis.resumen());
    }

    @Test
    void conSuficientesExactosNoAgregaParecidos() throws Exception {
        responde("{\"exactos\": [1, 2, 3], \"parecidos\": [4, 5], \"resumen\": \"\"}");

        ServicioIABusqueda.Analisis analisis = ia.analizar("x", ofertas).orElseThrow();

        assertEquals(3, analisis.elegidas().size());
        assertNull(analisis.resumen());
    }

    @Test
    void leEnviaATodosLosProductosNumeradosConTiendaYPrecio() {
        String lista = ServicioIABusqueda.lista("iphone 15", ofertas);

        assertTrue(lista.startsWith("Búsqueda: iphone 15\nProductos:\n1 | Case Glitter iPhone 11 | ICase Colombia | $ 30.000\n"));
        assertTrue(lista.contains("5 | Apple iPhone 15 128 GB Azul | Alkosto | $ 3.799.900\n"));
    }

    @Test
    void sinGeminiNoHaceNada() {
        when(gemini.estaHabilitado()).thenReturn(false);

        assertTrue(ia.interpretar("licuadora").isEmpty());
        assertTrue(ia.analizar("licuadora", ofertas).isEmpty());
        verify(gemini, never()).pedirJson(anyString(), anyString(), any());
    }

    @Test
    void unaRespuestaRaraSeDescarta() throws Exception {
        responde("{\"consulta\": \"\"}");
        assertTrue(ia.interpretar("lupa").isEmpty());
        when(gemini.pedirJson(anyString(), eq("otra"), any())).thenReturn(Optional.empty());
        assertTrue(ia.interpretar("otra").isEmpty());
    }

    private static OfertaNormalizada oferta(String titulo, String tienda, String precio) {
        return OfertaNormalizada.builder()
                .codigoMarketplace("googleshopping").idExterno(titulo).titulo(titulo)
                .precio(new BigDecimal(precio)).moneda("COP").condicion("nuevo").vendedor(tienda)
                .urlDestino("https://ejemplo.com/" + Math.abs(titulo.hashCode()))
                .build();
    }
}
