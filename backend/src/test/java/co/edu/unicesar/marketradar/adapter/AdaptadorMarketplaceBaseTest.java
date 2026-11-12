package co.edu.unicesar.marketradar.adapter;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdaptadorMarketplaceBaseTest {

    private final ConversorMoneda conversor = new ConversorMoneda(PropiedadesPrueba.crear(),
            moneda -> "USD".equals(moneda)
                    ? Optional.of(new TasaCambio("USD", new BigDecimal("4000"), LocalDate.of(2026, 10, 28), "prueba"))
                    : Optional.empty());
    private final ConsultaBusqueda consulta = ConsultaBusqueda.crear("audifonos", null, Set.of(), 1, 10);

    @Test
    void convierteDolaresAPesos() {
        AdaptadorSimulado adaptador = new AdaptadorSimulado(true, List.of(oferta("10.50", "2.00", "USD")));

        OfertaNormalizada convertida = adaptador.buscar(consulta).get(0);

        assertEquals("COP", convertida.getMoneda());
        assertEquals(new BigDecimal("42000"), convertida.getPrecio());
        assertEquals(new BigDecimal("8000"), convertida.getCostoEnvio());
    }

    @Test
    void guardaElPrecioOriginalYLaTasaConSuFuenteYFecha() {
        AdaptadorSimulado adaptador = new AdaptadorSimulado(true, List.of(oferta("10.50", "2.00", "USD")));

        OfertaNormalizada convertida = adaptador.buscar(consulta).get(0);

        assertEquals(new BigDecimal("10.50"), convertida.getPrecioOriginal());
        assertEquals("USD", convertida.getMonedaOriginal());
        assertEquals("prueba", convertida.getTasaAplicada().fuente());
        assertEquals(LocalDate.of(2026, 10, 28), convertida.getTasaAplicada().vigenteDesde());
    }

    @Test
    void unaOfertaYaConvertidaNoSeConvierteOtraVez() {
        TasaCambio tasa = new TasaCambio("USD", new BigDecimal("4000"), LocalDate.of(2026, 10, 28), "prueba");
        OfertaNormalizada yaEnPesos = oferta("10.50", "2.00", "USD")
                .convertida(new BigDecimal("42000"), new BigDecimal("8000"), "COP", tasa);
        AdaptadorSimulado adaptador = new AdaptadorSimulado(true, List.of(yaEnPesos));

        OfertaNormalizada resultado = adaptador.buscar(consulta).get(0);

        assertEquals(new BigDecimal("42000"), resultado.getPrecio(), "no se multiplica dos veces por la tasa");
        assertThrows(IllegalStateException.class,
                () -> yaEnPesos.convertida(BigDecimal.ONE, null, "COP", tasa));
    }

    @Test
    void sinTasaParaLaMonedaSeDejaPorFueraSoloEsaOferta() {
        AdaptadorSimulado adaptador = new AdaptadorSimulado(true,
                List.of(oferta("1299", "0", "MXN"), oferta("10.50", "0", "USD"), oferta("50000", "0", "COP")));

        List<OfertaNormalizada> ofertas = adaptador.buscar(consulta);

        assertEquals(List.of(new BigDecimal("42000"), new BigDecimal("50000")),
                ofertas.stream().map(OfertaNormalizada::getPrecio).toList());
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
        return OfertaNormalizada.builder()
                .codigoMarketplace("simulado").idExterno("1").titulo("Audífonos")
                .precio(new BigDecimal(precio)).moneda(moneda).costoEnvio(new BigDecimal(envio))
                .condicion("nuevo").vendedor("Tienda").urlDestino("https://ejemplo.com/1")
                .build();
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
