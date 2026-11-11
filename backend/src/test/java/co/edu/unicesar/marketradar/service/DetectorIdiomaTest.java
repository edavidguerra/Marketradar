package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.Idioma;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectorIdiomaTest {

    @Test
    void reconoceElIdiomaDeLasResenas() {
        assertEquals(Optional.of(Idioma.EN), DetectorIdioma.detectar(
                "Not a fresh product. This product is not new. The packing was broken when I got it."));
        assertEquals(Optional.of(Idioma.ES), DetectorIdioma.detectar(
                "Muy buena licuadora, llegó rápido y funciona perfecto. La recomiendo."));
        assertEquals(Optional.of(Idioma.PT), DetectorIdioma.detectar(
                "Produto muito bom, chegou antes do prazo e a qualidade é ótima."));
        assertEquals(Optional.of(Idioma.FR), DetectorIdioma.detectar(
                "Très bon produit, je le recommande. La qualité est excellente pour le prix."));
    }

    @Test
    void conPocasPalabrasNoAdivina() {
        assertTrue(DetectorIdioma.detectar("Excelente!!").isEmpty());
        assertTrue(DetectorIdioma.detectar("OK").isEmpty());
        assertTrue(DetectorIdioma.detectar(null).isEmpty());
        assertTrue(DetectorIdioma.detectar("   ").isEmpty());
    }
}
