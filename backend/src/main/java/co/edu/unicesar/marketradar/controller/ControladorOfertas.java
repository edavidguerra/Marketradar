package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.dto.HistorialPrecioDto;
import co.edu.unicesar.marketradar.dto.OfertaDetalleDto;
import co.edu.unicesar.marketradar.service.ServicioOfertas;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Expone el detalle y el historial de precios de las ofertas.
 */
@RestController
@RequestMapping("/api/v1/ofertas")
public class ControladorOfertas {

    private final ServicioOfertas servicioOfertas;

    public ControladorOfertas(ServicioOfertas servicioOfertas) {
        this.servicioOfertas = servicioOfertas;
    }

    /**
     * Detalle de una oferta.
     *
     * @param id identificador de la oferta
     * @return detalle
     */
    @GetMapping("/{id}")
    public OfertaDetalleDto detalle(@PathVariable long id) {
        return OfertaDetalleDto.desde(servicioOfertas.obtener(id));
    }

    /**
     * Historial de precios de una oferta.
     *
     * @param id identificador de la oferta
     * @return registros del más antiguo al más reciente
     */
    @GetMapping("/{id}/historial")
    public List<HistorialPrecioDto> historial(@PathVariable long id) {
        return servicioOfertas.historial(id).stream().map(HistorialPrecioDto::desde).toList();
    }
}
