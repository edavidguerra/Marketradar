package co.edu.unicesar.marketradar.validacion;

import co.edu.unicesar.marketradar.domain.TipoDocumento;
import co.edu.unicesar.marketradar.domain.TipoPqr;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidadorCamposTest {

    private static final Clock RELOJ = Clock.fixed(LocalDate.of(2026, 10, 28).atStartOfDay(ZoneId.of("America/Bogota"))
            .toInstant(), ZoneId.of("America/Bogota"));

    @Test
    void normalizaElCorreo() {
        assertEquals("ana.perez@correo.com", ValidadorCampos.correo("  Ana.Perez@Correo.COM "));
        assertThrows(ValidacionException.class, () -> ValidadorCampos.correo("ana@correo"));
        assertThrows(ValidacionException.class, () -> ValidadorCampos.correo(""));
    }

    @Test
    void aceptaNombresConTildesYRechazaNumeros() {
        assertEquals("María José", ValidadorCampos.nombre(" María   José ", "los nombres"));
        assertEquals("Peña-Ochoa", ValidadorCampos.nombre("Peña-Ochoa", "los apellidos"));
        assertThrows(ValidacionException.class, () -> ValidadorCampos.nombre("Ana3", "los nombres"));
        assertThrows(ValidacionException.class, () -> ValidadorCampos.nombre("A", "los nombres"));
    }

    @Test
    void validaDocumentoSegunElTipo() {
        assertEquals("1065123456", ValidadorCampos.numeroDocumento(TipoDocumento.CC, "1.065.123.456"));
        assertEquals("AB123456", ValidadorCampos.numeroDocumento(TipoDocumento.PA, "ab123456"));
        assertThrows(ValidacionException.class, () -> ValidadorCampos.numeroDocumento(TipoDocumento.CC, "12AB45"));
        assertThrows(ValidacionException.class, () -> ValidadorCampos.numeroDocumento(null, "123456"));
        assertNull(ValidadorCampos.numeroDocumento(null, " "));
    }

    @Test
    void validaCelularColombiano() {
        assertEquals("3001234567", ValidadorCampos.celular("+57 300 123 4567"));
        assertThrows(ValidacionException.class, () -> ValidadorCampos.celular("6055701234"));
        assertNull(ValidadorCampos.celular(""));
    }

    @Test
    void exigeMayoriaDeEdad() {
        assertEquals(LocalDate.of(1955, 3, 1), ValidadorCampos.fechaNacimiento(LocalDate.of(1955, 3, 1), RELOJ));
        assertThrows(ValidacionException.class,
                () -> ValidadorCampos.fechaNacimiento(LocalDate.of(2010, 1, 1), RELOJ));
        assertThrows(ValidacionException.class,
                () -> ValidadorCampos.fechaNacimiento(LocalDate.of(2027, 1, 1), RELOJ));
        assertThrows(ValidacionException.class,
                () -> ValidadorCampos.fechaNacimiento(LocalDate.of(1890, 1, 1), RELOJ));
    }

    @Test
    void validaOpcionesDeLista() {
        assertEquals(TipoPqr.QUEJA, ValidadorCampos.opcion("queja", TipoPqr.class, "el tipo"));
        assertThrows(ValidacionException.class, () -> ValidadorCampos.opcion("otro", TipoPqr.class, "el tipo"));
    }

    @Test
    void validaLongitudDeTextos() {
        assertThrows(ValidacionException.class, () -> ValidadorCampos.texto("Hola", "el asunto", 5, 120));
        assertEquals("Precio errado", ValidadorCampos.texto(" Precio errado ", "el asunto", 5, 120));
    }
}
