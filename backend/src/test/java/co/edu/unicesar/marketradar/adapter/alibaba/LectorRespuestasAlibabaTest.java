package co.edu.unicesar.marketradar.adapter.alibaba;

import co.edu.unicesar.marketradar.domain.mayorista.OfertaMayorista;
import co.edu.unicesar.marketradar.domain.mayorista.UnidadVenta;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LectorRespuestasAlibabaTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Forma de /alibaba/v1/search según https://api.sh/openapi/alibaba.json (2026-11-14). */
    static final String BUSQUEDA = """
            {"query":"cotton socks","page":1,"next_page":2,"sponsored_count":1,"cached":true,
             "fetched_at":"2026-11-14T10:00:00Z","products":[
              {"id":"1600000000001","title":"Cotton socks men","url":"//www.alibaba.com/product-detail/socks_1600000000001.html",
               "image":"//s.alicdn.com/socks.jpg","price":"US $0.50-0.80","price_min":0.5,"price_max":0.8,"currency":"USD",
               "quantity_prices":[{"min_quantity":100,"max_quantity":999,"price":0.8,"unit":"pairs"},
                                  {"min_quantity":1000,"max_quantity":null,"price":0.5,"unit":"pairs"}],
               "min_order":"100 pairs","unit":"pairs","sponsored":true,
               "supplier":{"name":"Zhejiang Socks Co.","country":"CN","years":8,"verified":true}},
              {"id":"1600000000002","title":"Phone case","url":"http://insecure.example.com/x","price":"US $1"},
              {"title":"sin id"}]}""";

    /** Forma de /alibaba/v1/product. */
    static final String DETALLE = """
            {"id":"1600000000001","title":"Cotton socks men","url":"https://www.alibaba.com/product-detail/socks_1600000000001.html",
             "fetched_at":"2026-11-14T11:00:00Z",
             "price":{"currency":"USD","range":"$0.50 - $0.80","unit":"pairs","trade_term":"FOB",
                      "tiers":[{"min_quantity":100,"max_quantity":999,"price":0.8,"formatted":"$0.80"},
                               {"min_quantity":1000,"max_quantity":-1,"price":0.5,"formatted":"$0.50"}]},
             "min_order":{"quantity":100,"unit":"pairs"},
             "options":[{"name":"Color","values":[{"id":"c1","name":"Black","available":true},
                                                  {"id":"c2","name":"White","available":false}]}],
             "skus":[{"id":"sku-1","price":0.8,"options":["Black"]},{"id":"sku-2","price":0.8,"options":["White"]}],
             "supplier":{"name":"Zhejiang Socks Co.","country_name":"China","years":8,"verified":true}}""";

    @Test
    void leeLaBusquedaConEscalasMinimoYAnuncios() throws Exception {
        LectorRespuestasAlibaba.Pagina pagina = LectorRespuestasAlibaba.leerBusqueda(JSON.readTree(BUSQUEDA));
        assertEquals(2, pagina.siguientePagina());
        assertTrue(pagina.desdeCache());
        assertEquals(1, pagina.ofertas().size(), "Se descartan las que no tienen id, título o enlace seguro");
        OfertaMayorista o = pagina.ofertas().get(0);
        assertEquals("https://www.alibaba.com/product-detail/socks_1600000000001.html", o.url());
        assertEquals("https://s.alicdn.com/socks.jpg", o.imagen());
        assertEquals(100L, o.pedidoMinimo());
        assertEquals(UnidadVenta.PAR, o.unidadPedidoMinimo());
        assertEquals(UnidadVenta.PAR, o.unidad());
        assertEquals(2, o.escalas().size());
        assertNull(o.escalas().get(1).maximo());
        assertTrue(o.patrocinado());
        assertEquals("USD", o.moneda());
        assertEquals(8, o.aniosProveedor());
    }

    @Test
    void leeElDetalleConVariantesYTopeAbierto() throws Exception {
        OfertaMayorista o = LectorRespuestasAlibaba.leerProducto(JSON.readTree(DETALLE));
        assertEquals(100L, o.pedidoMinimo());
        assertEquals(UnidadVenta.PAR, o.unidad());
        assertNull(o.escalas().get(1).maximo(), "-1 es sin tope");
        assertEquals(new BigDecimal("0.5"), o.precioMinimo());
        assertEquals("detalle", o.fuente());
        assertFalse(o.variantes().isEmpty());
        assertTrue(o.variantes().stream().anyMatch(v -> Boolean.FALSE.equals(v.disponible())));
    }

    @Test
    void sinProductosLaRespuestaNoEsValida() throws Exception {
        ApiShException e = assertThrows(ApiShException.class,
                () -> LectorRespuestasAlibaba.leerBusqueda(JSON.readTree("{\"error\":\"x\"}")));
        assertEquals(ApiShException.Tipo.RESPUESTA_INVALIDA, e.getTipo());
    }

    @Test
    void leeLaPresentacionCuandoElProveedorLaDice() throws Exception {
        String json = DETALLE.replace("\"unit\":\"pairs\",\"trade_term\"", "\"unit\":\"pack (12 pairs)\",\"trade_term\"")
                .replace("{\"quantity\":100,\"unit\":\"pairs\"}", "{\"quantity\":10,\"unit\":\"packs\"}")
                .replace("\"min_quantity\":100,", "\"min_quantity\":10,");
        OfertaMayorista o = LectorRespuestasAlibaba.leerProducto(JSON.readTree(json));
        assertEquals(UnidadVenta.PAQUETE, o.unidad());
        assertEquals(12, o.presentacion().cantidad());
        assertEquals(UnidadVenta.PAR, o.presentacion().unidadBase());
    }
}
