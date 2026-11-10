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

    private static List<String> titulos(List<OfertaNormalizada> ofertas) {
        return ofertas.stream().map(OfertaNormalizada::getTitulo).toList();
    }

    private static OfertaNormalizada oferta(String titulo) {
        return OfertaNormalizada.builder()
                .codigoMarketplace("googleshopping").idExterno(titulo).titulo(titulo)
                .precio(new BigDecimal("100000")).moneda("COP").condicion("nuevo").vendedor("Éxito")
                .urlDestino("https://ejemplo.com/" + Math.abs(titulo.hashCode()))
                .build();
    }
}
