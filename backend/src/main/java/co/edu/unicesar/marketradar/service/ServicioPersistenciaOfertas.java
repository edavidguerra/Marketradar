package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.Dao;
import co.edu.unicesar.marketradar.dao.HistorialPrecioDao;
import co.edu.unicesar.marketradar.dao.MarketplaceDao;
import co.edu.unicesar.marketradar.dao.OfertaDao;
import co.edu.unicesar.marketradar.domain.Busqueda;
import co.edu.unicesar.marketradar.domain.ConsumoApi;
import co.edu.unicesar.marketradar.domain.HistorialPrecio;
import co.edu.unicesar.marketradar.domain.Marketplace;
import co.edu.unicesar.marketradar.domain.Oferta;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Guarda en la base de datos las ofertas encontradas, su historial de precios (solo cuando el precio
 * cambia), las búsquedas realizadas y el consumo de cada API.
 */
@Service
public class ServicioPersistenciaOfertas {

    private final OfertaDao ofertaDao;
    private final HistorialPrecioDao historialDao;
    private final MarketplaceDao marketplaceDao;
    private final Dao<Busqueda> busquedaDao;
    private final Dao<ConsumoApi> consumoApiDao;

    public ServicioPersistenciaOfertas(OfertaDao ofertaDao, HistorialPrecioDao historialDao,
                                       MarketplaceDao marketplaceDao, Dao<Busqueda> busquedaDao,
                                       Dao<ConsumoApi> consumoApiDao) {
        this.ofertaDao = ofertaDao;
        this.historialDao = historialDao;
        this.marketplaceDao = marketplaceDao;
        this.busquedaDao = busquedaDao;
        this.consumoApiDao = consumoApiDao;
    }

    /**
     * Inserta o actualiza cada oferta y registra su precio en el historial si cambió.
     *
     * @param ofertas ofertas normalizadas
     * @return identificadores asignados, por clave {@code codigoMarketplace|idExterno}
     */
    public Map<String, Long> guardarOfertas(List<OfertaNormalizada> ofertas) {
        Map<String, Long> idsPorClave = new HashMap<>();
        Map<String, Short> idsMarketplace = new HashMap<>();
        for (OfertaNormalizada normalizada : ofertas) {
            Short marketplaceId = idsMarketplace.computeIfAbsent(normalizada.getCodigoMarketplace(), this::idMarketplace);
            if (marketplaceId == null) {
                continue;
            }
            Oferta oferta = guardarOferta(marketplaceId, normalizada);
            idsPorClave.put(normalizada.clave(), oferta.getId());
        }
        return idsPorClave;
    }

    /**
     * Registra una búsqueda realizada (sin datos personales).
     *
     * @param termino texto buscado
     * @param totalResultados cantidad de ofertas encontradas
     * @param duracionMs duración total en milisegundos
     */
    public void registrarBusqueda(String termino, int totalResultados, int duracionMs) {
        busquedaDao.insertar(new Busqueda(termino, totalResultados, duracionMs));
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

    private Oferta guardarOferta(short marketplaceId, OfertaNormalizada normalizada) {
        Optional<Oferta> existente = ofertaDao.buscarPorMarketplaceYIdExterno(marketplaceId, normalizada.getIdExterno());
        Oferta oferta = existente.orElseGet(Oferta::new);
        boolean registrarHistorial = existente.isEmpty() || oferta.cambioPrecio(normalizada.getPrecio());

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

        if (existente.isPresent()) {
            ofertaDao.actualizar(oferta);
        } else {
            ofertaDao.insertar(oferta);
        }
        if (registrarHistorial) {
            historialDao.insertar(new HistorialPrecio(oferta.getId(), normalizada.getPrecio(), normalizada.getMoneda()));
        }
        return oferta;
    }

    private Short idMarketplace(String codigo) {
        return marketplaceDao.buscarPorCodigo(codigo).map(Marketplace::getId).orElse(null);
    }
}
