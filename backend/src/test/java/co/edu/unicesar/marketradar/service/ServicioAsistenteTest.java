package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.asistente.MensajeChat;
import co.edu.unicesar.marketradar.asistente.ProveedorIA;
import co.edu.unicesar.marketradar.asistente.RolMensaje;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioAsistenteTest {

    private final ProveedorIA proveedor = mock(ProveedorIA.class);
    private final ServicioAsistente servicio = new ServicioAsistente(proveedor);

    @Test
    void agregaInstruccionesYLimitaElHistorial() {
        when(proveedor.responder(any())).thenReturn("Hola");
        List<MensajeChat> historial = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            historial.add(new MensajeChat(RolMensaje.USUARIO, "mensaje " + i));
        }

        assertEquals("Hola", servicio.responder(historial, "¿Dónde está más barato?"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MensajeChat>> captor = ArgumentCaptor.forClass(List.class);
        verify(proveedor).responder(captor.capture());
        List<MensajeChat> enviada = captor.getValue();
        assertEquals(RolMensaje.SISTEMA, enviada.get(0).getRol());
        assertEquals(ServicioAsistente.MENSAJES_DE_CONTEXTO + 2, enviada.size());
    }

    @Test
    void rechazaMensajeVacio() {
        assertThrows(ValidacionException.class, () -> servicio.responder(List.of(), "   "));
    }

    @Test
    void rechazaMensajeMuyLargo() {
        String largo = "a".repeat(ServicioAsistente.LONGITUD_MAXIMA_MENSAJE + 1);
        assertThrows(ValidacionException.class, () -> servicio.responder(List.of(), largo));
    }
}
