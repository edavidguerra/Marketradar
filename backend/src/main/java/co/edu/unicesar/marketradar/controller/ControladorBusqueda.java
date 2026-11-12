package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.service.mvp.PoliticaRecomendacion;
import co.edu.unicesar.marketradar.dto.RespuestaBusquedaDto;
import co.edu.unicesar.marketradar.dto.SugerenciasDto;
import co.edu.unicesar.marketradar.seguridad.UsuarioAutenticado;
import co.edu.unicesar.marketradar.service.ServicioBusqueda;
import co.edu.unicesar.marketradar.service.ServicioBusquedaFlexible;
import co.edu.unicesar.marketradar.service.ServicioResenas;
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
    private final ServicioResenas servicioResenas;
    private final PropiedadesMarketradar propiedades;

    public ControladorBusqueda(ServicioBusquedaFlexible servicioBusqueda, ServicioSugerencias servicioSugerencias,
                               ServicioResenas servicioResenas, PropiedadesMarketradar propiedades) {
        this.servicioBusqueda = servicioBusqueda;
        this.servicioSugerencias = servicioSugerencias;
        this.servicioResenas = servicioResenas;
        this.propiedades = propiedades;
    }

    /**
     * Cómo se elige la recomendación y se calcula el puntaje, con los pesos configurados. Es el mismo texto que usa el
     * asistente, para que nunca digan cosas distintas.
     *
     * @return reglas en frases cortas
     */
    @GetMapping("/como-recomendamos")
    public java.util.Map<String, java.util.List<String>> comoRecomendamos() {
        return java.util.Map.of("reglas", PoliticaRecomendacion.explicar(propiedades.mvp()));
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
     * @param original lo que escribió la persona antes de la corrección, para seguir exigiendo lo que pidió (por
     *                 ejemplo "nuevo") en las páginas siguientes y al filtrar; opcional
     * @param precioMinimo precio mínimo en pesos ("500.000" o "500000"), comparado con el precio más el envío informado; opcional
     * @param precioMaximo precio máximo en pesos, comparado igual; opcional
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
                                       @RequestParam(name = "precio_minimo", required = false) String precioMinimo,
                                       @RequestParam(name = "precio_maximo", required = false) String precioMaximo,
                                       @RequestParam(required = false) String original,
                                       @AuthenticationPrincipal UsuarioAutenticado usuario) {
        ConsultaBusqueda consulta = ConsultaBusqueda.crear(q, orden, separarCodigos(marketplaces), pagina, tamano)
                .conRangoDePrecio(ConsultaBusqueda.leerPrecio(precioMinimo), ConsultaBusqueda.leerPrecio(precioMaximo))
                // Al pasar de página o filtrar se sigue exigiendo lo que la persona escribió antes de la corrección.
                .conTextoOriginal(original);
        Long usuarioId = usuario == null ? null : usuario.getId();
        ServicioBusquedaFlexible.Resultado resultado = servicioBusqueda.buscar(consulta, usuarioId, exacto);
        // Así el botón "Ver reseñas" de cada producto puede pedirlas después a la tienda.
        servicioResenas.recordar(resultado.resultado());
        return RespuestaBusquedaDto.desde(resultado);
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
