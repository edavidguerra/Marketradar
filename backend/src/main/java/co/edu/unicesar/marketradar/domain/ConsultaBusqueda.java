package co.edu.unicesar.marketradar.domain;

import co.edu.unicesar.marketradar.exception.ValidacionException;

import java.util.Collections;
import java.util.Set;

/**
 * Parámetros de una búsqueda ya validados. Una instancia solo puede existir si sus datos son correctos.
 */
public final class ConsultaBusqueda {

    public static final int LONGITUD_MINIMA = 2;
    public static final int LONGITUD_MAXIMA = 100;
    public static final int TAMANO_MAXIMO = 20;

    private final String termino;
    private final OrdenResultados orden;
    private final Set<String> marketplaces;
    private final int pagina;
    private final int tamano;

    private ConsultaBusqueda(String termino, OrdenResultados orden, Set<String> marketplaces, int pagina, int tamano) {
        this.termino = termino;
        this.orden = orden;
        this.marketplaces = marketplaces;
        this.pagina = pagina;
        this.tamano = tamano;
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
        return new ConsultaBusqueda(limpio, ordenValido, codigos, pagina, tamano);
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
     * Clave de caché: dos consultas con el mismo término y marketplaces comparten resultados.
     *
     * @return clave normalizada
     */
    public String claveCache() {
        return termino.toLowerCase() + "|" + String.join(",", marketplaces.stream().sorted().toList());
    }
}
