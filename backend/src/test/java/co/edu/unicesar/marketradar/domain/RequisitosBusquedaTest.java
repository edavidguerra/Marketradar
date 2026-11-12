package co.edu.unicesar.marketradar.domain;

import co.edu.unicesar.marketradar.domain.RequisitosBusqueda.Nivel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequisitosBusquedaTest {

    private static final Optional<CondicionProducto> NUEVO = Optional.of(CondicionProducto.NUEVO);
    private static final Optional<CondicionProducto> SIN_DECIR = Optional.empty();

    @Test
    void leeModeloCapacidadYCondicionDeLoQueEscribio() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("iPhone 13 128 GB nuevo");

        assertEquals(List.of("modelo iphone 13", "128 GB", "nuevo"), requisitos.etiquetas());
    }

    @Test
    void unaBusquedaSinDetallesNoExigeNada() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("licuadora");

        assertTrue(requisitos.estaVacio());
        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Licuadora Oster 10 velocidades", SIN_DECIR).nivel());
    }

    @ParameterizedTest(name = "{0} con «{1}»: {2}")
    @CsvSource(delimiter = '|', value = {
        "iphone 13 128 gb nuevo | Apple iPhone 13 128 GB Azul Nuevo          | CUMPLE",
        "iphone 13 128 gb nuevo | Apple iPhone 13 Pro 128 GB                 | INCUMPLE",
        "iphone 13 128 gb nuevo | Apple iPhone 13 mini 128 GB                | INCUMPLE",
        "iphone 13 128 gb nuevo | Apple iPhone 13 256 GB                     | INCUMPLE",
        "iphone 13 128 gb nuevo | Apple iPhone 14 128 GB                     | INCUMPLE",
        "iphone 13 128 gb nuevo | Apple iPhone 13 Azul                       | SIN_CONFIRMAR",
        "iphone 13 128 gb nuevo | Apple iPhone 13 128 GB Reacondicionado     | INCUMPLE",
        "iphone 13 128 gb nuevo | iPhone 13 128 GB de exhibición             | INCUMPLE",
        "iphone 13 128 gb nuevo | iPhone 13 128GB usado como nuevo           | INCUMPLE",
        "iphone 17 pro          | Apple iPhone 17 Pro 256 GB                 | CUMPLE",
        "iphone 17 pro          | Apple iPhone 17 Pro Max 256 GB             | INCUMPLE",
        "iphone 17 pro          | Apple iPhone 17 256 GB                     | INCUMPLE",
        "iphone 17 pro max      | iPhone 17 Pro Max 512 GB Naranja           | CUMPLE",
        "iphone 17 pro max      | iPhone 17 Pro 512 GB                       | INCUMPLE",
        "galaxy s24 ultra       | Samsung Galaxy S24 Ultra 256 GB            | CUMPLE",
        "galaxy s24 ultra       | Samsung Galaxy S24+ 256 GB                 | INCUMPLE",
        "galaxy s24             | Samsung Galaxy S23 128 GB                  | INCUMPLE",
        "ps5                    | Consola PlayStation 5 Slim                 | SIN_CONFIRMAR",
        "ps5                    | Consola PS5 Slim 1 TB                      | CUMPLE",
        "portatil 1 tb          | Portátil Lenovo 1TB SSD                    | CUMPLE",
        "portatil 1 tb          | Portátil Lenovo 512 GB SSD                 | INCUMPLE",
        "iphone 15              | Combo iPhone 15 128 GB + AirPods           | INCUMPLE",
    })
    void comparaCadaOfertaConLoQuePidio(String busqueda, String titulo, Nivel esperado) {
        Optional<CondicionProducto> condicion = CondicionProducto.detectar(titulo);

        RequisitosBusqueda.Evaluacion evaluacion = RequisitosBusqueda.de(busqueda).evaluar(titulo, condicion);

        assertEquals(esperado, evaluacion.nivel(), evaluacion.motivos().toString());
    }

    @Test
    void siLaTiendaNoDiceLaCondicionNoSeDaPorNuevo() {
        RequisitosBusqueda.Evaluacion evaluacion = RequisitosBusqueda.de("iphone 13 128 gb nuevo")
                .evaluar("Apple iPhone 13 128 GB Azul", SIN_DECIR);

        assertEquals(Nivel.SIN_CONFIRMAR, evaluacion.nivel());
        assertEquals(List.of("La tienda no dice si es nuevo"), evaluacion.motivos());
    }

    @Test
    void cadaMotivoDiceQueLeFalta() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("iphone 13 128 gb nuevo");

        assertEquals(List.of("Es la versión Pro, no la que pidió"),
                requisitos.evaluar("Apple iPhone 13 Pro 128 GB", NUEVO).motivos());
        assertEquals(List.of("Es de 256 GB, no de 128 GB"),
                requisitos.evaluar("Apple iPhone 13 256 GB", NUEVO).motivos());
        assertEquals(List.of("Es reacondicionado, no nuevo"),
                requisitos.evaluar("Apple iPhone 13 128 GB", Optional.of(CondicionProducto.REACONDICIONADO)).motivos());
        assertEquals(List.of("No es la versión Pro"),
                RequisitosBusqueda.de("iphone 17 pro").evaluar("Apple iPhone 17 256 GB", NUEVO).motivos());
    }

    @Test
    void laCorreccionNoBorraLoQuePidioLaPersona() {
        // La corrección quitó "128 gb nuevo" y cambió "ps5" por "playstation 5".
        RequisitosBusqueda requisitos = RequisitosBusqueda.combinar("ps5 825 gb nueva", "playstation 5");

        assertEquals(List.of("modelo playstation 5", "825 GB", "nuevo"), requisitos.etiquetas());
        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Consola PlayStation 5 825 GB nueva", NUEVO).nivel());
        assertEquals(Nivel.INCUMPLE, requisitos.evaluar("Consola PlayStation 5 1 TB", NUEVO).nivel());
    }

    @Test
    void siLaCorreccionCambiaElNumeroSeExigenAmbos() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.combinar("iphone 13 pro", "iphone 14 pro");

        assertEquals(Nivel.INCUMPLE, requisitos.evaluar("Apple iPhone 14 Pro 128 GB", NUEVO).nivel());
    }

    @Test
    void quienPideUnComboPuedeVerCombos() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("combo iphone 15 con airpods");

        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Combo iPhone 15 128 GB + AirPods", NUEVO).nivel());
    }

    @Test
    void unNumeroQueEsParteDeOtraCosaNoEsUnModeloDistinto() {
        // "10 velocidades" no compite con el modelo "oster 6640".
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("licuadora oster 6640");

        assertEquals(Nivel.CUMPLE,
                requisitos.evaluar("Licuadora Oster 6640 de 10 velocidades 1.25 litros", SIN_DECIR).nivel());
    }
}
