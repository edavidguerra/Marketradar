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
    void siSoloUnaTraeTodasLasPalabrasSeMuestraSoloEsaSinCompletar() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Olla arrocera Oster 1,8 L"),
                oferta("Olla arrocera Imusa"),
                oferta("Sartén antiadherente"),
                oferta("Olla de presión Universal"),
                oferta("Arrocera Samurai"));

        // Antes se completaba hasta 3 con las que traían menos palabras (la Imusa, la Samurai).
        assertEquals(List.of("Olla arrocera Oster 1,8 L"), titulos(FiltroRelevancia.filtrar("olla arrocera oster", ofertas)));
    }

    @Test
    void conDosCoincidenciasSeMuestranDosAunqueHayaMasResultados() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Consola PS5 Slim 1 TB"),
                oferta("Consola Sony PlayStation 4 Slim 1 TB"),
                oferta("Sony PlayStation 5 Pro"),
                oferta("PlayStation 4 Pro reacondicionada"));

        assertEquals(List.of("Consola PS5 Slim 1 TB", "Sony PlayStation 5 Pro"),
                titulos(FiltroRelevancia.filtrar("playstation 5", ofertas)));
    }

    @Test
    void siNingunaSeParecePorLasPalabrasNoSeMuestraNinguna() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Producto A"), oferta("Producto B"), oferta("Producto C"), oferta("Producto D"));

        // Antes se mostraban las 4 "para no dejar a la persona sin nada": ninguna es una licuadora.
        assertEquals(List.of(), FiltroRelevancia.filtrar("licuadora", ofertas));
        assertEquals(List.of(), FiltroRelevancia.filtrar("licuadora", ofertas.subList(0, 2)), "ni siquiera si son pocas");
    }

    @Test
    void unaPantallaDeTresPuntoCincoNoCuentaComoElNumeroCinco() {
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Consola De Videojuegos Portátil Q21 Pantalla 3.5 Pulgadas 10000 Juegos Retro"),
                oferta("Consola PlayStation 5 Slim 1 TB"));

        assertEquals(List.of("Consola PlayStation 5 Slim 1 TB"), titulos(FiltroRelevancia.filtrar("playstation 5", ofertas)));
        assertEquals(0, FiltroRelevancia.coincidencias(FiltroRelevancia.palabras("5"), "Pantalla 3,5 pulgadas"));
    }

    @Test
    void elPrecioNoDecideQueProductoEs() {
        // Una PS5 barata no se quita por su precio: se muestra y el aviso de precio atípico lo decide después.
        List<OfertaNormalizada> ofertas = List.of(
                oferta("Consola PlayStation 5 Slim", "80961"),
                oferta("Consola PlayStation 5 Slim", "2167685"),
                oferta("PlayStation 5 Pro 2 TB", "5399055"),
                oferta("Consola PlayStation 5 Slim 1 TB", "2399000"));

        assertEquals(4, FiltroRelevancia.filtrar("playstation 5", ofertas).size());
    }

    @Test
    void deLoQueEligeLaIAQuitaAccesoriosYRepuestos() {
        List<OfertaNormalizada> candidatas = List.of(
                oferta("Funda iPhone 17 Pro", "60000"),
                oferta("iPhone 17 Pro 256 GB", "5200000"),
                oferta("iPhone 17 Pro 512 GB", "5900000"),
                oferta("iPhone 17 Pro repuesto pantalla", "300000"));

        assertEquals(List.of("iPhone 17 Pro 256 GB"), titulos(FiltroRelevancia.revisarEleccion("iphone 17 pro",
                List.of(candidatas.get(0), candidatas.get(1), candidatas.get(3)), candidatas)));
        assertEquals(List.of(), FiltroRelevancia.revisarEleccion("iphone 17 pro", List.of(candidatas.get(0)), candidatas));
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
