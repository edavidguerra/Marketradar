package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.BusquedaDao;
import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.domain.Aviso;
import co.edu.unicesar.marketradar.domain.Busqueda;
import co.edu.unicesar.marketradar.domain.OfertaDestacada;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
                "Escriba el producto que necesita y le mostramos en qué tienda le sale más barato, con envío incluido.",
                "Buscar un producto", null, null));
        return avisos;
    }

    private Optional<Aviso> seguimientoDeUltimaBusqueda(long usuarioId) {
        Optional<Busqueda> ultima = busquedaDao.listarPorUsuario(usuarioId, 1).stream().findFirst();
        if (ultima.isEmpty()) {
            return Optional.empty();
        }
        String termino = ultima.get().getTermino();
        return ofertaDao.buscarMasBarataPorTitulo(termino).map(destacada -> new Aviso("SEGUIMIENTO",
                "Lo que buscó: " + termino,
                destacada.oferta().getTitulo() + " está desde " + pesos(destacada.oferta().calcularPrecioTotal())
                        + ". Revise de nuevo antes de que cambie el precio.",
                "Ver ofertas de " + termino, termino, destacada.oferta().getUrlDestino()));
    }

    private Aviso bajaDePrecio(OfertaDestacada destacada) {
        return new Aviso("BAJA_PRECIO", "¡Bajó de precio!",
                destacada.oferta().getTitulo() + " bajó " + pesos(destacada.baja()) + " y ahora cuesta "
                        + pesos(destacada.oferta().getPrecioActual()) + ".",
                "Ver este producto", null, destacada.oferta().getUrlDestino());
    }

    private static String pesos(BigDecimal valor) {
        return "$" + NumberFormat.getIntegerInstance(COLOMBIA).format(valor);
    }
}
