package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.Idioma;
import co.edu.unicesar.marketradar.domain.ResenasProducto;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ServicioNoDisponibleException;
import co.edu.unicesar.marketradar.ia.ClienteGemini;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioTraduccionTest {

    private final ObjectMapper json = new ObjectMapper();
    private ClienteGemini gemini;
    private ServicioResenas resenas;
    private ServicioTraduccion servicio;

    @BeforeEach
    void preparar() throws Exception {
        gemini = mock(ClienteGemini.class);
        resenas = mock(ServicioResenas.class);
        when(gemini.estaHabilitado()).thenReturn(true);
        when(gemini.nuevoEsquema()).thenAnswer(invocacion -> json.createObjectNode());
        when(resenas.obtener(7L)).thenReturn(Optional.of(new ResenasProducto(new BigDecimal("4"), 2, List.of(),
                List.of(new ResenasProducto.Resena("Not a fresh product.", "The packing was broken.", "sreerag k.",
                        "noon.com", new BigDecimal("4"), "Hace 2 meses")))));
        when(gemini.pedirJson(anyString(), anyString(), any(ObjectNode.class))).thenReturn(Optional.of(json.readTree(
                "{\"titulo\": \"No es un producto nuevo.\", \"texto\": \"El empaque llegó roto.\", "
                        + "\"idioma_original\": \"EN\"}")));
        servicio = new ServicioTraduccion(gemini, resenas);
    }

    @Test
    void traduceLaResenaAlIdiomaDeLaCuentaYLaGuarda() {
        ServicioTraduccion.Traduccion traduccion = servicio.traducir(1L, 7L, 0, Idioma.ES);

        assertEquals("No es un producto nuevo.", traduccion.titulo());
        assertEquals("El empaque llegó roto.", traduccion.texto());
        assertEquals("en", traduccion.idiomaOriginal());
        assertEquals("es", traduccion.idiomaDestino());

        ArgumentCaptor<String> instrucciones = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(gemini).pedirJson(instrucciones.capture(), texto.capture(), any());
        assertTrue(instrucciones.getValue().contains("al español"));
        assertEquals("Título: Not a fresh product.\nTexto: The packing was broken.", texto.getValue());

        servicio.traducir(2L, 7L, 0, Idioma.ES);
        verify(gemini, times(1)).pedirJson(anyString(), anyString(), any());
    }

    @Test
    void soloTraduceResenasQueExisten() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.traducir(1L, 7L, 5, Idioma.ES));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.traducir(1L, 7L, -1, Idioma.ES));
        verify(gemini, never()).pedirJson(anyString(), anyString(), any());
    }

    @Test
    void sinGeminiOSiFallaLoDiceConUnMensajeSencillo() {
        when(gemini.pedirJson(anyString(), anyString(), any())).thenReturn(Optional.empty());
        ServicioNoDisponibleException fallo = assertThrows(ServicioNoDisponibleException.class,
                () -> servicio.traducir(1L, 7L, 0, Idioma.PT));
        assertTrue(fallo.getMessage().startsWith("No pudimos traducir"));

        when(gemini.estaHabilitado()).thenReturn(false);
        assertThrows(ServicioNoDisponibleException.class, () -> servicio.traducir(1L, 7L, 0, Idioma.ES));
    }

    @Test
    void limitaCuantasTraduccionesPideUnaPersonaPorHora() {
        when(gemini.pedirJson(anyString(), anyString(), any())).thenReturn(Optional.empty());
        for (int i = 0; i < ServicioTraduccion.MAXIMO_POR_HORA; i++) {
            assertThrows(ServicioNoDisponibleException.class, () -> servicio.traducir(9L, 7L, 0, Idioma.ES));
        }
        ServicioNoDisponibleException limite = assertThrows(ServicioNoDisponibleException.class,
                () -> servicio.traducir(9L, 7L, 0, Idioma.ES));
        assertTrue(limite.getMessage().startsWith("Ya tradujo muchas reseñas"));
        verify(gemini, times(ServicioTraduccion.MAXIMO_POR_HORA)).pedirJson(anyString(), anyString(), any());
    }

    @Test
    void unaRespuestaSinTextoNoSirve() throws Exception {
        assertTrue(ServicioTraduccion.leer(json.readTree("{\"titulo\": \"x\", \"texto\": \"  \"}"), Idioma.ES)
                .isEmpty());
        ServicioTraduccion.Traduccion sinTitulo = ServicioTraduccion.leer(json.readTree(
                "{\"titulo\": \"\", \"texto\": \"Bueno\", \"idioma_original\": \"klingon\"}"), Idioma.ES).orElseThrow();
        assertNull(sinTitulo.titulo());
        assertNull(sinTitulo.idiomaOriginal());
    }
}
