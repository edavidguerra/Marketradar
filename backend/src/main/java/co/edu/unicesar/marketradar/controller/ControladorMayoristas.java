package co.edu.unicesar.marketradar.controller;

import co.edu.unicesar.marketradar.adapter.ConversorMoneda;
import co.edu.unicesar.marketradar.config.PropiedadesMayoristas;
import co.edu.unicesar.marketradar.domain.mayorista.UnidadVenta;
import co.edu.unicesar.marketradar.dto.MayoristaDto;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import co.edu.unicesar.marketradar.service.mayorista.ServicioMayoristas;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

/**
 * Apartado "Al por mayor": búsqueda en Alibaba con precio por cantidad. Necesita sesión, porque cada consulta gasta un
 * crédito de API.SH. La clave de API.SH nunca sale del servidor.
 */
@RestController
@RequestMapping("/api/v1/mayoristas")
public class ControladorMayoristas {

    private final ServicioMayoristas servicio;
    private final PropiedadesMayoristas propiedades;
    private final ConversorMoneda conversor;

    public ControladorMayoristas(ServicioMayoristas servicio, PropiedadesMayoristas propiedades,
                                 ConversorMoneda conversor) {
        this.servicio = servicio;
        this.propiedades = propiedades;
        this.conversor = conversor;
    }

    /** @return si el apartado está configurado, unidades y límites */
    @GetMapping("/estado")
    public Map<String, Object> estado() {
        return Map.of("configurado", servicio.estaConfigurado(), "paginas_maximas", propiedades.paginasMaximas(),
                "unidades", MayoristaDto.UnidadDto.todas(), "nota", ServicioMayoristas.NOTA_SUBTOTAL);
    }

    /**
     * @param q producto
     * @param cantidad cantidad deseada
     * @param unidad unidad (PIEZA, PAR, PAQUETE...)
     * @param pagina página
     * @return resultados
     */
    @GetMapping("/busqueda")
    public MayoristaDto.BusquedaDto buscar(@RequestParam("q") String q,
                                           @RequestParam("cantidad") String cantidad,
                                           @RequestParam(value = "unidad", defaultValue = "PIEZA") String unidad,
                                           @RequestParam(value = "pagina", defaultValue = "1") String pagina) {
        return MayoristaDto.BusquedaDto.desde(servicio.buscar(q, numero(cantidad, "cantidad"), unidad(unidad),
                (int) numero(pagina, "página")), conversor.getMonedaDestino());
    }

    /**
     * @param ref enlace de Alibaba o número del producto
     * @param cantidad cantidad deseada
     * @param unidad unidad
     * @param variante variante elegida
     * @return detalle con el cálculo
     */
    @GetMapping("/producto")
    public MayoristaDto.OfertaDto producto(@RequestParam("ref") String ref,
                                           @RequestParam("cantidad") String cantidad,
                                           @RequestParam(value = "unidad", defaultValue = "PIEZA") String unidad,
                                           @RequestParam(value = "variante", required = false) String variante) {
        return MayoristaDto.OfertaDto.desde(servicio.detalle(ref, numero(cantidad, "cantidad"), unidad(unidad),
                variante), conversor.getMonedaDestino());
    }

    private static long numero(String texto, String campo) {
        String limpio = texto == null ? "" : texto.strip();
        if (!limpio.matches("\\d{1,9}")) {
            throw new ValidacionException("La " + campo + " debe ser un número entero.");
        }
        return Long.parseLong(limpio);
    }

    private static UnidadVenta unidad(String texto) {
        try {
            UnidadVenta unidad = UnidadVenta.valueOf(texto.strip().toUpperCase(Locale.ROOT));
            if (unidad == UnidadVenta.DESCONOCIDA) {
                throw new IllegalArgumentException();
            }
            return unidad;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidacionException("Elija la unidad: piezas, pares, paquetes...");
        }
    }
}
