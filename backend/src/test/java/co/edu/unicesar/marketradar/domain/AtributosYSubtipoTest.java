package co.edu.unicesar.marketradar.domain;

import co.edu.unicesar.marketradar.domain.RequisitosBusqueda.Evaluacion;
import co.edu.unicesar.marketradar.domain.RequisitosBusqueda.Nivel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que la persona pide de forma expresa además de la marca y el modelo: el subtipo ("olla arrocera", "freidora de
 * aire"), la conectividad y el color. Reconocer la marca no vuelve opcionales las demás palabras.
 */
class AtributosYSubtipoTest {

    private static final Optional<CondicionProducto> NUEVO = Optional.of(CondicionProducto.NUEVO);

    private static Evaluacion evaluar(String busqueda, String titulo) {
        return RequisitosBusqueda.de(busqueda).evaluar(titulo, NUEVO);
    }

    @ParameterizedTest(name = "{0} con «{1}»: {2}")
    @CsvSource(delimiter = '|', value = {
        "olla arrocera oster      | Licuadora Oster 600 W                     | INCUMPLE",
        "olla arrocera oster      | Olla Arrocera Oster 1,8 L                 | CUMPLE",
        "olla arrocera oster      | Arrocera Oster 10 tazas                   | CUMPLE",
        "olla arrocera oster      | Olla Arrocera Imusa 1,8 L                 | INCUMPLE",
        "audífonos inalámbricos   | Audífonos con cable de 3.5 mm             | INCUMPLE",
        "audífonos inalámbricos   | Audífonos Sony WH-CH520                   | SIN_CONFIRMAR",
        "audífonos inalámbricos   | Audífonos Bluetooth JBL Tune 520BT        | CUMPLE",
        "audífonos inalámbricos   | Audífonos Bluetooth con cable de carga USB | CUMPLE",
        "audífonos con cable      | Audífonos Bluetooth JBL Tune 520BT        | INCUMPLE",
        "freidora de aire         | Freidora de aceite 3 litros               | INCUMPLE",
        "freidora de aire         | Air Fryer Oster 4 L                       | CUMPLE",
        "freidora de aire         | Freidora sin aceite Imusa 3,5 L           | CUMPLE",
        "freidora de aire         | Freidora Kalley 4 L                       | SIN_CONFIRMAR",
        "licuadora oster roja     | Licuadora Oster blanca                    | INCUMPLE",
        "licuadora oster roja     | Licuadora Oster Roja 600 W                | CUMPLE",
        "licuadora oster roja     | Licuadora Oster 600 W                     | SIN_CONFIRMAR",
        "licuadora oster roja     | Licuadora Oster rojo y negro              | CUMPLE",
    })
    void cadaRequisitoExpresoSeExige(String busqueda, String titulo, Nivel esperado) {
        Evaluacion e = evaluar(busqueda, titulo);

        assertEquals(esperado, e.nivel(), e.motivos().toString());
    }

    @Test
    void unaLicuadoraOsterNoEsUnaOllaArroceraOsterAunqueLaMarcaCoincida() {
        Evaluacion e = evaluar("olla arrocera oster", "Licuadora Oster 600 W");

        assertTrue(e.ajena(), "es otro producto: no se muestra ni como alternativa");
        assertTrue(e.motivos().get(0).startsWith("Es una licuadora"), e.motivos().toString());
    }

    @Test
    void losMotivosDicenQueDatoConcretoDifiere() {
        assertEquals(List.of("Es con cable, no inalámbrico"),
                evaluar("audífonos inalámbricos", "Audífonos con cable de 3.5 mm").motivos());
        assertEquals(List.of("Es de aceite, no de aire"), evaluar("freidora de aire", "Freidora de aceite 3 litros").motivos());
        assertEquals(List.of("Es de color blanco, no rojo"), evaluar("licuadora oster roja", "Licuadora Oster blanca").motivos());
        assertEquals(List.of("El título no dice si es inalámbrico"),
                evaluar("audífonos inalámbricos", "Audífonos Sony WH-CH520").motivos());
        assertFalse(evaluar("freidora de aire", "Freidora de aceite 3 litros").ajena(),
                "es el mismo tipo de producto: se muestra como alternativa con el motivo");
    }

    @Test
    void losSinonimosCuentanYNoSeExigenPalabrasLiterales() {
        assertEquals(Map.of("conectividad", Set.of("inalámbrico")), AtributosProducto.leer("Audífonos TWS wireless"));
        assertEquals(Map.of("funcionamiento", Set.of("de aire")), AtributosProducto.leer("Airfryer 5 L sin aceite"));
        assertTrue(AtributosProducto.leer("Licuadora Oster 600 W").isEmpty());
        assertEquals(List.of("marca Oster", "color rojo"), RequisitosBusqueda.de("licuadora oster roja").etiquetas());
        assertEquals(List.of("inalámbrico"), RequisitosBusqueda.de("audifonos inalambricos").etiquetas());
    }

    @Test
    void laCorreccionNoPuedeQuitarUnaCaracteristica() {
        assertFalse(RequisitosBusqueda.correccionConserva("audífonos inalámbricos", "audífonos"));
        assertFalse(RequisitosBusqueda.correccionConserva("freidora de aire", "freidora"));
        assertTrue(RequisitosBusqueda.correccionConserva("audifonos inalanbricos bluetooth", "audífonos bluetooth"));
        RequisitosBusqueda combinados = RequisitosBusqueda.combinar("licuadora oster roja", "licuadora oster");
        assertEquals(Nivel.INCUMPLE, combinados.evaluar("Licuadora Oster blanca", NUEVO).nivel());
    }

    @Test
    void laComparacionUsaLaMismaLectura() {
        assertEquals(List.of("con cable frente a inalámbrico"),
                AtributosProducto.diferenciasDeProducto("Audífonos Sony con cable", "Audífonos Sony Bluetooth"));
        assertTrue(AtributosProducto.diferenciasDeProducto("Licuadora Oster roja", "Licuadora Oster negra").isEmpty(),
                "el color no hace otro producto para comparar");
    }
}
