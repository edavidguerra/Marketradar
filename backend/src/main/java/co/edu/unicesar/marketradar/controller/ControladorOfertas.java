package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.dto.HistorialPrecioDto;
import co.edu.unicesar.marketradar.dto.OfertaDetalleDto;
import co.edu.unicesar.marketradar.dto.ResenasDto;
import co.edu.unicesar.marketradar.service.ServicioOfertas;
import co.edu.unicesar.marketradar.service.ServicioResenas;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Expone el detalle, el historial de precios y las reseñas de las ofertas.
 */
@RestController
@RequestMapping("/api/v1/ofertas")
public class ControladorOfertas {

    private final ServicioOfertas servicioOfertas;
    private final ServicioResenas servicioResenas;

    public ControladorOfertas(ServicioOfertas servicioOfertas, ServicioResenas servicioResenas) {
        this.servicioOfertas = servicioOfertas;
        this.servicioResenas = servicioResenas;
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

    /**
     * Reseñas de compradores de una oferta que salió en una búsqueda reciente. Gasta una consulta de SerpApi la
     * primera vez; después quedan en caché un día.
     *
     * @param id identificador de la oferta
     * @return calificación, opiniones por estrellas y algunas opiniones escritas
     */
    @GetMapping("/{id}/resenas")
    public ResenasDto resenas(@PathVariable long id) {
        return servicioResenas.obtener(id).map(ResenasDto::desde).orElseGet(ResenasDto::sinResenas);
    }
}
