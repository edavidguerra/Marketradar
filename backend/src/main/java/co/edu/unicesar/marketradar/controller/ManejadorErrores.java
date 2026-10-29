package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.dto.ErrorRespuestaDto;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;
import co.edu.unicesar.marketradar.exception.CredencialesInvalidasException;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Convierte las excepciones en respuestas con formato uniforme y mensajes en español sencillo,
 * sin exponer trazas al usuario.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    private final boolean modoDesarrollo;

    public ManejadorErrores(PropiedadesMarketradar propiedades) {
        this.modoDesarrollo = propiedades.modoDesarrollo();
    }

    @ExceptionHandler(ValidacionException.class)
    public ResponseEntity<ErrorRespuestaDto> validacion(ValidacionException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "VALIDACION", e.getMessage(), e);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorRespuestaDto> parametroInvalido(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "Algún dato de la consulta no es válido. Revise e intente de nuevo.", e);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorRespuestaDto> credenciales(CredencialesInvalidasException e) {
        return e.isBloqueada()
                ? respuesta(HttpStatus.TOO_MANY_REQUESTS, "CUENTA_BLOQUEADA", e.getMessage(), e)
                : respuesta(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", e.getMessage(), e);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorRespuestaDto> noEncontrado(RecursoNoEncontradoException e) {
        return respuesta(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", e.getMessage(), e);
    }

    @ExceptionHandler(AccesoDatosException.class)
    public ResponseEntity<ErrorRespuestaDto> accesoDatos(AccesoDatosException e) {
        LOG.error("Error de acceso a datos", e);
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "DATOS_NO_DISPONIBLES",
                "No pudimos consultar la información en este momento. Intente de nuevo en unos minutos.", e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuestaDto> inesperado(Exception e) {
        LOG.error("Error inesperado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Algo salió mal. Intente de nuevo; si sigue pasando, vuelva más tarde.", e);
    }

    private ResponseEntity<ErrorRespuestaDto> respuesta(HttpStatus estado, String codigo, String mensajeUsuario,
                                                        Exception e) {
        String detalle = modoDesarrollo ? e.getClass().getSimpleName() + ": " + e.getMessage() : null;
        return ResponseEntity.status(estado).body(new ErrorRespuestaDto(codigo, mensajeUsuario, detalle));
    }
}
