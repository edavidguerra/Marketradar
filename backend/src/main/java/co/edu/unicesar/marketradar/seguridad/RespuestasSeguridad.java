package co.edu.unicesar.marketradar.seguridad;

import co.edu.unicesar.marketradar.dto.ErrorRespuestaDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Respuestas JSON en español sencillo cuando falta la sesión (401) o el rol no alcanza (403),
 * en lugar de la página de error por defecto.
 */
@Component
public class RespuestasSeguridad {

    private final ObjectMapper mapper;

    public RespuestasSeguridad(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @return manejador para peticiones sin sesión
     */
    public AuthenticationEntryPoint sinSesion() {
        return (peticion, respuesta, excepcion) -> escribir(respuesta, HttpStatus.UNAUTHORIZED, "SIN_SESION",
                "Para usar esta opción, ingrese con su correo y contraseña.");
    }

    /**
     * @return manejador para peticiones de un rol sin permiso o sin el token CSRF vigente
     */
    public AccessDeniedHandler sinPermiso() {
        return (peticion, respuesta, excepcion) -> {
            if (excepcion instanceof CsrfException) {
                escribir(respuesta, HttpStatus.FORBIDDEN, "PAGINA_VENCIDA",
                        "La página estuvo abierta mucho tiempo. Recárguela e intente de nuevo.");
            } else {
                escribir(respuesta, HttpStatus.FORBIDDEN, "SIN_PERMISO",
                        "Esta opción no está disponible para su tipo de cuenta.");
            }
        };
    }

    private void escribir(HttpServletResponse respuesta, HttpStatus estado, String codigo, String mensaje)
            throws IOException {
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        mapper.writeValue(respuesta.getWriter(), new ErrorRespuestaDto(codigo, mensaje, null));
    }
}
