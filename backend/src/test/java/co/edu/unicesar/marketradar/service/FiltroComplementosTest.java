package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FiltroComplementosTest {

    @Test
    void quienBuscaLaConsolaNoVeJuegosNiPaquetesDigitales() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Legend SET - Sony PlayStation 5", "PlayStation Store LATAM", "6503"),
                oferta("Undisputed - Iron and Steel Pack - Sony PlayStation 5", "PlayStation Store LATAM", "48985"),
                oferta("Juego PS5 Marvel's Spider-Man 2", "Éxito", "199900"),
                oferta("Control inalámbrico DualSense para PlayStation 5", "Falabella", "329900"),
                oferta("Funda protectora para PlayStation 5", "Mercado Libre", "59900"),
                oferta("Consola Sony PlayStation 5 Slim 1 TB", "Alkosto", "2299900"),
                oferta("Sony PlayStation 5 Pro", "Falabella", "3499900"),
                oferta("PlayStation 5 Slim Digital + 2 juegos", "Ktronix", "2099900"));

        assertEquals(List.of("Consola Sony PlayStation 5 Slim 1 TB", "Sony PlayStation 5 Pro",
                        "PlayStation 5 Slim Digital + 2 juegos"),
                titulos(FiltroComplementos.filtrar("playstation 5", ofertas)));
    }

    @Test
    void siLaPersonaPideElJuegoSeLoMuestra() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Juego PS5 Marvel's Spider-Man 2", "Éxito", "199900"),
                oferta("Juego PS5 EA Sports FC 25", "Alkosto", "189900"),
                oferta("Juego PS5 Gran Turismo 7", "Falabella", "159900"),
                oferta("Consola Sony PlayStation 5 Slim", "Alkosto", "2299900"));

        assertEquals(4, FiltroComplementos.filtrar("juego ps5", ofertas).size());
    }

    @Test
    void unJuegoDeOllasEsElProductoYNoUnComplemento() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Juego de ollas antiadherente 10 piezas", "Éxito", "299900"),
                oferta("Set de ollas Imusa 5 piezas", "Falabella", "199900"),
                oferta("Batería de cocina 7 piezas", "Alkosto", "249900"),
                oferta("Repuesto tapa de vidrio para olla", "Mercado Libre", "29900"));

        assertEquals(List.of("Juego de ollas antiadherente 10 piezas", "Set de ollas Imusa 5 piezas",
                "Batería de cocina 7 piezas"), titulos(FiltroComplementos.filtrar("juego de ollas", ofertas)));
    }

    @Test
    void siCasiTodoSonComplementosSeMuestraTodo() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Vaso para licuadora Oster", "Éxito", "49900"),
                oferta("Cuchilla repuesto para licuadora", "Falabella", "19900"),
                oferta("Licuadora Oster 3 velocidades", "Alkosto", "189900"),
                oferta("Tapa para licuadora", "Mercado Libre", "15900"));

        assertEquals(4, FiltroComplementos.filtrar("licuadora", ofertas).size());
    }

    @Test
    void quitaSiempreLasCajasVaciasAunqueSeanLoUnico() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Caja iPhone 17 Pro Original", "Mercado Libre", "150000"),
                oferta("Apple iPhone 17 Pro Max - Empty Box", "eBay", "90000"),
                oferta("iPhone 17 Pro solo caja y manual", "Linio", "80000"),
                oferta("iPhone 17 Pro 256 GB con caja original", "iDroid", "5200000"));

        assertEquals(List.of("iPhone 17 Pro 256 GB con caja original"),
                titulos(FiltroComplementos.quitarSinProducto("iphone 17 pro", ofertas)));
        assertEquals(List.of(), FiltroComplementos.quitarSinProducto("iphone 17 pro", ofertas.subList(0, 1)));
    }

    @Test
    void siLaPersonaBuscaUnaCajaNoSeQuita() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Caja fuerte digital Yale", "Homecenter", "350000"),
                oferta("Caja de herramientas Stanley", "Homecenter", "120000"));

        assertEquals(2, FiltroComplementos.quitarSinProducto("caja fuerte", ofertas).size());
        assertEquals(1, FiltroComplementos.quitarSinProducto("taladro", ofertas.subList(1, 2)).size());
    }

    @Test
    void lasFundasEnInglesYLosVidriosTambienSonComplementos() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Case iPhone 17 Pro MagSafe", "Amazon", "90000"),
                oferta("Vidrio templado iPhone 17 Pro", "Éxito", "25000"),
                oferta("iPhone 17 Pro 256 GB", "iDroid", "5200000"),
                oferta("iPhone 17 Pro 512 GB", "Ktronix", "5900000"),
                oferta("iPhone 17 Pro Max 256 GB", "Alkosto", "6100000"));

        assertEquals(List.of("iPhone 17 Pro 256 GB", "iPhone 17 Pro 512 GB", "iPhone 17 Pro Max 256 GB"),
                titulos(FiltroComplementos.filtrar("iphone 17 pro", ofertas)));
    }

    private static List<String> titulos(List<OfertaNormalizada> ofertas) {
        return ofertas.stream().map(OfertaNormalizada::getTitulo).toList();
    }

    private static OfertaNormalizada oferta(String titulo, String tienda, String precio) {
        return OfertaNormalizada.builder()
                .codigoMarketplace("googleshopping").idExterno(titulo).titulo(titulo)
                .precio(new BigDecimal(precio)).moneda("COP").condicion("nuevo").vendedor(tienda)
                .urlDestino("https://ejemplo.com/" + Math.abs(titulo.hashCode()))
                .build();
    }
}
