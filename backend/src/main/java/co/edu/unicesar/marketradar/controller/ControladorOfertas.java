package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.domain.Idioma;
import co.edu.unicesar.marketradar.dto.HistorialPrecioDto;
import co.edu.unicesar.marketradar.dto.OfertaDetalleDto;
import co.edu.unicesar.marketradar.dto.ResenasDto;
import co.edu.unicesar.marketradar.dto.TraduccionDto;
import co.edu.unicesar.marketradar.seguridad.UsuarioAutenticado;
import co.edu.unicesar.marketradar.service.ServicioOfertas;
import co.edu.unicesar.marketradar.service.ServicioPerfil;
import co.edu.unicesar.marketradar.service.ServicioResenas;
import co.edu.unicesar.marketradar.service.ServicioTraduccion;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final ServicioTraduccion servicioTraduccion;
    private final ServicioPerfil servicioPerfil;

    public ControladorOfertas(ServicioOfertas servicioOfertas, ServicioResenas servicioResenas,
                              ServicioTraduccion servicioTraduccion, ServicioPerfil servicioPerfil) {
        this.servicioOfertas = servicioOfertas;
        this.servicioResenas = servicioResenas;
        this.servicioTraduccion = servicioTraduccion;
        this.servicioPerfil = servicioPerfil;
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
     * @param usuario quien las pide; se marcan las reseñas que están en otro idioma que el de su cuenta
     * @return calificación, opiniones por estrellas y algunas opiniones escritas
     */
    @GetMapping("/{id}/resenas")
    public ResenasDto resenas(@PathVariable long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Idioma idioma = idiomaDe(usuario);
        return servicioResenas.obtener(id)
                .map(resenas -> ResenasDto.desde(resenas, idioma, servicioTraduccion.estaHabilitado(),
                        servicioResenas.procedencia(id, resenas)))
                .orElseGet(() -> ResenasDto.sinResenas(idioma));
    }

    /**
     * Traduce una reseña al idioma que la persona eligió en su cuenta.
     *
     * @param id identificador de la oferta
     * @param indice posición de la reseña en la lista de {@code GET /api/v1/ofertas/{id}/resenas}
     * @param usuario quien la pide
     * @return título y texto traducidos
     */
    @PostMapping("/{id}/resenas/{indice}/traduccion")
    public TraduccionDto traducirResena(@PathVariable long id, @PathVariable int indice,
                                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return TraduccionDto.desde(servicioTraduccion.traducir(usuario.getId(), id, indice, idiomaDe(usuario)));
    }

    private Idioma idiomaDe(UsuarioAutenticado usuario) {
        return usuario == null ? Idioma.ES : servicioPerfil.obtener(usuario.getId()).getIdioma();
    }
}
