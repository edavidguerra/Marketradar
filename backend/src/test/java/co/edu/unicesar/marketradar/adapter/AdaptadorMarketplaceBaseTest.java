package co.edu.unicesar.marketradar.adapter;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdaptadorMarketplaceBaseTest {

    private final ConversorMoneda conversor = new ConversorMoneda(PropiedadesPrueba.crear());
    private final ConsultaBusqueda consulta = ConsultaBusqueda.crear("audifonos", null, Set.of(), 1, 10);

    @Test
    void convierteDolaresAPesos() {
        AdaptadorSimulado adaptador = new AdaptadorSimulado(true, List.of(oferta("10.50", "2.00", "USD")));

        OfertaNormalizada convertida = adaptador.buscar(consulta).get(0);

        assertEquals("COP", convertida.getMoneda());
        assertEquals(new BigDecimal("42000.00"), convertida.getPrecio());
        assertEquals(new BigDecimal("8000.00"), convertida.getCostoEnvio());
    }

    @Test
    void noConsultaSiNoEstaHabilitado() {
        AdaptadorSimulado adaptador = new AdaptadorSimulado(false, List.of());
        assertThrows(MarketplaceException.class, () -> adaptador.buscar(consulta));
    }

    @Test
    void traduceErroresInesperados() {
        AdaptadorMarketplaceBase adaptador = new AdaptadorMarketplaceBase("roto", conversor, 5) {
            @Override
            public boolean estaHabilitado() {
                return true;
            }

            @Override
            protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
                throw new IllegalStateException("respuesta inválida");
            }
        };
        assertThrows(MarketplaceException.class, () -> adaptador.buscar(consulta));
    }

    private static OfertaNormalizada oferta(String precio, String envio, String moneda) {
        return new OfertaNormalizada("simulado", "1", "Audífonos", new BigDecimal(precio), moneda,
                new BigDecimal(envio), "nuevo", "Tienda", "https://ejemplo.com/1", null, null);
    }

    private final class AdaptadorSimulado extends AdaptadorMarketplaceBase {
        private final boolean habilitado;
        private final List<OfertaNormalizada> respuesta;

        AdaptadorSimulado(boolean habilitado, List<OfertaNormalizada> respuesta) {
            super("simulado", conversor, 5);
            this.habilitado = habilitado;
            this.respuesta = respuesta;
        }

        @Override
        public boolean estaHabilitado() {
            return habilitado;
        }

        @Override
        protected List<OfertaNormalizada> consultarApi(ConsultaBusqueda consulta) {
            return respuesta;
        }
    }
}
