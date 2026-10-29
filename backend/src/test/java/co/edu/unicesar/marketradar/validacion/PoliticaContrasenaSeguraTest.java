package co.edu.unicesar.marketradar.validacion;

import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticaContrasenaSeguraTest {

    private final PoliticaContrasena politica = new PoliticaContrasenaSegura();

    @Test
    void aceptaContrasenaConTodosLosTiposDeCaracter() {
        assertDoesNotThrow(() -> politica.validar("Radar#2026", "ana@correo.com"));
        assertTrue(politica.reglasIncumplidas("Ñandú*99x", "ana@correo.com").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "radar#2026, mayúscula",
            "RADAR#2026, minúscula",
            "Radar#abcd, número",
            "Radar2026x, especial",
            "Ra#1, 8 y 64",
            "'Radar #2026', espacios"
    })
    void informaLaReglaIncumplida(String clave, String regla) {
        assertTrue(politica.reglasIncumplidas(clave, "ana@correo.com").stream().anyMatch(r -> r.contains(regla)),
                () -> "Se esperaba la regla '" + regla + "' para " + clave);
    }

    @Test
    void rechazaContrasenaQueContieneElCorreo() {
        assertEquals(1, politica.reglasIncumplidas("Mariela#2026", "mariela@correo.com").size());
    }

    @Test
    void rechazaContrasenaVacia() {
        assertThrows(ValidacionException.class, () -> politica.validar(null, "ana@correo.com"));
    }
}
