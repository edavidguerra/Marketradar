package co.edu.unicesar.marketradar.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnlaceCompraTest {

    @Test
    void unEnlaceDeLaTiendaNombraLaTienda() {
        EnlaceCompra enlace = EnlaceCompra.de("https://www.falabella.com.co/falabella-co/product/123");

        assertEquals(EnlaceCompra.Tipo.DIRECTO, enlace.tipo());
        assertEquals("Ver la oferta en falabella.com.co", enlace.textoBoton());
        assertTrue(enlace.llevaALaTienda());
    }

    @Test
    void unaRedireccionDeGoogleNombraLaTiendaDeDestino() {
        EnlaceCompra enlace = EnlaceCompra.de(
                "https://www.google.com/url?url=https%3A%2F%2Fwww.alkosto.com%2Fiphone-13%2Fp%2F194252&sa=U");

        assertEquals(EnlaceCompra.Tipo.REDIRECCION, enlace.tipo());
        assertEquals("Ver la oferta en alkosto.com", enlace.textoBoton());
        assertTrue(enlace.llevaALaTienda());
    }

    @Test
    void unaBusquedaDeGoogleNoSePresentaComoLaOferta() {
        EnlaceCompra enlace = EnlaceCompra.de("https://www.google.com.co/search?q=iphone+13+128gb&ibp=oshop");

        assertEquals(EnlaceCompra.Tipo.BUSQUEDA_GOOGLE, enlace.tipo());
        assertEquals("Buscar esta oferta en Google", enlace.textoBoton());
        assertFalse(enlace.llevaALaTienda());
    }

    @Test
    void laFichaDeGoogleShoppingDiceQueMuestraTiendas() {
        EnlaceCompra enlace = EnlaceCompra.de("https://www.google.com/shopping/product/1234567890?gl=co");

        assertEquals(EnlaceCompra.Tipo.PAGINA_GOOGLE, enlace.tipo());
        assertEquals("Ver las tiendas en Google Shopping", enlace.textoBoton());
    }

    @Test
    void unaRedireccionAOtraPaginaDeGoogleSigueSiendoGoogle() {
        EnlaceCompra enlace = EnlaceCompra.de("https://www.google.com/url?q=https%3A%2F%2Fwww.google.com%2Fsearch");

        assertEquals(EnlaceCompra.Tipo.BUSQUEDA_GOOGLE, enlace.tipo());
    }

    @Test
    void unaRedireccionAUnDestinoInseguroNoSeNombraComoTienda() {
        EnlaceCompra enlace = EnlaceCompra.de("https://www.google.com/url?url=javascript%3Aalert(1)");

        assertEquals(EnlaceCompra.Tipo.BUSQUEDA_GOOGLE, enlace.tipo());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert(1)",
        "http://www.falabella.com.co/p/1",
        "data:text/html,hola",
        "https://192.168.0.1/oferta",
        "https://localhost/oferta",
        "https://banco.com@otro.com/oferta",
        "https://[::1]/oferta",
        "ftp://tienda.com/archivo",
        "https://tienda .com/oferta",
    })
    void soloSeAbrenPaginasWebSeguras(String url) {
        assertFalse(EnlaceCompra.esSeguro(url), url);
    }

    @Test
    void unaDireccionDemasiadoLargaNoSeAcepta() {
        assertFalse(EnlaceCompra.esSeguro("https://tienda.com/" + "a".repeat(EnlaceCompra.LARGO_MAXIMO)));
        assertTrue(EnlaceCompra.esSeguro("https://tienda.com/oferta?id=1"));
    }
}
