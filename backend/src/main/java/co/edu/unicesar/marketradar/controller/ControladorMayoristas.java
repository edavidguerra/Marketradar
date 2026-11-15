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
 * Apartado "Emprender": búsqueda al por mayor en Alibaba con precio por cantidad. Necesita sesión, porque cada consulta gasta un
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
                                           @RequestParam(value = "pagina", defaultValue = "1") String pagina,
                                           @RequestParam(value = "variantes", required = false) String variantes) {
        return MayoristaDto.BusquedaDto.desde(servicio.buscar(q, cantidad(cantidad), unidad(unidad),
                pagina(pagina), variantes(variantes)), conversor.getMonedaDestino());
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
        return MayoristaDto.OfertaDto.desde(servicio.detalle(ref, cantidad(cantidad), unidad(unidad),
                variante), conversor.getMonedaDestino());
    }

    /** Formato aceptado para la cantidad: solo cifras, o miles separados con punto en grupos completos de tres. */
    private static final java.util.regex.Pattern CANTIDAD = java.util.regex.Pattern.compile("\\d{1,8}|\\d{1,3}(\\.\\d{3}){1,2}");

    /**
     * Lee la cantidad con las mismas reglas que la página (frontend/js/usuario/mayoristas.js): un entero entre 1 y
     * 10.000.000, sin decimales ni signos. Se aceptan los miles con punto solo en grupos completos ("10.000"); "1.5",
     * "1,5" o "1 5" se rechazan en vez de leerse como 15.
     *
     * @param texto lo que escribió la persona
     * @return cantidad
     */
    static long cantidad(String texto) {
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.isEmpty()) {
            throw new ValidacionException("Escriba la cantidad que quiere comprar.");
        }
        if (limpio.matches("-\\s*[\\d.,]+")) {
            throw new ValidacionException("La cantidad debe estar entre 1 y 10.000.000.");
        }
        if (!CANTIDAD.matcher(limpio).matches()) {
            throw new ValidacionException("Escriba la cantidad con números enteros, sin decimales (por ejemplo 500 o "
                    + "10.000). Los miles van con punto.");
        }
        long valor = Long.parseLong(limpio.replace(".", ""));
        if (valor < 1 || valor > 10_000_000L) {
            throw new ValidacionException("La cantidad debe estar entre 1 y 10.000.000.");
        }
        return valor;
    }

    private static int pagina(String texto) {
        String limpio = texto == null ? "" : texto.strip();
        if (!limpio.matches("\\d{1,2}")) {
            throw new ValidacionException("La página debe ser un número entero.");
        }
        return Integer.parseInt(limpio);
    }

    /** "id:variante,id:variante" → mapa. Como mucho 20 parejas; lo que no tenga ese formato se ignora. */
    static Map<String, String> variantes(String texto) {
        Map<String, String> elegidas = new java.util.LinkedHashMap<>();
        if (texto == null || texto.isBlank() || texto.length() > 1600) {
            return elegidas;
        }
        for (String par : texto.split(",")) {
            String[] partes = par.strip().split(":", 2);
            if (partes.length == 2 && partes[0].matches("\\d{6,20}") && !partes[1].isBlank()
                    && partes[1].length() <= 60 && elegidas.size() < 20) {
                elegidas.put(partes[0], partes[1].strip());
            }
        }
        return elegidas;
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
