package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.PqrDao;
import co.edu.unicesar.marketradar.domain.EstadoPqr;
import co.edu.unicesar.marketradar.domain.Pqr;
import co.edu.unicesar.marketradar.domain.TipoPqr;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioPqrTest {

    private static final String DESCRIPCION = "El precio que vi en Marketradar no coincide con el de la tienda.";

    private PqrDao pqrDao;
    private ServicioPqr servicio;

    @BeforeEach
    void preparar() {
        pqrDao = mock(PqrDao.class);
        when(pqrDao.insertar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        servicio = new ServicioPqr(pqrDao);
    }

    @Test
    void radicaUnaQuejaValida() {
        Pqr pqr = servicio.radicar(7L, "queja", "Precio distinto", DESCRIPCION);

        assertEquals(TipoPqr.QUEJA, pqr.getTipo());
        assertEquals(EstadoPqr.RADICADA, pqr.getEstado());
        assertEquals(7L, pqr.getUsuarioId());
    }

    @Test
    void rechazaDescripcionMuyCorta() {
        assertThrows(ValidacionException.class, () -> servicio.radicar(7L, "QUEJA", "Precio distinto", "Mal"));
        verify(pqrDao, never()).insertar(any());
    }

    @Test
    void limitaLasSolicitudesSinRespuesta() {
        when(pqrDao.listarPorUsuario(7L)).thenReturn(Collections.nCopies(ServicioPqr.MAXIMO_ABIERTAS,
                new Pqr(7L, TipoPqr.PETICION, "Otra", DESCRIPCION)));
        assertThrows(ValidacionException.class, () -> servicio.radicar(7L, "PETICION", "Una más", DESCRIPCION));
    }

    @Test
    void abrirMarcaEnProcesoYResponderGuardaAlAdministrador() {
        Pqr pqr = new Pqr(7L, TipoPqr.SUGERENCIA, "Letra grande", DESCRIPCION);
        pqr.setId(3L);
        when(pqrDao.buscarPorId(3L)).thenReturn(Optional.of(pqr));

        assertEquals(EstadoPqr.EN_PROCESO, servicio.abrir(3L).getEstado());
        Pqr respondida = servicio.responder(3L, 1L, "Gracias, lo tendremos en cuenta.");

        assertEquals(EstadoPqr.RESPONDIDA, respondida.getEstado());
        assertEquals(1L, respondida.getRespondidaPor());
        assertNotNull(respondida.getRespondidaEn());
        assertThrows(ValidacionException.class, () -> servicio.responder(3L, 1L, "Segunda respuesta igual"));
    }

    @Test
    void filtraPorEstadoParaElAdministrador() {
        when(pqrDao.listarPorEstado(EstadoPqr.RADICADA)).thenReturn(List.of());
        assertEquals(List.of(), servicio.listarParaAdministrador("radicada"));
        assertThrows(ValidacionException.class, () -> servicio.listarParaAdministrador("cerrada"));
    }
}
