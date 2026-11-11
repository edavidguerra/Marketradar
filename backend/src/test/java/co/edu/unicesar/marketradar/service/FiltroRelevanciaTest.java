package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FiltroRelevanciaTest {

    @Test
    void primeroLasQueTraenTodasLasPalabrasBuscadas() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Licuadora Imusa 1,5 L"),
                oferta("Licuadora Oster 600 W Vaso de Vidrio"),
                oferta("Batidora Oster de mano"),
                oferta("Licuadora Oster Xpert Series"),
                oferta("Licuadora Oster Clásica 3 velocidades"),
                oferta("Licuadora Samurai Power Jet"));

        assertEquals(List.of("Licuadora Oster 600 W Vaso de Vidrio", "Licuadora Oster Xpert Series",
                        "Licuadora Oster Clásica 3 velocidades"),
                titulos(FiltroRelevancia.filtrar("licuadora oster", ofertas)));
    }

    @Test
    void losNumerosDebenSerExactosYSeEntiendenLasAbreviaturas() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Consola Sony PlayStation 4 Slim 1 TB"),
                oferta("Consola PS5 Slim 1 TB"),
                oferta("Sony PlayStation 5 Pro"),
                oferta("PlayStation 4 Pro reacondicionada"),
                oferta("PlayStation 5 Digital Edition"));

        assertEquals(List.of("Consola PS5 Slim 1 TB", "Sony PlayStation 5 Pro", "PlayStation 5 Digital Edition"),
                titulos(FiltroRelevancia.filtrar("playstation 5", ofertas)));
        assertEquals(3, FiltroRelevancia.filtrar("ps5", ofertas).size());
    }

    @Test
    void aceptaPluralesTildesYMayusculas() {
        assertEquals(2, FiltroRelevancia.coincidencias(FiltroRelevancia.palabras("ollas arroceras"),
                "Olla Arrocera Eléctrica 1,8 L"));
        assertEquals(2, FiltroRelevancia.coincidencias(FiltroRelevancia.palabras("televisores samsung"),
                "TV SAMSUNG 55\" Crystal UHD"));
        assertEquals(1, FiltroRelevancia.coincidencias(FiltroRelevancia.palabras("cafetera de goteo"),
                "Cafetera Italiana"));
    }

    @Test
    void siPocasTraenTodasLasPalabrasSeCompletaConLasMasParecidas() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Olla arrocera Oster 1,8 L"),
                oferta("Olla arrocera Imusa"),
                oferta("Sartén antiadherente"),
                oferta("Olla de presión Universal"),
                oferta("Arrocera Samurai"));

        // Solo una trae "olla arrocera oster"; se agregan las que traen dos palabras, y no la sartén.
        assertEquals(List.of("Olla arrocera Oster 1,8 L", "Olla arrocera Imusa"),
                titulos(FiltroRelevancia.filtrar("olla arrocera oster", ofertas)).subList(0, 2));
        assertFalse(titulos(FiltroRelevancia.filtrar("olla arrocera oster", ofertas)).contains("Sartén antiadherente"));
    }

    @Test
    void siNingunaSeParecePorLasPalabrasSeMuestranTodas() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Producto A"), oferta("Producto B"), oferta("Producto C"), oferta("Producto D"));

        assertEquals(4, FiltroRelevancia.filtrar("licuadora", ofertas).size());
    }

    @Test
    void quitaLosPreciosQueSonDeOtroProducto() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Consola Sony PlayStation Reacondicionada", "80961"),
                oferta("Consola PlayStation 5 Slim", "2167685"),
                oferta("Consola PlayStation 5 Slim Digital", "3045900"),
                oferta("Combo PlayStation 5 Slim + 2 juegos", "4199900"),
                oferta("PlayStation 5 Pro 2 TB", "5399055"));

        assertEquals(List.of("Consola PlayStation 5 Slim", "Consola PlayStation 5 Slim Digital",
                        "Combo PlayStation 5 Slim + 2 juegos", "PlayStation 5 Pro 2 TB"),
                titulos(FiltroRelevancia.filtrar("playstation", ofertas)));
    }

    @Test
    void noQuitaPreciosSiQuedariaCasiNada() {
        List<OfertaNormalizada> ofertas = List.of(oferta("Lupa A", "5000"), oferta("Lupa B", "6000"),
                oferta("Lupa C", "90000"), oferta("Lupa D", "95000"));

        assertEquals(4, FiltroRelevancia.sinPreciosAbsurdos(ofertas).size());
    }

    private static List<String> titulos(List<OfertaNormalizada> ofertas) {
        return ofertas.stream().map(OfertaNormalizada::getTitulo).toList();
    }

    private static OfertaNormalizada oferta(String titulo) {
        return oferta(titulo, "100000");
    }

    private static OfertaNormalizada oferta(String titulo, String precio) {
        return OfertaNormalizada.builder()
                .codigoMarketplace("googleshopping").idExterno(titulo).titulo(titulo)
                .precio(new BigDecimal(precio)).moneda("COP").condicion("nuevo").vendedor("Éxito")
                .urlDestino("https://ejemplo.com/" + Math.abs(titulo.hashCode()))
                .build();
    }
}
