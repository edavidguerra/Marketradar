package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.dto.ErrorRespuestaDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManejadorErroresTest {

    @Test
    void unArchivoQueNoExisteResponde404() {
        ManejadorErrores manejador = new ManejadorErrores(PropiedadesPrueba.crear());

        ResponseEntity<ErrorRespuestaDto> respuesta =
                manejador.rutaInexistente(new NoResourceFoundException(HttpMethod.GET, "favicon.ico"));

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
        assertEquals("NO_ENCONTRADO", respuesta.getBody().codigo());
    }
}
