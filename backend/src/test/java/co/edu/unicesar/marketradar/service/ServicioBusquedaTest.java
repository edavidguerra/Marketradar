package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.PropiedadesPrueba;
import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplace;
import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.ProductoMvp;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import co.edu.unicesar.marketradar.service.mvp.SeleccionPonderadaMvp;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioBusquedaTest {

    private ExecutorService ejecutor;
    private ServicioPersistenciaOfertas persistencia;

    @BeforeEach
    void preparar() {
        ejecutor = Executors.newFixedThreadPool(4);
        persistencia = mock(ServicioPersistenciaOfertas.class);
        when(persistencia.guardarOfertas(any())).thenReturn(Map.of());
    }

    @AfterEach
    void cerrar() {
        ejecutor.shutdownNow();
    }

    @Test
    void ordenaPorPrecioTotalYMarcaElMejorPrecio() {
        AdaptadorMarketplace tiendaA = adaptador("a", List.of(oferta("a", "1", "30000", "5000"),
                oferta("a", "2", "20000", null)));
        AdaptadorMarketplace tiendaB = adaptador("b", List.of(oferta("b", "3", "25000", "0")));

        ResultadoBusqueda resultado = servicio(tiendaA, tiendaB).buscar(consulta(null));

        List<String> ids = resultado.getOfertas().stream().map(OfertaNormalizada::getIdExterno).toList();
        assertEquals(List.of("2", "3", "1"), ids);
        assertTrue(resultado.esMejorPrecio(resultado.getOfertas().get(0)));
        assertTrue(resultado.getAdvertencias().isEmpty());
        assertTrue(resultado.getProductoMvp().isPresent());
    }

    @Test
    void siUnaTiendaFallaDevuelveLasDemasConAdvertencia() {
        AdaptadorMarketplace sana = adaptador("sana", List.of(oferta("sana", "1", "10000", null)));
        AdaptadorMarketplace rota = mock(AdaptadorMarketplace.class);
        when(rota.codigo()).thenReturn("rota");
        when(rota.estaHabilitado()).thenReturn(true);
        when(rota.buscar(any())).thenThrow(new MarketplaceException("rota", "caída"));

        ResultadoBusqueda resultado = servicio(sana, rota).buscar(consulta(null));

        assertEquals(1, resultado.getTotalResultados());
        assertEquals(1, resultado.getAdvertencias().size());
        assertTrue(resultado.getAdvertencias().get(0).contains("rota"));
    }

    @Test
    void laAdvertenciaUsaElNombreVisibleDeLaTienda() {
        assertEquals("Mercado Libre", ServicioBusqueda.nombreVisible("mercadolibre"));
        assertEquals("AliExpress", ServicioBusqueda.nombreVisible("aliexpress"));
        assertEquals("Shopify", ServicioBusqueda.nombreVisible("shopify"));
        assertEquals("otra", ServicioBusqueda.nombreVisible("otra"));
    }

    @Test
    void reutilizaLaCacheEnBusquedasRepetidas() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(oferta("a", "1", "10000", null)));
        ServicioBusqueda servicio = servicio(tienda);

        servicio.buscar(consulta("precio_asc"));
        servicio.buscar(consulta("precio_desc"));

        verify(tienda, times(1)).buscar(any());
    }

    @Test
    void unaBusquedaQueFallaSeVuelveAIntentarPronto() {
        AtomicLong nanos = new AtomicLong();
        AdaptadorMarketplace sana = adaptador("sana", List.of(oferta("sana", "1", "10000", null)));
        AdaptadorMarketplace rota = mock(AdaptadorMarketplace.class);
        when(rota.codigo()).thenReturn("rota");
        when(rota.estaHabilitado()).thenReturn(true);
        when(rota.buscar(any())).thenThrow(new MarketplaceException("rota", "caída"));
        ServicioBusqueda servicio = new ServicioBusqueda(List.of(sana, rota), ejecutor,
                CircuitBreakerRegistry.ofDefaults(), persistencia, PropiedadesPrueba.crear(),
                new SeleccionPonderadaMvp(PropiedadesPrueba.crear()), nanos::get);

        servicio.buscar(consulta(null));
        servicio.buscar(consulta(null));
        verify(sana, times(1)).buscar(any());

        nanos.addAndGet(ServicioBusqueda.VIGENCIA_CON_FALLAS.plusSeconds(1).toNanos());
        servicio.buscar(consulta(null));
        verify(sana, times(2)).buscar(any());
    }

    @Test
    void unaBusquedaSinFallasSeRecuerdaLaHoraCompleta() {
        AtomicLong nanos = new AtomicLong();
        AdaptadorMarketplace tienda = adaptador("a", List.of(oferta("a", "1", "10000", null)));
        ServicioBusqueda servicio = new ServicioBusqueda(List.of(tienda), ejecutor,
                CircuitBreakerRegistry.ofDefaults(), persistencia, PropiedadesPrueba.crear(),
                new SeleccionPonderadaMvp(PropiedadesPrueba.crear()), nanos::get);

        servicio.buscar(consulta(null));
        nanos.addAndGet(ServicioBusqueda.VIGENCIA_CON_FALLAS.plusSeconds(1).toNanos());
        servicio.buscar(consulta(null));

        verify(tienda, times(1)).buscar(any());
    }

    @Test
    void sinTiendasHabilitadasAvisaAlUsuario() {
        ResultadoBusqueda resultado = servicio().buscar(consulta(null));

        assertEquals(0, resultado.getTotalResultados());
        assertEquals(1, resultado.getAdvertencias().size());
    }

    @Test
    void guardaEnElHistorialLaFotoDeUnProductoEncontrado() {
        OfertaNormalizada sinFoto = oferta("a", "1", "10000", null);
        OfertaNormalizada conFoto = conImagen(oferta("a", "2", "20000", null), "https://fotos.ejemplo.com/2.jpg");
        ServicioBusqueda servicio = servicio(adaptador("a", List.of(sinFoto, conFoto)));

        servicio.buscar(consulta(null), 7L);

        verify(persistencia).registrarBusqueda(eq("licuadora"), eq(2), anyInt(), eq(7L),
                eq("https://fotos.ejemplo.com/2.jpg"));
    }

    @Test
    void laFotoDelHistorialSoloAceptaEnlacesSeguros() {
        OfertaNormalizada insegura = conImagen(oferta("a", "1", "10000", null), "http://fotos.ejemplo.com/1.jpg");
        OfertaNormalizada segura = conImagen(oferta("a", "2", "20000", null), "https://fotos.ejemplo.com/2.jpg");
        OfertaNormalizada otra = conImagen(oferta("a", "3", "30000", null), "https://fotos.ejemplo.com/3.jpg");
        ProductoMvp mvp = new ProductoMvp(otra, 90, List.of("Mejor precio"));

        assertEquals("https://fotos.ejemplo.com/3.jpg",
                ServicioBusqueda.imagenRepresentativa(mvp, List.of(insegura, segura)));
        assertEquals("https://fotos.ejemplo.com/2.jpg",
                ServicioBusqueda.imagenRepresentativa(null, List.of(insegura, segura)));
        assertNull(ServicioBusqueda.imagenRepresentativa(null, List.of(insegura)));
    }

    private static OfertaNormalizada conImagen(OfertaNormalizada oferta, String urlImagen) {
        return OfertaNormalizada.builder()
                .codigoMarketplace(oferta.getCodigoMarketplace()).idExterno(oferta.getIdExterno())
                .titulo(oferta.getTitulo()).precio(oferta.getPrecio()).moneda(oferta.getMoneda())
                .condicion("nuevo").vendedor("Vendedor").urlDestino(oferta.getUrlDestino()).urlImagen(urlImagen)
                .build();
    }

    @Test
    void conIALaListaYElResumenLosEligeGemini() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(oferta("a", "1", "30000", "0"),
                oferta("a", "2", "20000", "0"), oferta("a", "3", "25000", "0")));
        ServicioIABusqueda ia = mock(ServicioIABusqueda.class);
        when(ia.analizar(any(), any())).thenAnswer(invocacion -> {
            List<OfertaNormalizada> recibidas = invocacion.getArgument(1);
            return java.util.Optional.of(new ServicioIABusqueda.Analisis(List.of(recibidas.get(2)),
                    "La licuadora 3 cuesta $ 25.000."));
        });
        ServicioBusqueda servicio = servicio(tienda);
        servicio.usarIA(ia);

        ResultadoBusqueda resultado = servicio.buscar(consulta(null));

        assertEquals(List.of("3"), resultado.getOfertas().stream().map(OfertaNormalizada::getIdExterno).toList());
        assertEquals("La licuadora 3 cuesta $ 25.000.", resultado.getResumenIa().orElseThrow());
    }

    @Test
    void siLaIANoRespondeSeUsanLosFiltrosPropios() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(oferta("a", "1", "30000", "0"), oferta("a", "2", "20000", "0")));
        ServicioIABusqueda ia = mock(ServicioIABusqueda.class);
        when(ia.analizar(any(), any())).thenReturn(java.util.Optional.empty());
        ServicioBusqueda servicio = servicio(tienda);
        servicio.usarIA(ia);

        ResultadoBusqueda resultado = servicio.buscar(consulta(null));

        assertEquals(2, resultado.getTotalResultados());
        assertTrue(resultado.getResumenIa().isEmpty());
    }

    @Test
    void elRangoDePrecioDejaSoloLoQueCuestaEntreLosDosValores() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(oferta("a", "1", "30000", "5000"),
                oferta("a", "2", "20000", "0"), oferta("a", "3", "25000", "0"), oferta("a", "4", "50000", "0")));
        ServicioBusqueda servicio = servicio(tienda);

        ResultadoBusqueda resultado = servicio.buscar(consulta(null)
                .conRangoDePrecio(new BigDecimal("25000"), new BigDecimal("35000")), 7L);

        // Se cuenta el precio total: la 1 cuesta 30.000 más 5.000 de envío y entra justo en el máximo.
        assertEquals(List.of("3", "1"), resultado.getOfertas().stream().map(OfertaNormalizada::getIdExterno).toList());
        assertEquals(2, resultado.getTotalResultados());
        assertEquals(4, resultado.getTotalSinFiltro());
        assertTrue(resultado.esMejorPrecio(resultado.getOfertas().get(0)));
        assertEquals("3", resultado.getProductoMvp().orElseThrow().oferta().getIdExterno());
        verify(persistencia, times(0)).registrarBusqueda(any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void cambiarElRangoDePrecioNoVuelveAConsultarLasTiendas() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(oferta("a", "1", "30000", "0"), oferta("a", "2", "20000", "0")));
        ServicioBusqueda servicio = servicio(tienda);

        servicio.buscar(consulta(null));
        ResultadoBusqueda filtrado = servicio.buscar(consulta(null).conRangoDePrecio(null, new BigDecimal("1000")));

        verify(tienda, times(1)).buscar(any());
        assertEquals(0, filtrado.getTotalResultados());
        assertEquals(2, filtrado.getTotalSinFiltro());
    }

    @Test
    void conRangoDePrecioNoSeMuestraElResumenSiQuedaronOfertasPorFuera() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(oferta("a", "1", "30000", "0"),
                oferta("a", "2", "20000", "0"), oferta("a", "3", "25000", "0")));
        ServicioIABusqueda ia = mock(ServicioIABusqueda.class);
        when(ia.analizar(any(), any())).thenAnswer(invocacion -> java.util.Optional.of(
                new ServicioIABusqueda.Analisis(invocacion.getArgument(1), "La más barata cuesta $ 20.000.")));
        ServicioBusqueda servicio = servicio(tienda);
        servicio.usarIA(ia);

        ResultadoBusqueda filtrado = servicio.buscar(consulta(null).conRangoDePrecio(new BigDecimal("24000"), null));

        assertEquals(2, filtrado.getTotalResultados());
        assertTrue(filtrado.getResumenIa().isEmpty());
    }

    @Test
    void lasCajasVaciasNoSeMuestranNiSeLePasanALaIA() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(
                titulada("1", "Caja iPhone 17 Pro Original", "150000"),
                titulada("2", "Apple iPhone 17 Pro 256 GB Plata", "5200000"),
                titulada("3", "iPhone 17 Pro Max Empty Box", "120000"),
                titulada("4", "iPhone 17 Pro Max 256 GB", "6100000")));
        ServicioIABusqueda ia = mock(ServicioIABusqueda.class);
        when(ia.analizar(any(), any())).thenAnswer(invocacion -> java.util.Optional.of(
                new ServicioIABusqueda.Analisis(invocacion.getArgument(1), null)));
        ServicioBusqueda servicio = servicio(tienda);
        servicio.usarIA(ia);

        ResultadoBusqueda resultado = servicio.buscar(ConsultaBusqueda.crear("iphone 17 pro", null, Set.of(), 1, 10));

        // Pidió el Pro: el Pro Max no se recomienda, se muestra aparte diciendo por qué.
        assertEquals(List.of("2"), resultado.getOfertas().stream().map(OfertaNormalizada::getIdExterno).toList());
        assertEquals("4", resultado.getDetalle().alternativas().get(0).oferta().getIdExterno());
        assertTrue(resultado.getDetalle().alternativas().get(0).motivos().get(0).contains("Pro Max"),
                resultado.getDetalle().alternativas().get(0).motivos().toString());
        verify(ia).analizar(eq("iphone 17 pro"), org.mockito.ArgumentMatchers.argThat(lista -> lista.size() == 2));
    }

    @Test
    void siLaIAEligeSoloUnaFundaSeUsanLosFiltrosPropios() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(
                titulada("1", "Funda iPhone 17 Pro Silicona", "60000"),
                titulada("2", "Apple iPhone 17 Pro 256 GB Plata", "5200000"),
                titulada("3", "iPhone 17 Pro 512 GB Azul", "5900000"),
                titulada("4", "iPhone 17 Pro Max 256 GB", "6100000")));
        ServicioIABusqueda ia = mock(ServicioIABusqueda.class);
        when(ia.analizar(any(), any())).thenAnswer(invocacion -> {
            List<OfertaNormalizada> recibidas = invocacion.getArgument(1);
            return java.util.Optional.of(new ServicioIABusqueda.Analisis(List.of(recibidas.get(0)),
                    "La más barata es la funda a $ 60.000."));
        });
        ServicioBusqueda servicio = servicio(tienda);
        servicio.usarIA(ia);

        ResultadoBusqueda resultado = servicio.buscar(ConsultaBusqueda.crear("iphone 17 pro", null, Set.of(), 1, 10));

        assertEquals(List.of("2", "3"), resultado.getOfertas().stream().map(OfertaNormalizada::getIdExterno).toList());
        assertEquals(List.of("4"), resultado.getDetalle().alternativas().stream()
                .map(a -> a.oferta().getIdExterno()).toList());
        assertTrue(resultado.getResumenIa().isEmpty());
    }

    @Test
    void soloSeRecomiendaElIphone13De128GbNuevoQuePidio() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(
                conCondicion(titulada("1", "Apple iPhone 13 128 GB Reacondicionado", "2100000"), "reacondicionado"),
                titulada("2", "Apple iPhone 13 Pro 128 GB", "2600000"),
                titulada("3", "Apple iPhone 13 256 GB", "2700000"),
                conCondicion(titulada("4", "Apple iPhone 13 128 GB Azul", "2750000"), null),
                titulada("5", "Apple iPhone 13 128 GB Medianoche", "2899900"),
                titulada("6", "Apple iPhone 13 mini 128 GB", "2300000")));

        ResultadoBusqueda resultado = servicio(tienda)
                .buscar(ConsultaBusqueda.crear("iphone 13 128 gb nuevo", null, Set.of(), 1, 10));

        assertEquals(List.of("5"), resultado.getOfertas().stream().map(OfertaNormalizada::getIdExterno).toList());
        assertEquals("5", resultado.getProductoMvp().orElseThrow().oferta().getIdExterno());
        assertNull(resultado.getDetalle().motivoSinRecomendacion());
        // Primero las que solo falta confirmar, después las que no cumplen; cada una dice qué le falta.
        Map<String, List<String>> motivos = new java.util.LinkedHashMap<>();
        resultado.getDetalle().alternativas().forEach(a -> motivos.put(a.oferta().getIdExterno(), a.motivos()));
        assertEquals(List.of("4", "1", "6", "2", "3"), List.copyOf(motivos.keySet()));
        assertEquals(List.of("La tienda no dice si es nuevo"), motivos.get("4"));
        assertEquals(List.of("Es reacondicionado, no nuevo"), motivos.get("1"));
        assertEquals(List.of("Es la versión Mini, no la que pidió"), motivos.get("6"));
        assertEquals(List.of("Es de 256 GB, no de 128 GB"), motivos.get("3"));
        assertEquals(List.of("modelo iphone 13", "128 GB", "nuevo"), resultado.getDetalle().requisitos());
    }

    @Test
    void siNingunaCumpleNoSeRecomiendaNingunaYSeDiceQueFalta() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(
                conCondicion(titulada("1", "Apple iPhone 13 128 GB", "2100000"), "reacondicionado"),
                titulada("2", "Apple iPhone 13 Pro 128 GB", "2600000")));

        ResultadoBusqueda resultado = servicio(tienda)
                .buscar(ConsultaBusqueda.crear("iphone 13 128 gb nuevo", null, Set.of(), 1, 10));

        assertTrue(resultado.getOfertas().isEmpty());
        assertTrue(resultado.getProductoMvp().isEmpty());
        assertEquals("Ninguna oferta confirma todo lo que usted pidió (modelo iphone 13, 128 GB, nuevo), así que no "
                + "recomendamos ninguna. Abajo están las que se parecen, con lo que les falta.",
                resultado.getDetalle().motivoSinRecomendacion());
        assertEquals(2, resultado.getDetalle().alternativas().size());
    }

    @Test
    void unPrecioAtipicoSeMuestraConAvisoPeroNoSeRecomienda() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(
                titulada("1", "Licuadora Oster 6640", "35971"),
                titulada("2", "Licuadora Oster 6640 vaso de vidrio", "389900"),
                titulada("3", "Licuadora Oster 6640 negra", "399900"),
                titulada("4", "Licuadora Oster 6640 3 velocidades", "359718000")));

        ResultadoBusqueda resultado = servicio(tienda)
                .buscar(ConsultaBusqueda.crear("licuadora oster 6640", null, Set.of(), 1, 10));

        List<OfertaNormalizada> ofertas = resultado.getOfertas();
        // La de $ 35.971 ya la quitaba el filtro de precios absurdamente bajos (suele ser un repuesto). La de
        // $ 359.718.000 se muestra, con el aviso.
        assertEquals(List.of("2", "3", "4"), ofertas.stream().map(OfertaNormalizada::getIdExterno).toList());
        assertEquals(ServicioBusqueda.ALERTA_ATIPICO, ofertas.get(2).getAlertaPrecio());
        assertNull(ofertas.get(0).getAlertaPrecio());
        assertEquals("2", resultado.getProductoMvp().orElseThrow().oferta().getIdExterno());
        assertTrue(resultado.esMejorPrecio(ofertas.stream().filter(o -> o.getIdExterno().equals("2")).findFirst().orElseThrow()));
    }

    @Test
    void elFiltroDePrecioCuentaLosEnviosConocidosYAvisaLosQueFaltan() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(
                oferta("a", "1", "95000", "10000"),
                oferta("a", "2", "98000", null),
                oferta("a", "3", "90000", "0")));

        ResultadoBusqueda resultado = servicio(tienda).buscar(ConsultaBusqueda.crear("licuadora", null, Set.of(), 1, 10)
                .conRangoDePrecio(null, new BigDecimal("100000")));

        // La 1 cuesta 105.000 con envío: queda por fuera. La 2 entra, pero su envío no se sabe.
        assertEquals(List.of("3", "2"), resultado.getOfertas().stream().map(OfertaNormalizada::getIdExterno).toList());
        assertEquals(1, resultado.getDetalle().sinTotalConfirmado());
        assertTrue(resultado.getDetalle().mejorPrecioConfirmado());
    }

    @Test
    void siLaMasBarataNoTieneElEnvioConfirmadoSeAvisa() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(oferta("a", "1", "80000", null),
                oferta("a", "2", "90000", "0")));

        ResultadoBusqueda resultado = servicio(tienda).buscar(consulta(null));

        assertEquals("1", resultado.getOfertas().get(0).getIdExterno());
        assertTrue(!resultado.getDetalle().mejorPrecioConfirmado(), "su precio no incluye el envío");
    }

    @Test
    void siElRangoDejaPorFueraLasQueCumplenSeDiceAsi() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(titulada("1", "Apple iPhone 13 128 GB Nuevo", "2899900")));

        ResultadoBusqueda resultado = servicio(tienda).buscar(ConsultaBusqueda.crear("iphone 13 nuevo", null,
                Set.of(), 1, 10).conRangoDePrecio(null, new BigDecimal("1000000")));

        assertEquals("Ninguna de las ofertas que cumplen su búsqueda está en el rango de precio que eligió.",
                resultado.getDetalle().motivoSinRecomendacion());
    }

    @Test
    void laBusquedaCorregidaConservaLoQueEscribioLaPersona() {
        AdaptadorMarketplace tienda = adaptador("a", List.of(
                titulada("1", "Apple iPhone 13 256 GB", "2700000"),
                titulada("2", "Apple iPhone 13 128 GB", "2899900")));

        ResultadoBusqueda resultado = servicio(tienda).buscar(ConsultaBusqueda.crear("iphone 13", null, Set.of(), 1, 10)
                .conTextoOriginal("iphone 13 de 128 gb"));

        assertEquals(List.of("2"), resultado.getOfertas().stream().map(OfertaNormalizada::getIdExterno).toList());
    }

    private static OfertaNormalizada conCondicion(OfertaNormalizada oferta, String condicion) {
        return OfertaNormalizada.builder().codigoMarketplace(oferta.getCodigoMarketplace())
                .idExterno(oferta.getIdExterno()).titulo(oferta.getTitulo()).precio(oferta.getPrecio())
                .moneda(oferta.getMoneda()).costoEnvio(oferta.getCostoEnvio()).condicion(condicion)
                .vendedor(oferta.getVendedor()).urlDestino(oferta.getUrlDestino()).build();
    }

    private static OfertaNormalizada titulada(String id, String titulo, String precio) {
        return OfertaNormalizada.builder()
                .codigoMarketplace("a").idExterno(id).titulo(titulo).precio(new BigDecimal(precio)).moneda("COP")
                .costoEnvio(BigDecimal.ZERO).condicion("nuevo").vendedor("Tienda").urlDestino("https://ejemplo.com/" + id)
                .build();
    }

    private ServicioBusqueda servicio(AdaptadorMarketplace... adaptadores) {
        return new ServicioBusqueda(List.of(adaptadores), ejecutor, CircuitBreakerRegistry.ofDefaults(),
                persistencia, PropiedadesPrueba.crear(), new SeleccionPonderadaMvp(PropiedadesPrueba.crear()));
    }

    private static ConsultaBusqueda consulta(String orden) {
        return ConsultaBusqueda.crear("licuadora", orden, Set.of(), 1, 10);
    }

    private static AdaptadorMarketplace adaptador(String codigo, List<OfertaNormalizada> ofertas) {
        AdaptadorMarketplace adaptador = mock(AdaptadorMarketplace.class);
        when(adaptador.codigo()).thenReturn(codigo);
        when(adaptador.estaHabilitado()).thenReturn(true);
        when(adaptador.buscar(any())).thenReturn(ofertas);
        return adaptador;
    }

    private static OfertaNormalizada oferta(String tienda, String id, String precio, String envio) {
        return OfertaNormalizada.builder()
                .codigoMarketplace(tienda).idExterno(id).titulo("Licuadora " + id)
                .precio(new BigDecimal(precio)).moneda("COP")
                .costoEnvio(envio == null ? null : new BigDecimal(envio))
                .condicion("nuevo").vendedor("Vendedor").urlDestino("https://ejemplo.com/" + id)
                .build();
    }
}
