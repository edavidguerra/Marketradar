package co.edu.unicesar.marketradar.domain;

import co.edu.unicesar.marketradar.exception.ValidacionException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;

/**
 * Parámetros de una búsqueda ya validados. Una instancia solo puede existir si sus datos son correctos.
 */
public final class ConsultaBusqueda {

    public static final int LONGITUD_MINIMA = 2;
    public static final int LONGITUD_MAXIMA = 100;
    public static final int TAMANO_MAXIMO = 20;
    /** Cien mil millones de pesos: más que eso es un error al escribir. */
    public static final BigDecimal PRECIO_MAXIMO_PERMITIDO = new BigDecimal("100000000000");

    private final String termino;
    private final OrdenResultados orden;
    private final Set<String> marketplaces;
    private final int pagina;
    private final int tamano;
    private final BigDecimal precioMinimo;
    private final BigDecimal precioMaximo;
    /** Lo que escribió la persona, si se buscó otro texto (corregido); {@code null} si se buscó tal cual. */
    private final String textoOriginal;

    private ConsultaBusqueda(String termino, OrdenResultados orden, Set<String> marketplaces, int pagina, int tamano,
                             BigDecimal precioMinimo, BigDecimal precioMaximo) {
        this(termino, orden, marketplaces, pagina, tamano, precioMinimo, precioMaximo, null);
    }

    private ConsultaBusqueda(String termino, OrdenResultados orden, Set<String> marketplaces, int pagina, int tamano,
                             BigDecimal precioMinimo, BigDecimal precioMaximo, String textoOriginal) {
        this.textoOriginal = textoOriginal == null || textoOriginal.equals(termino) ? null : textoOriginal;
        this.termino = termino;
        this.orden = orden;
        this.marketplaces = marketplaces;
        this.pagina = pagina;
        this.tamano = tamano;
        this.precioMinimo = precioMinimo;
        this.precioMaximo = precioMaximo;
    }

    /**
     * Valida los parámetros y crea la consulta.
     *
     * @param termino texto buscado (2 a 100 caracteres, sin caracteres de control)
     * @param orden criterio de ordenamiento, {@code null} para el predeterminado
     * @param marketplaces códigos de marketplaces a consultar; vacío para todos los activos
     * @param pagina número de página, desde 1
     * @param tamano resultados por página, máximo 20
     * @return consulta válida
     * @throws ValidacionException si algún parámetro no es válido
     */
    public static ConsultaBusqueda crear(String termino, String orden, Set<String> marketplaces, int pagina, int tamano) {
        String limpio = termino == null ? "" : termino.strip();
        if (limpio.length() < LONGITUD_MINIMA || limpio.length() > LONGITUD_MAXIMA) {
            throw new ValidacionException("Escriba entre 2 y 100 letras para buscar un producto.");
        }
        if (limpio.chars().anyMatch(Character::isISOControl)) {
            throw new ValidacionException("La búsqueda tiene caracteres que no se pueden usar. Escríbala de nuevo.");
        }
        if (pagina < 1) {
            throw new ValidacionException("La página debe ser 1 o mayor.");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new ValidacionException("Se pueden mostrar entre 1 y 20 resultados por página.");
        }
        OrdenResultados ordenValido;
        try {
            ordenValido = OrdenResultados.desde(orden);
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("La forma de ordenar no es válida. Use precio más bajo, precio más alto o relevancia.");
        }
        Set<String> codigos = marketplaces == null ? Set.of() : Set.copyOf(marketplaces);
        return new ConsultaBusqueda(limpio, ordenValido, codigos, pagina, tamano, null, null);
    }

    /**
     * La misma consulta, mostrando solo lo que cuesta entre dos precios. Se compara el costo conocido: el precio más el envío cuando la tienda lo informa.
     *
     * @param minimo precio mínimo en pesos, o {@code null} para no poner piso
     * @param maximo precio máximo en pesos, o {@code null} para no poner techo
     * @return consulta nueva con el rango de precio
     * @throws ValidacionException si un precio es negativo, demasiado alto o el mínimo es mayor que el máximo
     */
    public ConsultaBusqueda conRangoDePrecio(BigDecimal minimo, BigDecimal maximo) {
        validarPrecio(minimo, "mínimo");
        validarPrecio(maximo, "máximo");
        if (minimo != null && maximo != null && minimo.compareTo(maximo) > 0) {
            throw new ValidacionException("El precio mínimo es mayor que el máximo. Revise los dos valores.");
        }
        return new ConsultaBusqueda(termino, orden, marketplaces, pagina, tamano, minimo, maximo, textoOriginal);
    }

    private static void validarPrecio(BigDecimal precio, String cual) {
        if (precio == null) {
            return;
        }
        if (precio.signum() < 0) {
            throw new ValidacionException("El precio " + cual + " no puede ser negativo.");
        }
        if (precio.compareTo(PRECIO_MAXIMO_PERMITIDO) > 0) {
            throw new ValidacionException("El precio " + cual + " es demasiado alto. Revíselo.");
        }
    }

    /**
     * Lee un precio escrito por la persona: acepta "1.500.000", "1500000", "1,500,000" o "$ 1.500.000".
     *
     * @param texto lo que escribió, o {@code null}
     * @return el precio en pesos, sin centavos, o {@code null} si lo dejó vacío
     * @throws ValidacionException si trae algo que no son números
     */
    public static BigDecimal leerPrecio(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        // Los centavos (",50") no cuentan; los puntos y comas de miles se quitan.
        String limpio = texto.strip().replaceFirst("^\\$\\s*", "").replaceFirst(",\\d{1,2}$", "")
                .replaceAll("[.,\\s]", "");
        if (limpio.length() > 15 || !limpio.matches("\\d+")) {
            throw new ValidacionException("Escriba el precio solo con números, por ejemplo 500.000.");
        }
        return new BigDecimal(limpio);
    }

    /** @return la misma consulta sin rango de precio, para saber cuánto hay en total */
    public ConsultaBusqueda sinRangoDePrecio() {
        return tieneRangoDePrecio() ? new ConsultaBusqueda(termino, orden, marketplaces, pagina, tamano, null, null,
                textoOriginal)
                : this;
    }

    /** @return {@code true} si la persona puso un precio mínimo o máximo */
    public boolean tieneRangoDePrecio() {
        return precioMinimo != null || precioMaximo != null;
    }

    /**
     * @param precioTotal precio con envío
     * @return {@code true} si está dentro del rango, contando los extremos
     */
    public boolean precioEnRango(BigDecimal precioTotal) {
        return (precioMinimo == null || precioTotal.compareTo(precioMinimo) >= 0)
                && (precioMaximo == null || precioTotal.compareTo(precioMaximo) <= 0);
    }

    /** @return precio mínimo, o {@code null} */
    public BigDecimal getPrecioMinimo() {
        return precioMinimo;
    }

    /** @return precio máximo, o {@code null} */
    public BigDecimal getPrecioMaximo() {
        return precioMaximo;
    }

    public String getTermino() {
        return termino;
    }

    public OrdenResultados getOrden() {
        return orden;
    }

    public Set<String> getMarketplaces() {
        return Collections.unmodifiableSet(marketplaces);
    }

    public int getPagina() {
        return pagina;
    }

    public int getTamano() {
        return tamano;
    }

    /**
     * La misma consulta con otro texto, por ejemplo con las palabras corregidas.
     *
     * @param otroTermino texto nuevo
     * @return consulta nueva con el mismo orden, tiendas, página, tamaño y rango de precio
     */
    public ConsultaBusqueda conTermino(String otroTermino) {
        return crear(otroTermino, orden.getValor(), marketplaces, pagina, tamano)
                .conRangoDePrecio(precioMinimo, precioMaximo)
                .conTextoOriginal(getTextoOriginal());
    }

    /**
     * La misma consulta, recordando lo que escribió la persona antes de corregirlo. Así lo que pidió ("nuevo",
     * "128 GB") se sigue exigiendo aunque la búsqueda corregida lo haya quitado, también al pasar de página o
     * filtrar por precio.
     *
     * @param original lo que escribió la persona; si está vacío, es muy largo o tiene caracteres de control, se ignora
     * @return consulta con el texto original
     */
    public ConsultaBusqueda conTextoOriginal(String original) {
        String limpio = original == null ? null : original.strip();
        if (limpio == null || limpio.length() < LONGITUD_MINIMA || limpio.length() > LONGITUD_MAXIMA
                || limpio.chars().anyMatch(Character::isISOControl)) {
            return this;
        }
        return new ConsultaBusqueda(termino, orden, marketplaces, pagina, tamano, precioMinimo, precioMaximo, limpio);
    }

    /** @return lo que escribió la persona (el mismo término si no se corrigió) */
    public String getTextoOriginal() {
        return textoOriginal == null ? termino : textoOriginal;
    }

    /**
     * Lo que la persona exigió: modelo, versión, capacidad y condición, sacados de lo que escribió y de la búsqueda
     * corregida.
     *
     * @return requisitos
     */
    public RequisitosBusqueda requisitos() {
        return RequisitosBusqueda.combinar(getTextoOriginal(), termino);
    }

    /**
     * Clave de caché: dos consultas con el mismo término y marketplaces comparten resultados. El rango de precio no
     * cuenta, porque se aplica después: cambiarlo no vuelve a consultar a las tiendas.
     *
     * @return clave normalizada
     */
    public String claveCache() {
        return termino.toLowerCase() + "|" + String.join(",", marketplaces.stream().sorted().toList());
    }
}
