package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.dto.RespuestaBusquedaDto;
import co.edu.unicesar.marketradar.dto.SugerenciasDto;
import co.edu.unicesar.marketradar.seguridad.UsuarioAutenticado;
import co.edu.unicesar.marketradar.service.ServicioBusqueda;
import co.edu.unicesar.marketradar.service.ServicioBusquedaFlexible;
import co.edu.unicesar.marketradar.service.ServicioSugerencias;
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

    private final ServicioBusquedaFlexible servicioBusqueda;
    private final ServicioSugerencias servicioSugerencias;

    public ControladorBusqueda(ServicioBusquedaFlexible servicioBusqueda, ServicioSugerencias servicioSugerencias) {
        this.servicioBusqueda = servicioBusqueda;
        this.servicioSugerencias = servicioSugerencias;
    }

    /**
     * Busca un producto en todos los marketplaces activos, entendiendo errores al escribir.
     *
     * @param q texto a buscar (obligatorio, 2 a 100 caracteres)
     * @param orden {@code precio_asc} (por defecto), {@code precio_desc} o {@code relevancia}
     * @param marketplaces códigos separados por coma; vacío para todos
     * @param pagina número de página desde 1
     * @param tamano resultados por página, máximo 20
     * @param exacto {@code true} para buscar el texto tal como se escribió, sin corregir errores
     * @param usuario persona con sesión, para guardar la búsqueda en su historial
     * @return resultados ordenados, advertencias y, si se corrigió, lo que la persona escribió
     */
    @GetMapping
    public RespuestaBusquedaDto buscar(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) String orden,
                                       @RequestParam(required = false) String marketplaces,
                                       @RequestParam(defaultValue = "1") int pagina,
                                       @RequestParam(defaultValue = "10") int tamano,
                                       @RequestParam(defaultValue = "false") boolean exacto,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        ConsultaBusqueda consulta = ConsultaBusqueda.crear(q, orden, separarCodigos(marketplaces), pagina, tamano);
        Long usuarioId = usuario == null ? null : usuario.getId();
        return RespuestaBusquedaDto.desde(servicioBusqueda.buscar(consulta, usuarioId, exacto));
    }

    /**
     * Sugiere productos mientras la persona escribe. No consulta las tiendas, así que no gasta búsquedas.
     *
     * @param q lo escrito hasta ahora
     * @return hasta 6 sugerencias
     */
    @GetMapping("/sugerencias")
    public SugerenciasDto sugerencias(@RequestParam(required = false) String q) {
        return new SugerenciasDto(servicioSugerencias.sugerir(q));
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
