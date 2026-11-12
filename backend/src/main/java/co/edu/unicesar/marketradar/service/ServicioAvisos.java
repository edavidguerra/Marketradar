package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.BusquedaDao;
import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.domain.Aviso;
import co.edu.unicesar.marketradar.domain.Busqueda;
import co.edu.unicesar.marketradar.domain.CondicionProducto;
import co.edu.unicesar.marketradar.domain.EnlaceCompra;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.OfertaDestacada;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.RequisitosBusqueda;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Arma los avisos del inicio con datos reales: lo que la persona buscó hace poco y los productos
 * que más bajaron de precio. Siempre hay al menos una invitación a buscar.
 */
@Service
public class ServicioAvisos {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioAvisos.class);
    static final int DIAS_BAJA_PRECIO = 30;
    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");

    private final OfertaDao ofertaDao;
    private final BusquedaDao busquedaDao;

    public ServicioAvisos(OfertaDao ofertaDao, BusquedaDao busquedaDao) {
        this.ofertaDao = ofertaDao;
        this.busquedaDao = busquedaDao;
    }

    /**
     * @param usuarioId persona con sesión
     * @return avisos en orden de importancia; el último siempre es una invitación a buscar
     */
    public List<Aviso> avisosPara(long usuarioId) {
        List<Aviso> avisos = new ArrayList<>();
        try {
            seguimientoDeUltimaBusqueda(usuarioId).ifPresent(avisos::add);
            ofertaDao.buscarMayorBajaDePrecio(DIAS_BAJA_PRECIO).map(this::bajaDePrecio).ifPresent(avisos::add);
        } catch (AccesoDatosException e) {
            LOG.warn("No se pudieron armar los avisos del inicio", e);
        }
        avisos.add(new Aviso("INVITACION", "Compare antes de comprar",
                "Escriba el producto que necesita y le mostramos en qué tienda le sale más barato. Si la tienda "
                        + "informa el envío, se lo sumamos; si no, se lo decimos.",
                "Buscar un producto", null, null));
        return avisos;
    }

    private Optional<Aviso> seguimientoDeUltimaBusqueda(long usuarioId) {
        Optional<Busqueda> ultima = busquedaDao.listarPorUsuario(usuarioId, 1).stream().findFirst();
        if (ultima.isEmpty()) {
            return Optional.empty();
        }
        String termino = ultima.get().getTermino();
        return elegirSeguimiento(termino, ofertaDao.listarMasBaratasPorTitulo(palabrasParaLaBase(termino), CANDIDATAS))
                .map(destacada -> new Aviso("SEGUIMIENTO",
                        "Lo que buscó: " + termino,
                        destacada.oferta().getTitulo() + " está desde " + pesos(destacada.oferta().calcularPrecioTotal())
                                + (destacada.oferta().getCostoEnvio() == null ? " más envío" : "")
                                + ". Revise de nuevo antes de que cambie el precio.",
                        textoEnlace(destacada.oferta().getUrlDestino(), "Ver ofertas de " + termino), termino,
                        destacada.oferta().getUrlDestino()));
    }

    /** Ofertas guardadas que se revisan para el aviso de seguimiento. */
    static final int CANDIDATAS = 40;

    /**
     * La oferta guardada más barata que de verdad es lo que buscó la persona: sin cajas, fundas ni accesorios, del
     * mismo modelo, versión, capacidad y condición, y sin precios que no se parecen a los demás.
     *
     * @param termino lo que buscó
     * @param candidatas ofertas guardadas cuyo título se parece, de la más barata a la más cara
     * @return la oferta, o vacío si ninguna sirve
     */
    static Optional<OfertaDestacada> elegirSeguimiento(String termino, List<OfertaDestacada> candidatas) {
        String buscado = ServicioSugerencias.normalizar(termino);
        Set<String> palabras = Set.of(buscado.split(" "));
        RequisitosBusqueda requisitos = RequisitosBusqueda.de(termino);
        Map<OfertaNormalizada, OfertaDestacada> porNormalizada = new LinkedHashMap<>();
        for (OfertaDestacada destacada : candidatas) {
            normalizada(destacada).ifPresent(oferta -> porNormalizada.put(oferta, destacada));
        }
        List<OfertaNormalizada> producto = porNormalizada.keySet().stream()
                .filter(o -> !FiltroComplementos.esSinProducto(o, buscado, palabras))
                .filter(o -> !FiltroComplementos.esComplemento(o, buscado, palabras))
                .filter(o -> requisitos.evaluar(o.getTitulo(), o.condicionProducto()).nivel()
                        == RequisitosBusqueda.Nivel.CUMPLE)
                .toList();
        List<OfertaNormalizada> verificadas = ServicioBusqueda.marcarPreciosAtipicos(producto, requisitos);
        for (int i = 0; i < producto.size(); i++) {
            if (verificadas.get(i).precioVerificado()) {
                return Optional.of(porNormalizada.get(producto.get(i)));
            }
        }
        return Optional.empty();
    }

    /** Palabras para buscar en la base: sin la condición ni la capacidad, que se revisan después. */
    static String palabrasParaLaBase(String termino) {
        String sinCapacidad = termino.replaceAll("(?i)\\b\\d{1,4}\\s?(gb|tb)\\b", " ");
        return Arrays.stream(sinCapacidad.split("\\s+"))
                .filter(p -> !p.isBlank() && CondicionProducto.detectar(p).isEmpty())
                .collect(Collectors.joining(" "));
    }

    private static Optional<OfertaNormalizada> normalizada(OfertaDestacada destacada) {
        Oferta oferta = destacada.oferta();
        try {
            return Optional.of(OfertaNormalizada.builder().codigoMarketplace(destacada.codigoMarketplace())
                    .idExterno(oferta.getIdExterno()).titulo(oferta.getTitulo()).precio(oferta.getPrecioActual())
                    .moneda(oferta.getMoneda()).costoEnvio(oferta.getCostoEnvio()).condicion(oferta.getCondicion())
                    .vendedor(oferta.getVendedor()).urlDestino(oferta.getUrlDestino()).build());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Aviso bajaDePrecio(OfertaDestacada destacada) {
        return new Aviso("BAJA_PRECIO", "¡Bajó de precio!",
                destacada.oferta().getTitulo() + " bajó " + pesos(destacada.baja()) + " y ahora cuesta "
                        + pesos(destacada.oferta().getPrecioActual())
                        + (destacada.oferta().getCostoEnvio() == null ? " más envío" : "")
                        + " en la misma tienda.",
                textoEnlace(destacada.oferta().getUrlDestino(), "Ver este producto"), null,
                destacada.oferta().getUrlDestino());
    }

    /** El botón dice a dónde lleva: si el enlace es una búsqueda de Google, lo dice así. */
    static String textoEnlace(String url, String siEsDeLaTienda) {
        if (!EnlaceCompra.esSeguro(url)) {
            return siEsDeLaTienda;
        }
        EnlaceCompra enlace = EnlaceCompra.de(url);
        return enlace.llevaALaTienda() ? siEsDeLaTienda + " en " + enlace.dominio() : enlace.textoBoton();
    }

    private static String pesos(BigDecimal valor) {
        return "$" + NumberFormat.getIntegerInstance(COLOMBIA).format(valor);
    }
}
