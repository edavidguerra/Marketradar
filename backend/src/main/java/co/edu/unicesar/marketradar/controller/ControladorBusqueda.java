package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.dto.RespuestaBusquedaDto;
import co.edu.unicesar.marketradar.seguridad.UsuarioAutenticado;
import co.edu.unicesar.marketradar.service.ServicioBusqueda;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recibe las búsquedas HTTP y delega en {@link ServicioBusqueda} sin lógica de negocio (GRASP Controller).
 */
@RestController
@RequestMapping("/api/v1/busqueda")
public class ControladorBusqueda {

    private final ServicioBusqueda servicioBusqueda;

    public ControladorBusqueda(ServicioBusqueda servicioBusqueda) {
        this.servicioBusqueda = servicioBusqueda;
    }

    /**
     * Busca un producto en todos los marketplaces activos.
     *
     * @param q texto a buscar (obligatorio, 2 a 100 caracteres)
     * @param orden {@code precio_asc} (por defecto), {@code precio_desc} o {@code relevancia}
     * @param marketplaces códigos separados por coma; vacío para todos
     * @param pagina número de página desde 1
     * @param tamano resultados por página, máximo 20
     * @param usuario persona con sesión, para guardar la búsqueda en su historial
     * @return resultados ordenados y advertencias
     */
    @GetMapping
    public RespuestaBusquedaDto buscar(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String orden,
                                       @RequestParam(required = false) String marketplaces,
                                       @RequestParam(defaultValue = "1") int pagina,
                                       @RequestParam(defaultValue = "10") int tamano,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        ConsultaBusqueda consulta = ConsultaBusqueda.crear(q, orden, separarCodigos(marketplaces), pagina, tamano);
        Long usuarioId = usuario == null ? null : usuario.getId();
        return RespuestaBusquedaDto.desde(consulta, servicioBusqueda.buscar(consulta, usuarioId));
    }

    private static Set<String> separarCodigos(String marketplaces) {
        if (marketplaces == null || marketplaces.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(marketplaces.split(","))
                .map(String::strip)
                .filter(codigo -> !codigo.isEmpty())
                .collect(Collectors.toSet());
    }
}
