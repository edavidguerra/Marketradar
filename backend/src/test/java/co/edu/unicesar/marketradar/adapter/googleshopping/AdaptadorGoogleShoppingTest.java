package co.edu.unicesar.marketradar.adapter.googleshopping;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.EnlaceCompra;
import co.edu.unicesar.marketradar.domain.EstadoEnvio;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ResenasProducto;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

class AdaptadorGoogleShoppingTest {

    private static final String URL = "http://localhost/serpapi";
    private static final String CLAVE = "clave-secreta-de-prueba";

    private static final String RESPUESTA = """
            {"search_metadata":{"status":"Success"},
             "shopping_results":[
               {"position":1,"title":"Olla Arrocera 1,8 L Antiadherente","product_id":"111",
                "product_link":"https://www.google.com.co/shopping/product/111","source":"Éxito",
                "price":"$ 159.900","extracted_price":159900,"rating":4.6,"reviews":312,
                "thumbnail":"https://serpapi.com/images/111.jpg","delivery":"Envío gratis",
                "immersive_product_page_token":"token-111"},
               {"position":2,"title":"Olla Arrocera Importada","product_id":"222",
                "product_link":"https://www.google.com.co/shopping/product/222","source":"Tienda Internacional",
                "price":"US$ 25,00","extracted_price":25.0},
               {"position":3,"title":"Sin precio","product_id":"333",
                "product_link":"https://www.google.com.co/shopping/product/333"},
               {"position":4,"title":"Sin enlace","extracted_price":1000}
             ]}""";

    private RestClient.Builder builder;
    private MockRestServiceServer servidor;

    @BeforeEach
    void preparar() {
        // Mismo formato JSON que la aplicación (spring.jackson.property-naming-strategy: SNAKE_CASE).
        ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        builder = RestClient.builder().baseUrl(URL)
                .messageConverters(convertidores -> convertidores.addFirst(new MappingJackson2HttpMessageConverter(mapper)));
        servidor = MockRestServiceServer.bindTo(builder).build();
    }

    private AdaptadorGoogleShopping crear(String clave) {
        return new AdaptadorGoogleShopping(null, builder.build(), true, clave);
    }

    private static ConsultaBusqueda consulta(String termino) {
        return ConsultaBusqueda.crear(termino, null, Set.of(), 1, 10);
    }

    @Test
    void convierteLosResultadosEnOfertasConLaTiendaReal() {
        servidor.expect(once(), requestTo(startsWith(URL + "/search.json")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("engine", "google_shopping"))
                .andExpect(queryParam("q", "olla%20arrocera"))
                .andExpect(queryParam("gl", "co"))
                .andExpect(queryParam("hl", "es"))
                .andExpect(queryParam("api_key", CLAVE))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        List<OfertaNormalizada> ofertas = crear(CLAVE).consultarApi(consulta("olla arrocera"));

        servidor.verify();
        assertEquals(3, ofertas.size(), "el que no trae precio se descarta; el que no trae enlace, no");
        OfertaNormalizada primera = ofertas.get(0);
        assertEquals("googleshopping", primera.getCodigoMarketplace());
        assertEquals("111", primera.getIdExterno());
        assertEquals("Olla Arrocera 1,8 L Antiadherente", primera.getTitulo());
        assertEquals(0, new BigDecimal("159900").compareTo(primera.getPrecio()));
        assertEquals("COP", primera.getMoneda());
        assertEquals(0, BigDecimal.ZERO.compareTo(primera.getCostoEnvio()));
        assertEquals("Éxito", primera.getVendedor());
        assertEquals("https://www.google.com.co/shopping/product/111", primera.getUrlDestino());
        assertEquals("https://serpapi.com/images/111.jpg", primera.getUrlImagen());
        assertEquals(new BigDecimal("4.6"), primera.getCalificacion());
        assertEquals(312, primera.getTotalResenas());

        OfertaNormalizada segunda = ofertas.get(1);
        assertEquals("USD", segunda.getMoneda());
        assertNull(segunda.getCostoEnvio());
        assertNull(segunda.getCalificacion());

        // En Colombia muchos productos llegan sin enlace: se abre su búsqueda en Google Shopping.
        assertEquals("https://www.google.com.co/search?tbm=shop&gl=co&hl=es&q=Sin+enlace", ofertas.get(2).getUrlDestino());
    }

    @Test
    void elEnlaceDeBusquedaIncluyeLaTiendaYEsSeguro() {
        String enlace = AdaptadorGoogleShopping.enlaceBusqueda("Licuadora Oster 600 W & vaso", "Éxito");

        assertEquals("https://www.google.com.co/search?tbm=shop&gl=co&hl=es&q=Licuadora+Oster+600+W+%26+vaso+%C3%89xito", enlace);
        assertTrue(AdaptadorGoogleShopping.esSeguro(enlace));
    }

    @Test
    void usaLosProductosAgrupadosPorCategoriaYLosDestacados() {
        // Así responde Google con productos populares: sin shopping_results, todo agrupado por categoría.
        String agrupada = """
                {"categorized_shopping_results":[
                   {"title":"Consolas","shopping_results":[
                     {"title":"Consola PlayStation 5 Slim","product_id":"ps5",
                      "product_link":"https://www.google.com.co/shopping/product/ps5","source":"Alkosto",
                      "price":"$ 2.299.900","extracted_price":2299900}]},
                   {"title":"Con lector","shopping_results":[
                     {"title":"Consola PlayStation 5 Slim","product_id":"ps5",
                      "product_link":"https://www.google.com.co/shopping/product/ps5","source":"Alkosto",
                      "extracted_price":2299900},
                     {"title":"PlayStation 5 Pro","product_id":"ps5pro",
                      "product_link":"https://www.google.com.co/shopping/product/ps5pro","source":"Falabella",
                      "extracted_price":3499900}]}],
                 "inline_shopping_results":[
                   {"title":"PS5 Digital","link":"https://www.exito.com/ps5-digital","source":"Éxito",
                    "extracted_price":1999900}]}""";
        servidor.expect(once(), requestTo(startsWith(URL)))
                .andRespond(withSuccess(agrupada, MediaType.APPLICATION_JSON));

        List<OfertaNormalizada> ofertas = crear(CLAVE).consultarApi(consulta("playstation 5"));

        assertEquals(List.of("Consola PlayStation 5 Slim", "PlayStation 5 Pro", "PS5 Digital"),
                ofertas.stream().map(OfertaNormalizada::getTitulo).toList(), "sin repetir el mismo producto");
        assertEquals("https://www.exito.com/ps5-digital", ofertas.get(2).getUrlDestino());
    }

    @Test
    void sinResultadosDeGoogleDevuelveVacio() {
        servidor.expect(once(), requestTo(startsWith(URL)))
                .andRespond(withSuccess("{\"error\":\"Google hasn't returned any results for this query.\"}",
                        MediaType.APPLICATION_JSON));

        assertTrue(crear(CLAVE).consultarApi(consulta("xyzxyz")).isEmpty());
    }

    @Test
    void unaClaveMalaSeInformaComoErrorDeLaTienda() {
        servidor.expect(once(), requestTo(startsWith(URL)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":\"Invalid API key.\"}").contentType(MediaType.APPLICATION_JSON));

        MarketplaceException error = assertThrows(MarketplaceException.class,
                () -> crear(CLAVE).consultarApi(consulta("lupa")));
        assertTrue(error.getMessage().contains("401"));
    }

    @Test
    void sinBusquedasDisponiblesAvisaElLimite() {
        servidor.expect(once(), requestTo(startsWith(URL)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":\"Your account has run out of searches.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        MarketplaceException error = assertThrows(MarketplaceException.class,
                () -> crear(CLAVE).consultarApi(consulta("lupa")));
        assertTrue(error.getMessage().contains("429"));
    }

    @Test
    void unErrorDeConexionNoMuestraLaClave() {
        servidor.expect(once(), requestTo(startsWith(URL)))
                .andRespond(withException(new IOException("sin conexión")));

        MarketplaceException error = assertThrows(MarketplaceException.class,
                () -> crear(CLAVE).consultarApi(consulta("lupa")));
        assertFalse(error.getMessage().contains(CLAVE));
        assertNull(error.getCause());
    }

    @Test
    void soloSeConsultaConClave() {
        assertTrue(crear(CLAVE).estaHabilitado());
        assertFalse(crear("").estaHabilitado());
        assertFalse(new AdaptadorGoogleShopping(null, builder.build(), false, CLAVE).estaHabilitado());
    }

    @Test
    void guardaLaClaveParaPedirLasResenasDespues() {
        servidor.expect(once(), requestTo(startsWith(URL)))
                .andRespond(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON));

        List<OfertaNormalizada> ofertas = crear(CLAVE).consultarApi(consulta("olla arrocera"));

        assertEquals("token-111", ofertas.get(0).getTokenResenas());
        assertNull(ofertas.get(1).getTokenResenas());
    }

    @Test
    void traeLasResenasDelProductoConSuCalificacion() {
        StringBuilder opiniones = new StringBuilder();
        for (int i = 1; i <= 12; i++) {
            opiniones.append(i > 1 ? "," : "").append("""
                    {"title":"Muy buena %d","text":"  Cocina el arroz perfecto %d.  ","user_name":"Rosa","source":"Éxito",
                     "rating":5,"date":"hace 2 meses"}""".formatted(i, i));
        }
        String respuesta = """
                {"product_results":{"title":"Olla Arrocera","rating":4.6,"reviews":312,
                  "ratings":[{"stars":1,"amount":4},{"stars":5,"amount":250},{"stars":3,"amount":10},
                             {"stars":4,"amount":40},{"stars":2,"amount":8}],
                  "user_reviews":[{"title":"Sin texto","rating":2},%s]}}""".formatted(opiniones);
        servidor.expect(once(), requestTo(startsWith(URL + "/search.json")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("engine", "google_immersive_product"))
                .andExpect(queryParam("page_token", "token-111"))
                .andExpect(queryParam("api_key", CLAVE))
                .andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

        ResenasProducto resenas = crear(CLAVE).resenas("token-111");

        servidor.verify();
        assertEquals(new BigDecimal("4.6"), resenas.calificacion());
        assertEquals(312, resenas.totalResenas());
        assertEquals(List.of(5, 4, 3, 2, 1), resenas.distribucion().stream().map(ResenasProducto.Estrellas::estrellas).toList());
        assertEquals(250, resenas.distribucion().get(0).cantidad());
        assertEquals(10, resenas.resenas().size(), "máximo 10 opiniones, y sin las que no tienen texto");
        ResenasProducto.Resena primera = resenas.resenas().get(0);
        assertEquals("Muy buena 1", primera.titulo());
        assertEquals("Cocina el arroz perfecto 1.", primera.texto());
        assertEquals("Rosa", primera.autor());
        assertEquals("Éxito", primera.fuente());
        assertEquals("hace 2 meses", primera.fecha());
    }

    @Test
    void laClaveDelProductoLlegaCompletaASerpApi() {
        // Las claves son base64: si el "+" no se codifica, SerpApi lo lee como espacio y no encuentra el producto.
        servidor.expect(once(), requestTo(containsString("page_token=eyJh%2Bb%2Fc%3D%3D&")))
                .andRespond(withSuccess("{\"product_results\":{\"rating\":4.5,\"reviews\":2}}", MediaType.APPLICATION_JSON));

        assertEquals(2, crear(CLAVE).resenas("eyJh+b/c==").totalResenas());
        servidor.verify();
    }

    @Test
    void unProductoSinResenasDevuelveVacio() {
        servidor.expect(once(), requestTo(startsWith(URL)))
                .andRespond(withSuccess("{\"error\":\"Google hasn't returned any results for this query.\"}",
                        MediaType.APPLICATION_JSON));

        assertTrue(crear(CLAVE).resenas("token").estaVacia());
    }

    @Test
    void unErrorAlPedirLasResenasNoMuestraLaClave() {
        servidor.expect(once(), requestTo(startsWith(URL)))
                .andRespond(withException(new IOException("sin conexión")));

        MarketplaceException error = assertThrows(MarketplaceException.class, () -> crear(CLAVE).resenas("token"));
        assertFalse(error.getMessage().contains(CLAVE));
        assertNull(error.getCause());
    }

    @Test
    void reconoceLaMonedaYElEnvioGratis() {
        assertEquals("COP", AdaptadorGoogleShopping.moneda("$ 159.900"));
        assertEquals("USD", AdaptadorGoogleShopping.moneda("US$ 25,00"));
        assertEquals("COP", AdaptadorGoogleShopping.moneda(null));
        assertEquals(BigDecimal.ZERO, AdaptadorGoogleShopping.costoEnvio("Free delivery"));
        assertEquals(BigDecimal.ZERO, AdaptadorGoogleShopping.costoEnvio("Envío gratis"));
        assertNull(AdaptadorGoogleShopping.costoEnvio(null));
    }

    @Test
    void leeElCostoDelEnvio() {
        assertEquals(new BigDecimal("10000"), AdaptadorGoogleShopping.costoEnvio("Envío: $ 10.000", "COP"));
        assertEquals(new BigDecimal("12900"), AdaptadorGoogleShopping.costoEnvio("Envío por COP 12.900", "COP"));
        assertEquals(new BigDecimal("8500"), AdaptadorGoogleShopping.costoEnvio("Recíbelo mañana ($8,500)", "COP"));
        assertEquals(new BigDecimal("17"), AdaptadorGoogleShopping.costoEnvio("Get it today ($17)", "USD"));
        assertEquals(new BigDecimal("9.99"), AdaptadorGoogleShopping.costoEnvio("US$ 9,99 delivery", "USD"));
    }

    @Test
    void siElEnvioTieneCondicionesOEstaEnOtraMonedaNoSeAdivina() {
        // Gratis solo por encima de un valor: no se sabe si esta compra lo alcanza.
        assertNull(AdaptadorGoogleShopping.costoEnvio("Envío gratis en compras superiores a $ 150.000", "COP"));
        assertNull(AdaptadorGoogleShopping.costoEnvio("Free delivery on $45+", "USD"));
        assertNull(AdaptadorGoogleShopping.costoEnvio("Envío US$ 20", "COP"));
        assertNull(AdaptadorGoogleShopping.costoEnvio("Llega en 3 días", "COP"));
    }

    @Test
    void leeNumerosConPuntosYComas() {
        assertEquals(new BigDecimal("1299.50"), AdaptadorGoogleShopping.numero("1.299,50"));
        assertEquals(new BigDecimal("1299.50"), AdaptadorGoogleShopping.numero("1,299.50"));
        assertEquals(new BigDecimal("1323605"), AdaptadorGoogleShopping.numero("1.323.605"));
        assertEquals(new BigDecimal("45"), AdaptadorGoogleShopping.numero("45."));
    }

    @Test
    void cadaEstadoDelEnvioSeLeeSinInventarUnCero() {
        BigDecimal precio = new BigDecimal("80000");
        assertEquals(EstadoEnvio.GRATIS, AdaptadorGoogleShopping.envio("Envío gratis", "COP", precio).estado());
        assertEquals(EstadoEnvio.CONOCIDO, AdaptadorGoogleShopping.envio("Envío: $ 12.900", "COP", precio).estado());
        AdaptadorGoogleShopping.Envio estimado = AdaptadorGoogleShopping.envio("Envío desde $ 9.900", "COP", precio);
        assertEquals(EstadoEnvio.ESTIMADO, estimado.estado());
        assertEquals(new BigDecimal("9900"), estimado.costo());
        AdaptadorGoogleShopping.Envio nada = AdaptadorGoogleShopping.envio(null, "COP", precio);
        assertEquals(EstadoEnvio.DESCONOCIDO, nada.estado());
        assertNull(nada.costo(), "un envío que no se informa no vale cero");
        assertEquals(EstadoEnvio.DESCONOCIDO, AdaptadorGoogleShopping.envio("Llega mañana", "COP", precio).estado());
    }

    @Test
    void elEnvioGratisConMinimoSoloCuentaSiElPrecioLoAlcanza() {
        String texto = "Envío gratis en compras superiores a $ 150.000";
        assertEquals(EstadoEnvio.GRATIS,
                AdaptadorGoogleShopping.envio(texto, "COP", new BigDecimal("159900")).estado());
        assertEquals(EstadoEnvio.DESCONOCIDO,
                AdaptadorGoogleShopping.envio(texto, "COP", new BigDecimal("99900")).estado());
        assertEquals(EstadoEnvio.DESCONOCIDO,
                AdaptadorGoogleShopping.envio("Free delivery on $45+", "USD", null).estado());
    }

    @Test
    void reconoceVariasMonedasYNoAdivinaLasDesconocidas() {
        assertEquals(java.util.Optional.of("COP"), AdaptadorGoogleShopping.monedaDe("$ 159.900"));
        assertEquals(java.util.Optional.of("COP"), AdaptadorGoogleShopping.monedaDe("COL$ 159.900"));
        assertEquals(java.util.Optional.of("USD"), AdaptadorGoogleShopping.monedaDe("USD 25.00"));
        assertEquals(java.util.Optional.of("MXN"), AdaptadorGoogleShopping.monedaDe("MX$ 1.299,00"));
        assertEquals(java.util.Optional.of("EUR"), AdaptadorGoogleShopping.monedaDe("19,99 €"));
        assertTrue(AdaptadorGoogleShopping.monedaDe("A$ 45.00").isEmpty());
        assertTrue(AdaptadorGoogleShopping.monedaDe("¥ 3,000").isEmpty());
        assertTrue(AdaptadorGoogleShopping.monedaDe("S/ 120.00").isEmpty());
    }

    @Test
    void avisaCuandoElPrecioNoEsElPrecioCompleto() {
        assertNull(AdaptadorGoogleShopping.alertaPrecio("$ 159.900", new BigDecimal("159900")));
        assertEquals("Es un precio \"desde\" o por cuotas, no el precio completo",
                AdaptadorGoogleShopping.alertaPrecio("Desde $ 159.900", new BigDecimal("159900")));
        assertTrue(AdaptadorGoogleShopping.alertaPrecio("$ 99.900/mes", new BigDecimal("99900")) != null);
        // El texto dice 359.718 pero el valor leído es 100 veces más: no se usa sin revisarlo.
        assertEquals("El precio que leímos no coincide con el que muestra la tienda",
                AdaptadorGoogleShopping.alertaPrecio("$ 359.718", new BigDecimal("35971800")));
    }

    @Test
    void unaRespuestaConVariasMonedasCondicionesYEnviosQuedaBienClasificada() {
        String respuesta = """
                {"search_metadata":{"status":"Success"},
                 "shopping_results":[
                   {"title":"Apple iPhone 13 128 GB","product_id":"1","source":"Alkosto","price":"$ 2.899.900",
                    "extracted_price":2899900,"delivery":"Envío gratis","second_hand_condition":"refurbished"},
                   {"title":"Apple iPhone 13 128 GB Nuevo","product_id":"2","source":"Éxito","price":"$ 3.199.900",
                    "extracted_price":3199900,"delivery":"Envío desde $ 9.900"},
                   {"title":"iPhone 13 128 GB","product_id":"3","source":"Tienda MX","price":"A$ 900.00",
                    "extracted_price":900},
                   {"title":"Televisor de exhibición","product_id":"4","source":"Ktronix","price":"$ 1.500.000",
                    "extracted_price":1500000,"tag":"Exhibición"}
                 ]}""";
        servidor.expect(once(), requestTo(startsWith(URL + "/search.json")))
                .andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

        List<OfertaNormalizada> ofertas = crear(CLAVE).consultarApi(consulta("iphone 13"));

        assertEquals(List.of("1", "2", "4"), ofertas.stream().map(OfertaNormalizada::getIdExterno).toList(),
                "la de una moneda que no se reconoce queda por fuera");
        assertEquals("reacondicionado", ofertas.get(0).getCondicion());
        assertEquals(EstadoEnvio.GRATIS, ofertas.get(0).getEstadoEnvio());
        assertEquals("nuevo", ofertas.get(1).getCondicion());
        assertEquals(EstadoEnvio.ESTIMADO, ofertas.get(1).getEstadoEnvio());
        assertTrue(ofertas.get(1).totalConfirmado().isEmpty(), "con envío estimado no hay precio total confirmado");
        assertEquals("exhibicion", ofertas.get(2).getCondicion());
        assertEquals(EstadoEnvio.DESCONOCIDO, ofertas.get(2).getEstadoEnvio());
        assertNull(ofertas.get(2).getCostoEnvio());
        assertEquals(EnlaceCompra.Tipo.BUSQUEDA_GOOGLE, ofertas.get(2).enlace().tipo());
    }
}
