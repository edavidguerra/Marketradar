package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.BusquedaDao;
import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.domain.Aviso;
import co.edu.unicesar.marketradar.domain.Busqueda;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.OfertaDestacada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicioAvisosTest {

    private final OfertaDao ofertaDao = mock(OfertaDao.class);
    private final BusquedaDao busquedaDao = mock(BusquedaDao.class);
    private final ServicioAvisos servicio = new ServicioAvisos(ofertaDao, busquedaDao);

    @Test
    void sinDatosSoloInvitaABuscar() {
        when(busquedaDao.listarPorUsuario(1L, 1)).thenReturn(List.of());
        when(ofertaDao.buscarMayorBajaDePrecio(anyInt())).thenReturn(Optional.empty());

        List<Aviso> avisos = servicio.avisosPara(1L);

        assertEquals(1, avisos.size());
        assertEquals("INVITACION", avisos.get(0).tipo());
    }

    @Test
    void recuerdaLaUltimaBusquedaYLasBajasDePrecio() {
        when(busquedaDao.listarPorUsuario(1L, 1)).thenReturn(List.of(new Busqueda("tensiómetro", 3, 800)));
        when(ofertaDao.listarMasBaratasPorTitulo(anyString(), anyInt()))
                .thenReturn(List.of(new OfertaDestacada(oferta("90000"), "mercadolibre", null)));
        when(ofertaDao.buscarMayorBajaDePrecio(anyInt()))
                .thenReturn(Optional.of(new OfertaDestacada(oferta("90000"), "mercadolibre", new BigDecimal("120000"))));

        List<Aviso> avisos = servicio.avisosPara(1L);

        assertEquals(List.of("SEGUIMIENTO", "BAJA_PRECIO", "INVITACION"), avisos.stream().map(Aviso::tipo).toList());
        assertEquals("tensiómetro", avisos.get(0).terminoBusqueda());
        assertEquals("Tensiómetro de brazo bajó $30.000 y ahora cuesta $90.000 más envío en la misma tienda.", avisos.get(1).mensaje());
        assertEquals("https://ejemplo.com/t", avisos.get(1).enlace());
    }

    @Test
    void elSeguimientoSaltaLasCajasLosAccesoriosYOtrasVersiones() {
        List<OfertaDestacada> candidatas = List.of(
                new OfertaDestacada(oferta("Caja vacía iPhone 15 Pro", "40000", "a"), "googleshopping", null),
                new OfertaDestacada(oferta("Funda para iPhone 15 Pro", "30000", "b"), "googleshopping", null),
                new OfertaDestacada(oferta("Apple iPhone 15 Pro Max 256 GB", "4000000", "c"), "googleshopping", null),
                new OfertaDestacada(oferta("Apple iPhone 15 Pro 128 GB", "3500000", "d"), "googleshopping", null));

        OfertaDestacada elegida = ServicioAvisos.elegirSeguimiento("iphone 15 pro", candidatas).orElseThrow();

        assertEquals("Apple iPhone 15 Pro 128 GB", elegida.oferta().getTitulo());
    }

    @Test
    void elSeguimientoNoUsaUnPrecioQueNoSePareceALosDemas() {
        List<OfertaDestacada> candidatas = List.of(
                new OfertaDestacada(oferta("Tensiómetro de brazo Omron", "9000", "a"), "googleshopping", null),
                new OfertaDestacada(oferta("Tensiómetro de brazo Beurer", "110000", "b"), "googleshopping", null),
                new OfertaDestacada(oferta("Tensiómetro de brazo Medisana", "120000", "c"), "googleshopping", null),
                new OfertaDestacada(oferta("Tensiómetro de brazo Citizen", "130000", "d"), "googleshopping", null));

        OfertaDestacada elegida = ServicioAvisos.elegirSeguimiento("tensiómetro de brazo", candidatas).orElseThrow();

        assertEquals("Tensiómetro de brazo Beurer", elegida.oferta().getTitulo());
    }

    @Test
    void elBotonDelAvisoDiceAdondeLleva() {
        assertEquals("Ver este producto en exito.com",
                ServicioAvisos.textoEnlace("https://www.exito.com/tensiometro/p", "Ver este producto"));
        assertEquals("Buscar esta oferta en Google",
                ServicioAvisos.textoEnlace("https://www.google.com.co/search?q=tensiometro", "Ver este producto"));
    }

    @Test
    void laCapacidadYLaCondicionNoSeMandanALaBase() {
        assertEquals("iphone 13", ServicioAvisos.palabrasParaLaBase("iphone 13 128 GB nuevo"));
    }

    private static Oferta oferta(String titulo, String precio, String id) {
        Oferta oferta = oferta(precio);
        oferta.setTitulo(titulo);
        oferta.setIdExterno(id);
        return oferta;
    }

    private static Oferta oferta(String precio) {
        Oferta oferta = new Oferta();
        oferta.setIdExterno("t");
        oferta.setMoneda("COP");
        oferta.setVendedor("Tienda Salud");
        oferta.setTitulo("Tensiómetro de brazo");
        oferta.setPrecioActual(new BigDecimal(precio));
        oferta.setUrlDestino("https://ejemplo.com/t");
        return oferta;
    }
}
