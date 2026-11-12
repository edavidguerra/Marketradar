package co.edu.unicesar.marketradar.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CondicionProductoTest {

    @ParameterizedTest(name = "«{0}» es {1}")
    @CsvSource(delimiter = '|', value = {
        "iPhone 13 128 GB Nuevo Sellado            | NUEVO",
        "iPhone 13 Reacondicionado como nuevo      | REACONDICIONADO",
        "Apple iPhone 13 (Renewed) Refurbished     | REACONDICIONADO",
        "Televisor LG de exhibición, como nuevo    | EXHIBICION",
        "Nevera Haceb open box                     | EXHIBICION",
        "iPhone 12 usado en buen estado            | USADO",
        "Celular seminuevo                         | USADO",
        "Portátil como nuevo                       | USADO",
        "PS5 Pre-Owned                             | USADO",
    })
    void distingueNuevoUsadoReacondicionadoYExhibicion(String texto, CondicionProducto esperada) {
        assertEquals(Optional.of(esperada), CondicionProducto.detectar(texto));
    }

    @Test
    void siNoDiceNadaNoSeSupone() {
        assertEquals(Optional.empty(), CondicionProducto.detectar("Apple iPhone 13 128 GB Azul", null, ""));
    }

    @Test
    void laEtiquetaDeLaTiendaCuentaAunqueElTituloNoLaDiga() {
        assertEquals(Optional.of(CondicionProducto.REACONDICIONADO),
                CondicionProducto.detectar("refurbished", "Apple iPhone 13 128 GB"));
    }

    @Test
    void seLeeDelCodigoGuardado() {
        assertEquals(Optional.of(CondicionProducto.EXHIBICION), CondicionProducto.deCodigo("exhibicion"));
        assertEquals(Optional.empty(), CondicionProducto.deCodigo(null));
        assertEquals(Optional.empty(), CondicionProducto.deCodigo("otra"));
    }
}
