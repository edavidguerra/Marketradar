package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.adapter.tasas.FuenteTasaCambio;
import co.edu.unicesar.marketradar.dao.TasaCambioDao;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioTasasCambioTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);

    private final TasaCambioDao dao = mock(TasaCambioDao.class);

    @Test
    void laTrmTienePrioridadSobreLaTasaDeMercado() {
        ServicioTasasCambio servicio = new ServicioTasasCambio(List.of(
                fuente(new TasaCambio("USD", new BigDecimal("4150"), HOY, "TRM")),
                fuente(new TasaCambio("USD", new BigDecimal("4170"), HOY, "mercado"),
                        new TasaCambio("EUR", new BigDecimal("4800"), HOY, "mercado"))),
                dao, PropiedadesPrueba.crear());

        servicio.actualizar();

        assertEquals(new BigDecimal("4150"), servicio.tasaVigente("USD").orElseThrow().valor());
        assertEquals(new BigDecimal("4800"), servicio.tasaVigente("EUR").orElseThrow().valor());
        verify(dao, times(2)).guardar(any());
    }

    @Test
    void siLaFuenteFallaUsaLaUltimaGuardada() {
        FuenteTasaCambio caida = mock(FuenteTasaCambio.class);
        when(caida.nombre()).thenReturn("caída");
        when(caida.consultar()).thenThrow(new IllegalStateException("sin conexión"));
        when(dao.buscarVigente("USD"))
                .thenReturn(Optional.of(new TasaCambio("USD", new BigDecimal("4100"), HOY.minusDays(1), "TRM")));
        ServicioTasasCambio servicio = new ServicioTasasCambio(List.of(caida), dao, PropiedadesPrueba.crear());

        servicio.actualizar();

        assertEquals(new BigDecimal("4100"), servicio.tasaVigente("USD").orElseThrow().valor());
    }

    @Test
    void sinNadaGuardadoUsaLaTasaDeRespaldoSoloParaDolares() {
        when(dao.buscarVigente(any())).thenReturn(Optional.empty());
        ServicioTasasCambio servicio = new ServicioTasasCambio(List.of(), dao, PropiedadesPrueba.crear());

        TasaCambio respaldo = servicio.tasaVigente("USD").orElseThrow();
        assertEquals(new BigDecimal("4000"), respaldo.valor());
        assertEquals(ServicioTasasCambio.FUENTE_RESPALDO, respaldo.fuente());
        assertTrue(servicio.tasaVigente("JPY").isEmpty());
    }

    private static FuenteTasaCambio fuente(TasaCambio... tasas) {
        FuenteTasaCambio fuente = mock(FuenteTasaCambio.class);
        when(fuente.nombre()).thenReturn("prueba");
        when(fuente.consultar()).thenReturn(List.of(tasas));
        return fuente;
    }
}
