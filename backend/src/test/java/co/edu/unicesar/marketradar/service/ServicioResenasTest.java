package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.adapter.googleshopping.AdaptadorGoogleShopping;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ResenasProducto;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioResenasTest {

    private static final ResenasProducto RESENAS = new ResenasProducto(new BigDecimal("4.5"), 20,
            List.of(new ResenasProducto.Estrellas(5, 15), new ResenasProducto.Estrellas(1, 5)),
            List.of(new ResenasProducto.Resena("Buena", "Funciona bien", "Luis", "Éxito", new BigDecimal("5"), null)));

    private AdaptadorGoogleShopping googleShopping;
    private ServicioResenas servicio;

    @BeforeEach
    void preparar() {
        googleShopping = mock(AdaptadorGoogleShopping.class);
        when(googleShopping.estaHabilitado()).thenReturn(true);
        servicio = new ServicioResenas(googleShopping);
    }

    @Test
    void pideLasResenasUnaSolaVezPorDia() {
        when(googleShopping.resenas("token-7")).thenReturn(RESENAS);
        servicio.recordar(resultado(oferta("token-7"), 7L));

        Optional<ResenasProducto> primera = servicio.obtener(7L);
        Optional<ResenasProducto> segunda = servicio.obtener(7L);

        assertTrue(servicio.disponibles(7L));
        assertEquals(RESENAS, primera.orElseThrow());
        assertEquals(primera, segunda);
        verify(googleShopping, times(1)).resenas("token-7"); // la segunda vez sale de la caché
    }

    @Test
    void sinResenasDeLaTiendaDevuelveVacioYTampocoRepiteLaConsulta() {
        when(googleShopping.resenas("token-8")).thenReturn(new ResenasProducto(null, null, List.of(), List.of()));
        servicio.recordar(resultado(oferta("token-8"), 8L));

        assertTrue(servicio.obtener(8L).isEmpty());
        assertTrue(servicio.obtener(8L).isEmpty());
        verify(googleShopping, times(1)).resenas("token-8");
    }

    @Test
    void unaOfertaQueNoSalioEnUnaBusquedaNoTieneResenas() {
        servicio.recordar(resultado(oferta(null), 9L));

        assertFalse(servicio.disponibles(9L));
        RecursoNoEncontradoException error = assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener(9L));
        assertTrue(error.getMessage().contains("Vuelva a buscarlo"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.obtener(404L));
        verify(googleShopping, never()).resenas(anyString());
    }

    @Test
    void lasOpinionesDelMismoModeloSeAceptanYSeDiceQueSonDelProducto() {
        servicio.recordar(resultado(celular("token-1", "Apple iPhone 13 128 GB Azul"), 1L));

        ServicioResenas.Procedencia procedencia = servicio.procedencia(1L, deProducto("Apple iPhone 13"));

        assertEquals(ServicioResenas.Procedencia.Estado.VERIFICADA, procedencia.estado());
        assertTrue(procedencia.nota().startsWith("Son opiniones del producto, reunidas por Google de varias tiendas; "
                + "no son opiniones sobre Éxito."), procedencia.nota());
        assertTrue(procedencia.nota().contains("pueden incluir otras capacidades o colores"), procedencia.nota());
    }

    @Test
    void lasOpinionesDeOtraVersionSeRechazan() {
        servicio.recordar(resultado(celular("token-2", "Apple iPhone 13 128 GB Azul"), 2L));

        ServicioResenas.Procedencia pro = servicio.procedencia(2L, deProducto("Apple iPhone 13 Pro Max"));
        ServicioResenas.Procedencia otro = servicio.procedencia(2L, deProducto("Apple iPhone 12"));

        assertEquals(ServicioResenas.Procedencia.Estado.RECHAZADA, pro.estado());
        assertTrue(pro.nota().contains("otro modelo o versión"), pro.nota());
        assertEquals(ServicioResenas.Procedencia.Estado.RECHAZADA, otro.estado());
    }

    @Test
    void siGoogleNoDiceDeQueProductoSonNoSeDanPorVerificadas() {
        servicio.recordar(resultado(celular("token-3", "Apple iPhone 13 128 GB Azul"), 3L));

        ServicioResenas.Procedencia procedencia = servicio.procedencia(3L, RESENAS);

        assertEquals(ServicioResenas.Procedencia.Estado.SIN_VERIFICAR, procedencia.estado());
        assertTrue(procedencia.nota().contains("pueden ser de otra versión"), procedencia.nota());
    }

    @Test
    void sinNumeroDeModeloSoloSeDiceAQueProductoLasAsociaGoogle() {
        servicio.recordar(resultado(oferta("token-4"), 4L));

        ServicioResenas.Procedencia procedencia = servicio.procedencia(4L, deProducto("Olla arrocera Imusa 1,8 L"));

        assertEquals(ServicioResenas.Procedencia.Estado.SIN_VERIFICAR, procedencia.estado());
        assertTrue(procedencia.nota().endsWith("Google las asocia a \"Olla arrocera Imusa 1,8 L\"."),
                procedencia.nota());
    }

    @Test
    void unaOpinionRepetidaEnDosSitiosSeMuestraUnaVez() {
        ResenasProducto.Resena luis = new ResenasProducto.Resena("Buena", "Funciona  bien", "Luis", "Éxito",
                new BigDecimal("5"), null);
        ResenasProducto.Resena copia = new ResenasProducto.Resena("Buena", "Funciona bien", "luis", "Alkosto",
                new BigDecimal("5"), null);

        ResenasProducto resenas = new ResenasProducto(new BigDecimal("5"), 2, List.of(), List.of(luis, copia));

        assertEquals(1, resenas.resenas().size());
    }

    private static ResenasProducto deProducto(String titulo) {
        return new ResenasProducto(RESENAS.calificacion(), RESENAS.totalResenas(), RESENAS.distribucion(),
                RESENAS.resenas(), titulo);
    }

    private static OfertaNormalizada celular(String token, String titulo) {
        return OfertaNormalizada.builder()
                .codigoMarketplace("googleshopping").idExterno("p-" + token).titulo(titulo)
                .precio(new BigDecimal("2899900")).moneda("COP").condicion("nuevo").vendedor("Éxito")
                .urlDestino("https://www.exito.com/iphone").tokenResenas(token)
                .build();
    }

    private static OfertaNormalizada oferta(String token) {
        return OfertaNormalizada.builder()
                .codigoMarketplace("googleshopping").idExterno("p-" + token).titulo("Olla arrocera")
                .precio(new BigDecimal("159900")).moneda("COP").condicion("nuevo").vendedor("Éxito")
                .urlDestino("https://www.exito.com/olla").tokenResenas(token)
                .build();
    }

    private static ResultadoBusqueda resultado(OfertaNormalizada oferta, long id) {
        return new ResultadoBusqueda(List.of(oferta), List.of(), 1, Map.of(oferta.clave(), id), oferta.costoConocido(), null);
    }
}
