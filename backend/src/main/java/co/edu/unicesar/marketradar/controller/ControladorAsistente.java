package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.asistente.MensajeChat;
import co.edu.unicesar.marketradar.asistente.RolMensaje;
import co.edu.unicesar.marketradar.dto.MensajeHistorialDto;
import co.edu.unicesar.marketradar.dto.RespuestaAsistenteDto;
import co.edu.unicesar.marketradar.dto.SolicitudAsistenteDto;
import co.edu.unicesar.marketradar.service.ServicioAsistente;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Recibe las preguntas al chatbot de IA y delega en {@link ServicioAsistente}.
 */
@RestController
@RequestMapping("/api/v1/asistente")
public class ControladorAsistente {

    private final ServicioAsistente servicioAsistente;

    public ControladorAsistente(ServicioAsistente servicioAsistente) {
        this.servicioAsistente = servicioAsistente;
    }

    /**
     * Envía una pregunta al asistente.
     *
     * @param solicitud pregunta nueva e historial de la conversación
     * @return respuesta del asistente
     */
    @PostMapping("/mensajes")
    public RespuestaAsistenteDto preguntar(@RequestBody SolicitudAsistenteDto solicitud) {
        List<MensajeChat> historial = solicitud.historial() == null ? List.of()
                : solicitud.historial().stream().map(ControladorAsistente::aMensaje).toList();
        String respuesta = servicioAsistente.responder(historial, solicitud.mensaje());
        return new RespuestaAsistenteDto(respuesta, servicioAsistente.estaDisponible());
    }

    private static MensajeChat aMensaje(MensajeHistorialDto dto) {
        RolMensaje rol = "asistente".equalsIgnoreCase(dto.rol()) ? RolMensaje.ASISTENTE : RolMensaje.USUARIO;
        return new MensajeChat(rol, dto.contenido() == null ? "" : dto.contenido());
    }
}
