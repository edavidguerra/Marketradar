package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.dto.MarketplaceDto;
import co.edu.unicesar.marketradar.dto.SaludDto;
import co.edu.unicesar.marketradar.service.ServicioAsistente;
import co.edu.unicesar.marketradar.service.ServicioMarketplaces;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Expone la lista de marketplaces y el estado del servicio.
 */
@RestController
@RequestMapping("/api/v1")
public class ControladorMarketplaces {

    private final ServicioMarketplaces servicioMarketplaces;
    private final ServicioAsistente servicioAsistente;

    public ControladorMarketplaces(ServicioMarketplaces servicioMarketplaces, ServicioAsistente servicioAsistente) {
        this.servicioMarketplaces = servicioMarketplaces;
        this.servicioAsistente = servicioAsistente;
    }

    /**
     * Lista de marketplaces y su estado.
     *
     * @return marketplaces registrados
     */
    @GetMapping("/marketplaces")
    public List<MarketplaceDto> marketplaces() {
        Map<String, Boolean> estado = servicioMarketplaces.estadoAdaptadores();
        return servicioMarketplaces.listar().stream()
                .map(marketplace -> new MarketplaceDto(marketplace.getCodigo(), marketplace.getNombre(),
                        marketplace.getSitioWeb(), marketplace.isActivo(),
                        estado.getOrDefault(marketplace.getCodigo(), false)))
                .toList();
    }

    /**
     * Estado del servicio, de cada adaptador y del asistente.
     *
     * @return estado
     */
    @GetMapping("/salud")
    public SaludDto salud() {
        return new SaludDto("OK", servicioMarketplaces.estadoAdaptadores(), servicioAsistente.estaDisponible());
    }
}
