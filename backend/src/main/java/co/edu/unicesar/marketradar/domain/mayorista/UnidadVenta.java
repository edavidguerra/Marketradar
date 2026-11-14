package co.edu.unicesar.marketradar.domain.mayorista;

import java.util.Locale;
import java.util.Map;

/**
 * Unidad en que vende un proveedor al por mayor. Piezas, pares y paquetes son unidades distintas: nunca se convierte una
 * en otra sin una equivalencia que informe el proveedor (por ejemplo "10 piezas por paquete").
 */
public enum UnidadVenta {

    PIEZA("pieza", "piezas"),
    PAR("par", "pares"),
    PAQUETE("paquete", "paquetes"),
    JUEGO("juego", "juegos"),
    CAJA("caja", "cajas"),
    BOLSA("bolsa", "bolsas"),
    ROLLO("rollo", "rollos"),
    METRO("metro", "metros"),
    KILOGRAMO("kilogramo", "kilogramos"),
    TONELADA("tonelada", "toneladas"),
    DESCONOCIDA("unidad sin identificar", "unidades sin identificar");

    private final String singular;
    private final String plural;

    UnidadVenta(String singular, String plural) {
        this.singular = singular;
        this.plural = plural;
    }

    private static final Map<String, UnidadVenta> PALABRAS = Map.ofEntries(
            Map.entry("piece", PIEZA), Map.entry("pieces", PIEZA), Map.entry("pc", PIEZA), Map.entry("pcs", PIEZA),
            Map.entry("unit", PIEZA), Map.entry("units", PIEZA), Map.entry("pieza", PIEZA), Map.entry("piezas", PIEZA),
            Map.entry("unidad", PIEZA), Map.entry("unidades", PIEZA),
            Map.entry("pair", PAR), Map.entry("pairs", PAR), Map.entry("par", PAR), Map.entry("pares", PAR),
            Map.entry("pack", PAQUETE), Map.entry("packs", PAQUETE), Map.entry("package", PAQUETE),
            Map.entry("packages", PAQUETE), Map.entry("packet", PAQUETE), Map.entry("packets", PAQUETE),
            Map.entry("paquete", PAQUETE), Map.entry("paquetes", PAQUETE),
            Map.entry("set", JUEGO), Map.entry("sets", JUEGO), Map.entry("juego", JUEGO), Map.entry("juegos", JUEGO),
            Map.entry("box", CAJA), Map.entry("boxes", CAJA), Map.entry("carton", CAJA), Map.entry("cartons", CAJA),
            Map.entry("caja", CAJA), Map.entry("cajas", CAJA),
            Map.entry("bag", BOLSA), Map.entry("bags", BOLSA), Map.entry("bolsa", BOLSA), Map.entry("bolsas", BOLSA),
            Map.entry("roll", ROLLO), Map.entry("rolls", ROLLO), Map.entry("rollo", ROLLO), Map.entry("rollos", ROLLO),
            Map.entry("meter", METRO), Map.entry("meters", METRO), Map.entry("metre", METRO), Map.entry("metres", METRO),
            Map.entry("metro", METRO), Map.entry("metros", METRO),
            Map.entry("kilogram", KILOGRAMO), Map.entry("kilograms", KILOGRAMO), Map.entry("kg", KILOGRAMO),
            Map.entry("kilogramo", KILOGRAMO), Map.entry("kilogramos", KILOGRAMO),
            Map.entry("ton", TONELADA), Map.entry("tons", TONELADA), Map.entry("tonne", TONELADA),
            Map.entry("tonnes", TONELADA), Map.entry("metric ton", TONELADA), Map.entry("tonelada", TONELADA),
            Map.entry("toneladas", TONELADA));

    /**
     * Lee la unidad como la escribe el proveedor ("pieces", "Pair", "set").
     *
     * @param texto unidad; puede venir con mayúsculas o con un número delante
     * @return unidad, o {@link #DESCONOCIDA}
     */
    public static UnidadVenta leer(String texto) {
        if (texto == null) {
            return DESCONOCIDA;
        }
        String limpio = texto.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").strip();
        UnidadVenta completa = PALABRAS.get(limpio);
        if (completa != null) {
            return completa;
        }
        for (String palabra : limpio.split(" ")) {
            UnidadVenta unidad = PALABRAS.get(palabra);
            if (unidad != null) {
                return unidad;
            }
        }
        return DESCONOCIDA;
    }

    /** @return nombre en singular */
    public String singular() {
        return singular;
    }

    /**
     * @param cantidad cantidad
     * @return "1 par", "20 piezas"
     */
    public String con(long cantidad) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }

    /** @return nombre en plural */
    public String plural() {
        return plural;
    }
}
