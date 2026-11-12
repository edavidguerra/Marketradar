package co.edu.unicesar.marketradar.service.mvp;

import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticaRecomendacionTest {

    private final List<String> reglas = PoliticaRecomendacion.explicar(new PropiedadesMarketradar.Mvp(0.6, 0.3, 0.1, 20));

    @Test
    void losPesosQueSeExplicanSonLosQueSeUsan() {
        String texto = String.join("\n", reglas);

        assertTrue(texto.contains("Precio (60 %)"), texto);
        assertTrue(texto.contains("Opiniones (30 %)"), texto);
        assertTrue(texto.contains("Envío (10 %)"), texto);
        assertTrue(texto.contains("Con menos de 20 opiniones"), texto);
        assertTrue(texto.contains("baja un " + Math.round((1 - SeleccionPonderadaMvp.PENALIZACION_NO_NUEVO) * 100)
                + " %"), texto);
    }

    @Test
    void diceQueNoEsUnaProbabilidadYCuandoNoSeRecomienda() {
        String texto = String.join("\n", reglas);

        assertTrue(texto.contains("No es una probabilidad"), texto);
        assertTrue(texto.contains("Si ninguna cumple, no recomendamos ninguna"), texto);
        assertTrue(texto.contains("no opiniones sobre el vendedor"), texto);
    }
}
