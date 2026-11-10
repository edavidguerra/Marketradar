package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.BusquedaDao;
import co.edu.unicesar.marketradar.dao.Dao;
import co.edu.unicesar.marketradar.dao.MarketplaceDao;
import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.domain.Busqueda;
import co.edu.unicesar.marketradar.domain.ConsumoApi;
import co.edu.unicesar.marketradar.domain.Marketplace;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda en la base de datos las ofertas encontradas, su historial de precios (solo cuando el precio
 * cambia), las búsquedas realizadas y el consumo de cada API.
 */
@Service
public class ServicioPersistenciaOfertas {

    private final OfertaDao ofertaDao;
    private final MarketplaceDao marketplaceDao;
    private final BusquedaDao busquedaDao;
    private final Dao<ConsumoApi> consumoApiDao;
    private final Map<String, Short> idsMarketplace = new ConcurrentHashMap<>();

    public ServicioPersistenciaOfertas(OfertaDao ofertaDao,
                                       MarketplaceDao marketplaceDao, BusquedaDao busquedaDao,
                                       Dao<ConsumoApi> consumoApiDao) {
        this.ofertaDao = ofertaDao;
        this.marketplaceDao = marketplaceDao;
        this.busquedaDao = busquedaDao;
        this.consumoApiDao = consumoApiDao;
    }

    /**
     * Inserta o actualiza todas las ofertas de una búsqueda en una sola consulta y registra su precio en el
     * historial si es nuevo o cambió.
     *
     * @param ofertas ofertas normalizadas
     * @return identificadores asignados, por clave {@code codigoMarketplace|idExterno}
     */
    public Map<String, Long> guardarOfertas(List<OfertaNormalizada> ofertas) {
        List<Oferta> filas = new ArrayList<>();
        Map<String, String> clavePorFila = new HashMap<>();
        for (OfertaNormalizada normalizada : ofertas) {
            Short marketplaceId = idMarketplace(normalizada.getCodigoMarketplace());
            if (marketplaceId == null) {
                continue;
            }
            filas.add(aOferta(marketplaceId, normalizada));
            clavePorFila.putIfAbsent(OfertaDao.clave(marketplaceId, normalizada.getIdExterno()), normalizada.clave());
        }
        if (filas.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> idsPorClave = new HashMap<>();
        ofertaDao.guardarLote(filas).forEach((claveFila, id) -> {
            String clave = clavePorFila.get(claveFila);
            if (clave != null) {
                idsPorClave.put(clave, id);
            }
        });
        return idsPorClave;
    }

    /**
     * Registra una búsqueda realizada. Si la hizo una persona con sesión, queda en su historial.
     *
     * @param termino texto buscado
     * @param totalResultados cantidad de ofertas encontradas
     * @param duracionMs duración total en milisegundos
     * @param usuarioId persona con sesión, o {@code null}
     * @param urlImagen foto de un producto encontrado, para mostrarla en el historial; puede ser {@code null}
     */
    public void registrarBusqueda(String termino, int totalResultados, int duracionMs, Long usuarioId,
                                  String urlImagen) {
        Busqueda busqueda = new Busqueda(termino, totalResultados, duracionMs);
        busqueda.setUsuarioId(usuarioId);
        busqueda.setUrlImagen(urlImagen);
        busquedaDao.insertar(busqueda);
    }

    /**
     * Registra una llamada a la API de un marketplace.
     *
     * @param codigoMarketplace marketplace consultado
     * @param exitoso si respondió correctamente
     * @param duracionMs duración en milisegundos
     */
    public void registrarConsumoApi(String codigoMarketplace, boolean exitoso, int duracionMs) {
        Short marketplaceId = idMarketplace(codigoMarketplace);
        if (marketplaceId != null) {
            consumoApiDao.insertar(new ConsumoApi(marketplaceId, exitoso, null, duracionMs));
        }
    }

    private static Oferta aOferta(short marketplaceId, OfertaNormalizada normalizada) {
        Oferta oferta = new Oferta();
        oferta.setMarketplaceId(marketplaceId);
        oferta.setIdExterno(normalizada.getIdExterno());
        oferta.setTitulo(normalizada.getTitulo());
        oferta.setUrlDestino(normalizada.getUrlDestino());
        oferta.setUrlImagen(normalizada.getUrlImagen());
        oferta.setPrecioActual(normalizada.getPrecio());
        oferta.setMoneda(normalizada.getMoneda());
        oferta.setCostoEnvio(normalizada.getCostoEnvio());
        oferta.setCondicion(normalizada.getCondicion());
        oferta.setVendedor(normalizada.getVendedor());
        oferta.setConsultadoEn(normalizada.getConsultadoEn());
        oferta.setCalificacion(normalizada.getCalificacion());
        oferta.setTotalResenas(normalizada.getTotalResenas());
        return oferta;
    }

    /** Las tiendas casi nunca cambian, así que su identificador se guarda en memoria y no se consulta cada vez. */
    private Short idMarketplace(String codigo) {
        Short enMemoria = idsMarketplace.get(codigo);
        if (enMemoria != null) {
            return enMemoria;
        }
        Short id = marketplaceDao.buscarPorCodigo(codigo).map(Marketplace::getId).orElse(null);
        if (id != null) {
            idsMarketplace.put(codigo, id);
        }
        return id;
    }
}
