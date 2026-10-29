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
        assertEquals("Es el precio más bajo de esta búsqueda.", mvp.razones().get(0));
        assertEquals("https://tienda.com/b", mvp.oferta().getUrlDestino());
    }

    @Test
    void muchasBuenasOpinionesSuperanUnaDiferenciaPequenaDePrecio() {
        ProductoMvp mvp = seleccion.elegir(List.of(
                oferta("barata", "100000", null, null, null, "nuevo"),
                oferta("confiable", "104000", "0", "4.8", 2500, "nuevo"))).orElseThrow();

        assertEquals("confiable", mvp.oferta().getIdExterno());
        assertTrue(mvp.razones().get(0).startsWith("Cuesta $4.000 más"));
        assertTrue(mvp.razones().get(1).contains("4,8 de 5 estrellas (2.500 opiniones)"));
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
    void elPuntajeVaDeCeroACien() {
        ProductoMvp mvp = seleccion.elegir(List.of(oferta("unica", "50000", "0", "5", 100000, "nuevo")))
                .orElseThrow();
        assertTrue(mvp.puntaje() > 90 && mvp.puntaje() <= 100);
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
