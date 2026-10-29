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
        when(ofertaDao.buscarMasBarataPorTitulo(anyString()))
                .thenReturn(Optional.of(new OfertaDestacada(oferta("90000"), "mercadolibre", null)));
        when(ofertaDao.buscarMayorBajaDePrecio(anyInt()))
                .thenReturn(Optional.of(new OfertaDestacada(oferta("90000"), "mercadolibre", new BigDecimal("120000"))));

        List<Aviso> avisos = servicio.avisosPara(1L);

        assertEquals(List.of("SEGUIMIENTO", "BAJA_PRECIO", "INVITACION"), avisos.stream().map(Aviso::tipo).toList());
        assertEquals("tensiómetro", avisos.get(0).terminoBusqueda());
        assertEquals("Tensiómetro de brazo bajó $30.000 y ahora cuesta $90.000.", avisos.get(1).mensaje());
        assertEquals("https://ejemplo.com/t", avisos.get(1).enlace());
    }

    private static Oferta oferta(String precio) {
        Oferta oferta = new Oferta();
        oferta.setTitulo("Tensiómetro de brazo");
        oferta.setPrecioActual(new BigDecimal(precio));
        oferta.setUrlDestino("https://ejemplo.com/t");
        return oferta;
    }
}
