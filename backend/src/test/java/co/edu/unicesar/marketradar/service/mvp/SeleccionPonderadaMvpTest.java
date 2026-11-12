package co.edu.unicesar.marketradar.service.mvp;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ProductoMvp;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeleccionPonderadaMvpTest {

    private final SeleccionPonderadaMvp seleccion = new SeleccionPonderadaMvp(PropiedadesPrueba.crear());

    @Test
    void sinOfertasNoHayMvp() {
        assertTrue(seleccion.elegir(List.of()).isEmpty());
    }

    @Test
    void conIgualesResenasGanaLaMasBarata() {
        ProductoMvp mvp = seleccion.elegir(List.of(
                oferta("a", "120000", "0", "4.5", 300, "nuevo"),
                oferta("b", "100000", "0", "4.5", 300, "nuevo"))).orElseThrow();

        assertEquals("b", mvp.oferta().getIdExterno());
        assertEquals("Tiene el precio total más bajo de las 2 ofertas que cumplen su búsqueda.", mvp.razones().get(0));
        assertEquals("https://tienda.com/b", mvp.oferta().getUrlDestino());
    }

    @Test
    void muchasBuenasOpinionesSuperanUnaDiferenciaPequenaDePrecio() {
        ProductoMvp mvp = seleccion.elegir(List.of(
                oferta("barata", "100000", null, null, null, "nuevo"),
                oferta("confiable", "104000", "0", "4.8", 2500, "nuevo"))).orElseThrow();

        assertEquals("confiable", mvp.oferta().getIdExterno());
        assertTrue(mvp.razones().get(0).startsWith("Cuesta $4.000 más"));
        assertTrue(mvp.razones().get(1).contains("4,8 de 5 estrellas en 2.500 opiniones"));
        assertEquals("El envío es gratis.", mvp.razones().get(2));
    }

    @Test
    void pocasOpinionesPerfectasNoEnganianAlPuntaje() {
        ProductoMvp mvp = seleccion.elegir(List.of(
                oferta("dos-resenas", "100000", "0", "5", 2, "nuevo"),
                oferta("mil-resenas", "100000", "0", "4.6", 1000, "nuevo"))).orElseThrow();

        assertEquals("mil-resenas", mvp.oferta().getIdExterno());
    }

    @Test
    void penalizaProductosUsados() {
        ProductoMvp mvp = seleccion.elegir(List.of(
                oferta("usado", "95000", "0", "4.5", 300, "Usado"),
                oferta("nuevo", "100000", "0", "4.5", 300, "nuevo"))).orElseThrow();

        assertEquals("nuevo", mvp.oferta().getIdExterno());
    }

    @Test
    void sinEnvioConocidoPrefiereLaTiendaDeColombiaQueLaDelExterior() {
        OfertaNormalizada exterior = OfertaNormalizada.builder()
                .codigoMarketplace("googleshopping").idExterno("ebay").titulo("Tensiómetro ebay")
                .precio(new BigDecimal("98000")).moneda("COP").vendedor("eBay - salud")
                .calificacion(new BigDecimal("4.5")).totalResenas(300).condicion("nuevo")
                .urlDestino("https://www.google.com.co/search?q=t").build();
        OfertaNormalizada local = OfertaNormalizada.builder()
                .codigoMarketplace("googleshopping").idExterno("exito").titulo("Tensiómetro exito")
                .precio(new BigDecimal("100000")).moneda("COP").vendedor("Éxito")
                .calificacion(new BigDecimal("4.5")).totalResenas(300).condicion("nuevo")
                .urlDestino("https://www.google.com.co/search?q=t").build();

        ProductoMvp mvp = seleccion.elegir(List.of(exterior, local)).orElseThrow();
        assertEquals("exito", mvp.oferta().getIdExterno());
        assertTrue(mvp.razones().contains("La tienda vende desde Colombia."));

        ProductoMvp soloExterior = seleccion.elegir(List.of(exterior)).orElseThrow();
        assertTrue(soloExterior.razones().stream().anyMatch(r -> r.startsWith("Atención: la tienda vende desde el "
                + "exterior. Puede tardar más")));
    }

    @Test
    void elPuntajeVaDeCeroACien() {
        ProductoMvp mvp = seleccion.elegir(List.of(oferta("unica", "50000", "0", "5", 100000, "nuevo")))
                .orElseThrow();
        assertTrue(mvp.puntaje() > 90 && mvp.puntaje() <= 100);
    }

    @Test
    void unEnvioDesconocidoNuncaSubeElPuntaje() {
        int desconocido = sola(oferta("x", "100000", null, "4.5", 300, "nuevo"));
        int conCosto = sola(oferta("x", "100000", "15000", "4.5", 300, "nuevo"));
        int gratis = sola(oferta("x", "100000", "0", "4.5", 300, "nuevo"));

        assertTrue(desconocido < conCosto, desconocido + " < " + conCosto);
        assertTrue(conCosto < gratis, conCosto + " < " + gratis);
    }

    @Test
    void unaCondicionDesconocidaNuncaSubeElPuntaje() {
        assertTrue(sola(oferta("x", "100000", "0", "4.5", 300, null))
                < sola(oferta("x", "100000", "0", "4.5", 300, "nuevo")));
    }

    @Test
    void sinOpinionesCuentaComoTresEstrellasNiMasNiMenos() {
        int sinOpiniones = sola(oferta("x", "100000", "0", null, null, "nuevo"));
        int tresEstrellas = sola(oferta("x", "100000", "0", "3.0", 5000, "nuevo"));
        int buenas = sola(oferta("x", "100000", "0", "4.5", 5000, "nuevo"));

        assertEquals(tresEstrellas, sinOpiniones);
        assertTrue(sinOpiniones < buenas);
    }

    @Test
    void unEnvioDesconocidoNoHaceVerMasBarataLaOferta() {
        // Sin el envío, la "a" parece $ 5.000 más barata; con el envío más caro conocido (20.000) ya no lo es.
        ProductoMvp mvp = seleccion.elegir(List.of(
                oferta("a", "95000", null, "4.5", 300, "nuevo"),
                oferta("b", "100000", "0", "4.5", 300, "nuevo"),
                oferta("c", "90000", "20000", "4.5", 300, "nuevo"))).orElseThrow();

        assertEquals("b", mvp.oferta().getIdExterno());
    }

    @Test
    void siNingunPrecioSePudoVerificarNoRecomiendaNinguna() {
        assertTrue(seleccion.elegir(List.of(
                oferta("a", "95000", "0", "4.5", 300, "nuevo").conAlertaPrecio("Revíselo"))).isEmpty());
    }

    @Test
    void laConfianzaDiceCuantoFaltoSaber() {
        ProductoMvp completa = seleccion.elegir(List.of(conTienda(oferta("a", "95000", "0", "4.5", 300, "nuevo"),
                "Éxito"))).orElseThrow();
        assertEquals(ProductoMvp.Confianza.ALTA, completa.confianza());
        assertTrue(completa.faltantes().isEmpty(), completa.faltantes().toString());
        assertEquals(1, completa.comparadas());

        ProductoMvp incompleta = seleccion.elegir(List.of(oferta("a", "95000", null, null, null, null)))
                .orElseThrow();
        assertEquals(ProductoMvp.Confianza.BAJA, incompleta.confianza());
        assertEquals(List.of("el costo del envío", "desde dónde vende la tienda", "opiniones de compradores",
                "la condición del producto (si es nuevo)"), incompleta.faltantes());
    }

    @Test
    void lasOpinionesDeGoogleSeDicenDelProductoNoDeLaTienda() {
        OfertaNormalizada google = OfertaNormalizada.builder().codigoMarketplace("googleshopping").idExterno("g")
                .titulo("Tensiómetro").precio(new BigDecimal("95000")).moneda("COP").costoEnvio(BigDecimal.ZERO)
                .calificacion(new BigDecimal("4.6")).totalResenas(312).condicion("nuevo")
                .urlDestino("https://tienda.com/g").build();

        ProductoMvp mvp = seleccion.elegir(List.of(google)).orElseThrow();

        assertEquals("Tiene 4,6 de 5 estrellas en 312 opiniones del producto reunidas por Google (no son opiniones "
                + "sobre esta tienda).", mvp.razones().get(1));
    }

    private int sola(OfertaNormalizada oferta) {
        return seleccion.elegir(List.of(oferta)).orElseThrow().puntaje();
    }

    private static OfertaNormalizada conTienda(OfertaNormalizada oferta, String tienda) {
        return OfertaNormalizada.builder().codigoMarketplace(oferta.getCodigoMarketplace())
                .idExterno(oferta.getIdExterno()).titulo(oferta.getTitulo()).precio(oferta.getPrecio())
                .moneda(oferta.getMoneda()).costoEnvio(oferta.getCostoEnvio()).condicion(oferta.getCondicion())
                .calificacion(oferta.getCalificacion()).totalResenas(oferta.getTotalResenas()).vendedor(tienda)
                .urlDestino(oferta.getUrlDestino()).build();
    }

    private static OfertaNormalizada oferta(String id, String precio, String envio, String estrellas,
                                            Integer opiniones, String condicion) {
        return OfertaNormalizada.builder()
                .codigoMarketplace("simulado").idExterno(id).titulo("Tensiómetro " + id)
                .precio(new BigDecimal(precio)).moneda("COP")
                .costoEnvio(envio == null ? null : new BigDecimal(envio))
                .calificacion(estrellas == null ? null : new BigDecimal(estrellas)).totalResenas(opiniones)
                .condicion(condicion).urlDestino("https://tienda.com/" + id)
                .build();
    }
}
