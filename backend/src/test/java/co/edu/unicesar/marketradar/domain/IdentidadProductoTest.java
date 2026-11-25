package co.edu.unicesar.marketradar.domain;

import co.edu.unicesar.marketradar.domain.RequisitosBusqueda.Evaluacion;
import co.edu.unicesar.marketradar.domain.RequisitosBusqueda.Nivel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Identidad del producto (familia, marca, modelo y variante) contra lo que pidió la persona. Cada caso reproduce un
 * fallo visto en la búsqueda: un número suelto ("3.5 pulgadas", "Bluetooth 5.0") confirmaba el modelo "PlayStation 5".
 */
class IdentidadProductoTest {

    private static final Optional<CondicionProducto> NUEVO = Optional.of(CondicionProducto.NUEVO);

    /** Título de la consola retro de la captura (construido a partir de la descripción del caso). */
    static final String Q21 = "Consola De Videojuegos Portátil Q21 Pantalla 3.5 Pulgadas 10000 Juegos Retro";
    static final String Q21_COMA = "Consola Retro Q21 Portátil Pantalla 3,5\" Recargable";

    private static Evaluacion evaluar(String busqueda, String titulo) {
        return RequisitosBusqueda.de(busqueda).evaluar(titulo, NUEVO);
    }

    @ParameterizedTest(name = "playstation 5 con «{0}» no cumple")
    @ValueSource(strings = {Q21, Q21_COMA})
    void laQ21ConPantallaDeTresPuntoCincoNuncaEsUnaPlayStation5(String titulo) {
        Evaluacion e = evaluar("playstation 5", titulo);

        assertEquals(Nivel.INCUMPLE, e.nivel(), e.motivos().toString());
        assertTrue(e.ajena(), "la Q21 es otro producto, no una alternativa de la PS5");
        assertEquals(Nivel.INCUMPLE, evaluar("ps5", titulo).nivel());
    }

    @ParameterizedTest(name = "playstation 5 con «{0}»")
    @ValueSource(strings = {"Consola Retro X5 Portátil 4.3 Pulgadas", "Adaptador Bluetooth 5.0 USB para PC",
            "Audífonos Bluetooth 5.0 Inalámbricos", "Celular Moto G 5G 128 GB"})
    void unNumeroDeConectividadOMedidaNoConfirmaElModelo(String titulo) {
        Evaluacion e = evaluar("playstation 5", titulo);

        assertNotEquals(Nivel.CUMPLE, e.nivel(), e.motivos().toString());
        assertTrue(e.ajena(), titulo + ": " + e.motivos());
    }

    @Test
    void unPortatilDeTrecePulgadasNoEsUnIphone13() {
        Evaluacion e = evaluar("iphone 13", "Portátil Lenovo IdeaPad 13 Pulgadas 8 GB RAM 256 GB SSD");

        assertEquals(Nivel.INCUMPLE, e.nivel());
        assertTrue(e.ajena());
        assertTrue(e.motivos().get(0).contains("computador portátil"), e.motivos().toString());
    }

    @ParameterizedTest(name = "{0} con «{1}»")
    @CsvSource(delimiter = '|', value = {
        "ps5                  | Consola Sony PlayStation 5 Slim 1 TB",
        "playstation 5        | Consola PS5 Slim 1 TB",
        "sony playstation 5   | Consola PS5 Slim",
        "ps5                  | Sony PlayStation®5 Slim",
        "play station 5       | PlayStation 5 Slim 1TB",
    })
    void ps5YPlayStation5SonElMismoModeloEnLosDosSentidos(String busqueda, String titulo) {
        Evaluacion e = evaluar(busqueda, titulo);

        assertEquals(Nivel.CUMPLE, e.nivel(), e.motivos().toString());
    }

    @Test
    void elAliasSeEntiendeIgualEnLaBusquedaYEnElTitulo() {
        assertEquals(RequisitosBusqueda.de("ps5").etiquetas(), RequisitosBusqueda.de("playstation 5").etiquetas());
        assertEquals(List.of("modelo playstation 5"), RequisitosBusqueda.de("PS5").etiquetas());
    }

    @ParameterizedTest(name = "playstation 5 con «{0}»")
    @ValueSource(strings = {"Control DualSense PS5 Inalámbrico Blanco", "Sony DualSense Wireless Controller PS5",
            "Funda Protectora Para Consola PS5 Slim", "Juego Spider-Man 2 PS5 Físico",
            "Caja Vacía PlayStation 5 Original", "Base de Carga Dual para Controles PS5"})
    void quienBuscaLaConsolaNoRecibeControlesFundasJuegosNiCajas(String titulo) {
        Evaluacion e = evaluar("playstation 5", titulo);

        assertEquals(Nivel.INCUMPLE, e.nivel(), titulo + ": " + e.motivos());
        assertTrue(e.ajena(), titulo);
    }

    @Test
    void quienBuscaElControlParaLaPs5SiRecibeControlesCompatibles() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("control para playstation 5");

        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Control DualSense PS5 Inalámbrico Blanco", NUEVO).nivel());
        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Control Inalámbrico Compatible Con PS5", NUEVO).nivel());
        Evaluacion consola = requisitos.evaluar("Consola PlayStation 5 Slim 1 TB", NUEVO);
        assertEquals(Nivel.INCUMPLE, consola.nivel());
        assertTrue(consola.ajena(), "una consola no es un control");
        assertTrue(requisitos.evaluar("Funda Para Control PS5", NUEVO).ajena(), "una funda no es un control");
        assertEquals(Nivel.INCUMPLE, requisitos.evaluar("Control Inalámbrico Para PS4", NUEVO).nivel());
    }

    @Test
    void unaLicuadoraImusaNoEsUnaLicuadoraOster() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("licuadora oster");

        Evaluacion imusa = requisitos.evaluar("Licuadora Imusa 1,5 L 3 Velocidades", NUEVO);
        assertEquals(Nivel.INCUMPLE, imusa.nivel());
        assertFalse(imusa.ajena(), "es el mismo tipo de producto: se muestra como alternativa de otra marca");
        assertEquals(List.of("Es de la marca Imusa, no Oster"), imusa.motivos());
        assertEquals(Nivel.CUMPLE, requisitos.evaluar("Licuadora Oster 600 W Vaso de Vidrio", NUEVO).nivel());
        assertEquals(Nivel.SIN_CONFIRMAR, requisitos.evaluar("Licuadora 10 Velocidades 1,25 L", NUEVO).nivel());
        assertNotEquals(Nivel.CUMPLE, requisitos.evaluar("Batidora Oster de Mano", NUEVO).nivel());
        assertTrue(requisitos.etiquetas().contains("marca Oster"), requisitos.etiquetas().toString());
    }

    @Test
    void losDecimalesConservanSuContexto() {
        // "1.25 litros" es una medida: no es el modelo 25 ni el 1.
        assertEquals(Nivel.CUMPLE, evaluar("licuadora oster 6640", "Licuadora Oster 6640 de 10 velocidades 1.25 litros")
                .nivel());
        assertEquals(List.of("1.25", "litros"), TextoProducto.palabras("1,25 litros"));
        assertEquals(List.of("3.5", "pulgadas"), TextoProducto.palabras("3.5\""));
        assertEquals(List.of("bluetooth", "5.0"), TextoProducto.palabras("Bluetooth 5.0"));
    }

    @Test
    void unaMedidaPedidaSeExigeComoMedidaYNoComoModelo() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("televisor samsung 55 pulgadas");

        assertEquals(Nivel.CUMPLE, requisitos.evaluar("TV Samsung 55\" Crystal UHD 4K", NUEVO).nivel());
        assertEquals(List.of("Es de 65 pulgadas, no de 55 pulgadas"),
                requisitos.evaluar("Televisor Samsung 65 Pulgadas 4K", NUEVO).motivos());
    }

    @Test
    void unBundleOUnaVarianteNoSeTomanComoLaMismaOferta() {
        RequisitosBusqueda requisitos = RequisitosBusqueda.de("playstation 5");

        Evaluacion conJuegos = requisitos.evaluar("Consola PS5 Slim + 2 Juegos", NUEVO);
        assertEquals(Nivel.INCUMPLE, conJuegos.nivel());
        assertFalse(conJuegos.ajena(), "trae la consola: se muestra aparte, identificada como paquete");
        // "playstation 5" no dice qué edición: la Digital no incumple, pero se muestra aparte como otra variante.
        Evaluacion digital = requisitos.evaluar("Consola PlayStation 5 Slim Edición Digital", NUEVO);
        assertEquals(Nivel.OTRA_VARIANTE, digital.nivel());
        assertFalse(digital.ajena());
        assertTrue(digital.motivos().get(0).contains("Digital"), digital.motivos().toString());
        assertFalse(digital.motivos().get(0).contains("no la que pidió"), digital.motivos().toString());
        assertEquals(Nivel.CUMPLE, RequisitosBusqueda.de("ps5 digital")
                .evaluar("Consola PlayStation 5 Slim Edición Digital", NUEVO).nivel());
    }

    @Test
    void sinEvidenciaPositivaNoSeConfirma() {
        // Nada lo contradice, pero el título no dice que sea una PlayStation 5.
        Evaluacion e = evaluar("playstation 5", "Consola de videojuegos 4K 1 TB");
        assertEquals(Nivel.SIN_CONFIRMAR, e.nivel());
        assertFalse(e.ajena());
    }

    @Test
    void laCorreccionNoPuedeCambiarLaIntencionNiElModelo() {
        assertFalse(RequisitosBusqueda.correccionConserva("control para ps5", "playstation 5"));
        assertFalse(RequisitosBusqueda.correccionConserva("iphone 13", "iphone 14"));
        assertFalse(RequisitosBusqueda.correccionConserva("licuadora oster", "licuadora"));
        assertTrue(RequisitosBusqueda.correccionConserva("quiero un ps5 pa mi nieto", "playstation 5"));
        assertTrue(RequisitosBusqueda.correccionConserva("licuadorra oster", "licuadora oster"));
        // La combinación sigue exigiendo el control aunque la búsqueda corregida lo haya perdido.
        RequisitosBusqueda combinados = RequisitosBusqueda.combinar("control para ps5", "playstation 5");
        assertTrue(combinados.evaluar("Consola PlayStation 5 Slim", NUEVO).ajena());
    }
}
