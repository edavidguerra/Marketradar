package co.edu.unicesar.marketradar.service.mayorista;

import co.edu.unicesar.marketradar.adapter.alibaba.ApiShException;
import co.edu.unicesar.marketradar.adapter.alibaba.ClienteAlibabaApiSh;
import co.edu.unicesar.marketradar.domain.mayorista.EscalaPrecio;
import co.edu.unicesar.marketradar.domain.mayorista.OfertaMayorista;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Fichas de producto simuladas para las pruebas de Emprender: por defecto, la ficha de cada oferta dice lo mismo que
 * la búsqueda (precio verificable). Se busca por URL o por id. Una referencia sin ficha registrada responde como si Alibaba no contestara.
 */
final class FichasSimuladas {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Map<String, JsonNode> porUrl = new HashMap<>();

    private FichasSimuladas() {
    }

    /** Conecta las fichas al cliente simulado. */
    static FichasSimuladas en(ClienteAlibabaApiSh cliente) {
        FichasSimuladas fichas = new FichasSimuladas();
        when(cliente.producto(anyString())).thenAnswer(invocacion -> {
            JsonNode ficha = fichas.porUrl.get(invocacion.<String>getArgument(0));
            if (ficha == null) {
                throw new ApiShException(ApiShException.Tipo.NO_DISPONIBLE, "sin ficha simulada", null);
            }
            return ficha;
        });
        return fichas;
    }

    /** Registra fichas iguales a la búsqueda. */
    FichasSimuladas iguales(List<OfertaMayorista> ofertas) {
        ofertas.forEach(o -> {
            porUrl.put(o.url(), igual(o));
            porUrl.put(o.id(), igual(o));
        });
        return this;
    }

    /** Registra una ficha escrita a mano para una oferta. */
    FichasSimuladas ficha(OfertaMayorista oferta, String json) {
        try {
            porUrl.put(oferta.url(), JSON.readTree(json));
            porUrl.put(oferta.id(), JSON.readTree(json));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return this;
    }

    /** Ficha con las mismas escalas, moneda, unidad y pedido mínimo que la búsqueda. */
    static JsonNode igual(OfertaMayorista o) {
        ObjectNode raiz = JSON.createObjectNode();
        raiz.put("id", o.id());
        raiz.put("title", o.titulo());
        raiz.put("url", o.url());
        ObjectNode precio = raiz.putObject("price");
        if (o.moneda() != null) {
            precio.put("currency", o.moneda());
        }
        precio.put("unit", o.unidad().name().equals("PAR") ? "pairs" : "pieces");
        ArrayNode escalas = precio.putArray("tiers");
        for (EscalaPrecio e : o.escalas()) {
            ObjectNode escala = escalas.addObject();
            escala.put("min_quantity", e.minimo());
            if (e.maximo() == null) {
                escala.putNull("max_quantity");
            } else {
                escala.put("max_quantity", e.maximo());
            }
            escala.put("price", e.precio());
        }
        if (o.pedidoMinimo() != null) {
            ObjectNode minimo = raiz.putObject("min_order");
            minimo.put("quantity", o.pedidoMinimo());
            minimo.put("unit", o.unidadPedidoMinimo().name().equals("PAR") ? "pairs" : "pieces");
        }
        return raiz;
    }
}
