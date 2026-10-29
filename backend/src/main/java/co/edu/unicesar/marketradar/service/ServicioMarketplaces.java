package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.adapter.AdaptadorMarketplace;
import co.edu.unicesar.marketradar.dao.MarketplaceDao;
import co.edu.unicesar.marketradar.domain.Marketplace;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Informa qué marketplaces existen y si cada adaptador está disponible.
 */
@Service
public class ServicioMarketplaces {

    private final MarketplaceDao marketplaceDao;
    private final ServicioBusqueda servicioBusqueda;

    public ServicioMarketplaces(MarketplaceDao marketplaceDao, ServicioBusqueda servicioBusqueda) {
        this.marketplaceDao = marketplaceDao;
        this.servicioBusqueda = servicioBusqueda;
    }

    /**
     * Lista los marketplaces registrados en la base de datos.
     *
     * @return marketplaces ordenados por nombre
     */
    public List<Marketplace> listar() {
        return marketplaceDao.listarTodos();
    }

    /**
     * Indica, por código, si cada adaptador está habilitado.
     *
     * @return estado de cada adaptador
     */
    public Map<String, Boolean> estadoAdaptadores() {
        Map<String, Boolean> estado = new TreeMap<>();
        for (AdaptadorMarketplace adaptador : servicioBusqueda.adaptadoresRegistrados().values()) {
            estado.put(adaptador.codigo(), adaptador.estaHabilitado());
        }
        return estado;
    }
}
