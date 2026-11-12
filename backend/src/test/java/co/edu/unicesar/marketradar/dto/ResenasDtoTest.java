package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.Idioma;
import co.edu.unicesar.marketradar.domain.ResenasProducto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResenasDtoTest {

    private final ResenasProducto producto = new ResenasProducto(new BigDecimal("3.8"), 160, List.of(), List.of(
            new ResenasProducto.Resena("Not a fresh product.", "This product is not new. The packing was broken.",
                    "sreerag k.", "noon.com", new BigDecimal("4"), "Hace 2 meses"),
            new ResenasProducto.Resena(null, "Muy buena consola, llegó rápido y funciona perfecto.", "Ana", null,
                    new BigDecimal("5"), null),
            new ResenasProducto.Resena(null, "Top!", "Leo", null, null, null)));

    @Test
    void ofreceTraducirSoloLasResenasEnOtroIdioma() {
        ResenasDto dto = ResenasDto.desde(producto, Idioma.ES, true);

        assertEquals("es", dto.idiomaUsuario());
        assertEquals("español", dto.nombreIdiomaUsuario());
        assertEquals(0, dto.resenas().get(0).indice());
        assertEquals("en", dto.resenas().get(0).idioma());
        assertTrue(dto.resenas().get(0).traducible());
        assertEquals("es", dto.resenas().get(1).idioma());
        assertFalse(dto.resenas().get(1).traducible());
        // Muy corta para saber el idioma: no se ofrece traducir.
        assertNull(dto.resenas().get(2).idioma());
        assertFalse(dto.resenas().get(2).traducible());
    }

    @Test
    void siLaCuentaEstaEnInglesSeOfreceTraducirLaDeEspanol() {
        ResenasDto dto = ResenasDto.desde(producto, Idioma.EN, true);
        assertFalse(dto.resenas().get(0).traducible());
        assertTrue(dto.resenas().get(1).traducible());
    }

    @Test
    void sinTraduccionDisponibleNoSeOfreceElBoton() {
        ResenasDto dto = ResenasDto.desde(producto, Idioma.ES, false);
        assertTrue(dto.resenas().stream().noneMatch(ResenasDto.ResenaDto::traducible));
    }

    @Test
    void explicaPorQueElTotalNoCuadraConLasEstrellasNiConLasOpinionesMostradas() {
        ResenasProducto distinto = new ResenasProducto(new BigDecimal("4.5"), 312,
                List.of(new ResenasProducto.Estrellas(5, 200), new ResenasProducto.Estrellas(4, 90)),
                producto.resenas());

        assertEquals("El total de 312 opiniones y la suma de las estrellas (290) vienen de conteos distintos de "
                + "Google; se muestran 3 de las 312 opiniones.", ResenasDto.notaConteo(distinto));
    }

    @Test
    void siTodoCuadraNoHayNota() {
        ResenasProducto cuadra = new ResenasProducto(new BigDecimal("4.5"), 3,
                List.of(new ResenasProducto.Estrellas(5, 2), new ResenasProducto.Estrellas(4, 1)), producto.resenas());

        assertNull(ResenasDto.notaConteo(cuadra));
    }

    @Test
    void lasOpinionesRechazadasNoSeMuestranYSeDicePorQue() {
        ResenasDto dto = ResenasDto.rechazadas(Idioma.ES, "Google entregó opiniones de otro modelo.");

        assertFalse(dto.disponibles());
        assertTrue(dto.resenas().isEmpty());
        assertEquals("rechazada", dto.procedencia());
        assertEquals("Google entregó opiniones de otro modelo.", dto.notaProcedencia());
    }
}
