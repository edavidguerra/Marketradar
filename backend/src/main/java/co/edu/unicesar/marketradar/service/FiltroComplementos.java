package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.OfertaNormalizada;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deja primero el producto que la persona buscó y no lo que se vende para él.
 *
 * <p>Quien busca "playstation 5" quiere la consola, pero Google Shopping también trae juegos, paquetes de la
 * tienda digital, controles y fundas, que son mucho más baratos y por eso quedaban de primeros. Un resultado es un
 * <em>complemento</em> cuando su título empieza por palabras como "juego", "funda" o "cargador", trae "DLC" o
 * "expansión", dice "para playstation", o viene de una tienda de juegos digitales, y la persona no escribió esas
 * palabras. "Juego de ollas" o "set de sábanas" no cuentan: ahí "juego" quiere decir varias piezas.</p>
 *
 * <p>Si quedan al menos {@value #MINIMO_PRINCIPALES} resultados que no son complementos, se muestran solo esos.
 * Si no, se muestran todos, para no dejar a la persona sin nada.</p>
 *
 * <p>Lo que no trae el producto, como la caja vacía de un iPhone ("Caja iPhone 17 Pro", "Solo caja", "Empty box"),
 * se quita siempre con {@link #quitarSinProducto}: mostrarla, aunque sea lo único, confundiría a la persona.</p>
 */
final class FiltroComplementos {

    static final int MINIMO_PRINCIPALES = 3;

    /** Al comienzo del título indican que no es el producto sino algo para él ("Funda para...", "Juego PS5..."). */
    private static final Set<String> AL_COMIENZO = Set.of(
            "juego", "juegos", "videojuego", "videojuegos", "pack", "set", "funda", "forro", "estuche", "carcasa",
            "protector", "mica", "cable", "cargador", "repuesto", "repuestos", "soporte", "adaptador", "control",
            "membresia", "suscripcion", "tarjeta", "gift", "giftcard", "sticker", "calcomania", "vinilo", "skin",
            "case", "cover", "vidrio", "lamina", "pelicula", "correa", "manilla");

    /** Al comienzo del título y con el nombre del producto buscado: es solo la caja ("Caja iPhone 17 Pro"). */
    private static final Set<String> EMPAQUE = Set.of("caja", "cajas", "empaque", "empaques", "box");

    /** En cualquier parte del título: la publicación no trae el producto. */
    private static final List<String> SIN_PRODUCTO = List.of(
            "caja vacia", "cajas vacias", "solo caja", "solo la caja", "solo empaque", "solo el empaque",
            "sin equipo", "sin telefono", "sin celular", "sin consola", "no incluye equipo", "no incluye telefono",
            "empty box", "box only", "only box", "only the box", "solo para exhibicion", "dummy", "replica de exhibicion");

    /** En cualquier parte del título: solo existen en contenido digital para juegos. */
    private static final Set<String> EN_CUALQUIER_PARTE = Set.of("dlc", "expansion", "addon", "skins", "season");

    /** "Juego de ollas", "set de cuchillos": varias piezas del mismo producto, no un complemento. */
    private static final Set<String> VARIAS_PIEZAS = Set.of("juego", "juegos", "set", "pack");

    /** Tiendas que venden juegos y contenido digital, no aparatos. */
    private static final List<String> TIENDAS_DIGITALES = List.of(
            "playstation store", "microsoft store", "xbox store", "nintendo eshop", "eshop", "steam");

    private FiltroComplementos() {
    }

    static List<OfertaNormalizada> filtrar(String termino, List<OfertaNormalizada> ofertas) {
        String buscado = ServicioSugerencias.normalizar(termino);
        if (buscado.isEmpty() || ofertas.size() <= MINIMO_PRINCIPALES) {
            return ofertas;
        }
        Set<String> palabrasBuscadas = Set.of(buscado.split(" "));
        List<OfertaNormalizada> principales = new ArrayList<>();
        for (OfertaNormalizada oferta : ofertas) {
            if (!esComplemento(oferta, buscado, palabrasBuscadas)) {
                principales.add(oferta);
            }
        }
        return principales.size() >= MINIMO_PRINCIPALES ? principales : ofertas;
    }

    /**
     * Quita lo que no trae el producto buscado, como cajas vacías. Se aplica siempre, aunque quede la lista vacía.
     *
     * @param termino lo que buscó la persona
     * @param ofertas resultados de las tiendas
     * @return los mismos resultados, sin cajas ni empaques
     */
    static List<OfertaNormalizada> quitarSinProducto(String termino, List<OfertaNormalizada> ofertas) {
        String buscado = ServicioSugerencias.normalizar(termino);
        if (buscado.isEmpty()) {
            return ofertas;
        }
        Set<String> palabrasBuscadas = Set.of(buscado.split(" "));
        return ofertas.stream().filter(oferta -> !esSinProducto(oferta, buscado, palabrasBuscadas)).toList();
    }

    static boolean esSinProducto(OfertaNormalizada oferta, String buscado, Set<String> palabrasBuscadas) {
        String titulo = ServicioSugerencias.normalizar(oferta.getTitulo());
        String conEspacios = " " + titulo + " ";
        boolean pideEmpaque = palabrasBuscadas.stream().anyMatch(EMPAQUE::contains);
        if (!pideEmpaque && SIN_PRODUCTO.stream().anyMatch(frase -> conEspacios.contains(" " + frase + " "))) {
            return true;
        }
        String[] palabras = titulo.split(" ");
        String principal = buscado.split(" ")[0];
        for (int i = 0; i < Math.min(2, palabras.length); i++) {
            if (EMPAQUE.contains(palabras[i]) && !pideEmpaque && conEspacios.contains(" " + principal + " ")) {
                return true;
            }
        }
        return false;
    }

    static boolean esComplemento(OfertaNormalizada oferta, String buscado, Set<String> palabrasBuscadas) {
        String titulo = ServicioSugerencias.normalizar(oferta.getTitulo());
        String[] palabras = titulo.split(" ");
        for (int i = 0; i < Math.min(2, palabras.length); i++) {
            String palabra = palabras[i];
            boolean variasPiezas = VARIAS_PIEZAS.contains(palabra) && i + 1 < palabras.length
                    && "de".equals(palabras[i + 1]);
            if (AL_COMIENZO.contains(palabra) && !variasPiezas && !palabrasBuscadas.contains(palabra)) {
                return true;
            }
        }
        for (String palabra : palabras) {
            if (EN_CUALQUIER_PARTE.contains(palabra) && !palabrasBuscadas.contains(palabra)) {
                return true;
            }
        }
        // "Control para PlayStation 5", "Funda para iPhone 15": es para el producto, no el producto.
        String primera = buscado.split(" ")[0];
        if (!palabrasBuscadas.contains("para") && (titulo.contains("para " + primera)
                || titulo.contains("compatible con " + primera))) {
            return true;
        }
        String tienda = ServicioSugerencias.normalizar(oferta.getVendedor());
        boolean pideDigital = palabrasBuscadas.contains("juego") || palabrasBuscadas.contains("digital");
        return !pideDigital && TIENDAS_DIGITALES.stream().anyMatch(tienda::contains);
    }
}
