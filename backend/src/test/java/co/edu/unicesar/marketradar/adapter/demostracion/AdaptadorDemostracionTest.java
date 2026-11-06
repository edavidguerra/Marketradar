package co.edu.unicesar.marketradar.adapter.demostracion;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptadorDemostracionTest {

    private static final List<AdaptadorDemostracion.Producto> CATALOGO = AdaptadorDemostracion.leerCatalogo();

    private static AdaptadorDemostracion crear(boolean habilitado) {
        return new AdaptadorDemostracion(new ConversorMoneda(PropiedadesPrueba.crear(), null), habilitado,
                "https://marketradar.onrender.com/", CATALOGO);
    }

    private static List<OfertaNormalizada> buscar(String termino) {
        return crear(true).buscar(ConsultaBusqueda.crear(termino, null, Set.of(), 1, 10));
    }

    @Test
    void elCatalogoTraeProductosCompletosYSinRepetir() {
        assertTrue(CATALOGO.size() >= 40);
        Set<String> ids = new HashSet<>();
        for (AdaptadorDemostracion.Producto producto : CATALOGO) {
            assertTrue(producto.id().matches("DEMO-\\d{3}"), producto.id());
            assertTrue(ids.add(producto.id()), "Id repetido: " + producto.id());
            assertFalse(producto.titulo().isBlank());
            assertTrue(producto.precio().signum() > 0);
            assertTrue(producto.envio().signum() >= 0);
            assertTrue(producto.calificacion().compareTo(BigDecimal.ONE) >= 0
                    && producto.calificacion().compareTo(BigDecimal.valueOf(5)) <= 0);
        }
    }

    @Test
    void encuentraLasOllasArrocerasConEnlaceALaPaginaDeDemostracion() {
        List<OfertaNormalizada> ofertas = buscar("Olla arrocera");

        assertEquals(4, ofertas.size());
        OfertaNormalizada primera = ofertas.get(0);
        assertEquals("demostracion", primera.getCodigoMarketplace());
        assertEquals("COP", primera.getMoneda());
        assertEquals("https://marketradar.onrender.com/tienda-demostracion.html?producto=DEMO-001",
                primera.getUrlDestino());
        assertTrue(ofertas.stream().allMatch(oferta -> oferta.getTitulo().toLowerCase().contains("arrocera")));
    }

    @Test
    void ignoraTildesMayusculasPluralesYPalabrasDeRelleno() {
        assertEquals(3, buscar("TENSIOMETRO").size());
        assertEquals(3, buscar("tensiómetro").size());
        assertEquals(4, buscar("ollas arroceras").size());
        assertEquals(4, buscar("olla arrocera barata").size());
        assertEquals(3, buscar("licuadora para jugos").size());
    }

    @Test
    void sinCoincidenciasDevuelveVacio() {
        assertTrue(buscar("motosierra").isEmpty());
        assertTrue(buscar("de para").isEmpty());
    }

    @Test
    void soloSeConsultaSiEstaHabilitada() {
        assertTrue(crear(true).estaHabilitado());
        assertFalse(crear(false).estaHabilitado());
    }
}
