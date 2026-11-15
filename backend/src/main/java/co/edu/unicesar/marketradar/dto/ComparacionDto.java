package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.EnlaceCompra;
import co.edu.unicesar.marketradar.service.ServicioComparacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Tabla de comparación de productos: productos en columnas y características en filas.
 *
 * @param productos productos comparados, en el orden en que se eligieron
 * @param filas características; cada fila tiene un valor por producto
 * @param equivalentes {@code true} solo si la equivalencia está confirmada
 * @param masBaratoId oferta más barata, solo si los productos son equivalentes y están en la misma moneda
 * @param avisos por qué no se deben comparar como iguales
 * @param equivalencia {@code equivalentes}, {@code diferentes} o {@code no_confirmada}
 * @param faltantes datos esenciales que faltan
 */
public record ComparacionDto(List<ProductoDto> productos, List<FilaDto> filas, boolean equivalentes, Long masBaratoId,
                             List<String> avisos, String equivalencia, List<String> faltantes) {

    /**
     * @param id identificador de la oferta
     * @param titulo nombre del producto
     * @param tienda código del marketplace
     * @param vendedor tienda para mostrar
     * @param urlImagen foto
     * @param urlDestino enlace de la oferta
     * @param enlace a dónde lleva el enlace y qué dice el botón
     * @param categoria tipo de producto deducido del título
     */
    public record ProductoDto(long id, String titulo, String tienda, String vendedor, String urlImagen,
                              String urlDestino, OfertaDto.EnlaceDto enlace, String categoria) {
    }

    /**
     * @param valor texto para mostrar, o "No disponible"
     * @param numero valor numérico, si lo tiene
     * @param disponible si se conoce el dato
     * @param fuente {@code tienda} o {@code titulo}, si es una característica
     */
    public record CeldaDto(String valor, BigDecimal numero, boolean disponible, String fuente) {
    }

    /**
     * @param clave nombre interno
     * @param etiqueta nombre para mostrar
     * @param grupo {@code oferta} o {@code especificaciones}
     * @param nota aclaración, si la tiene
     * @param valores un valor por producto
     * @param difiere si no todos los productos tienen el mismo valor
     */
    public record FilaDto(String clave, String etiqueta, String grupo, String nota, List<CeldaDto> valores,
                          boolean difiere, boolean esencial) {
    }

    /**
     * @param comparacion comparación del servicio
     * @return DTO
     */
    public static ComparacionDto desde(ServicioComparacion.Comparacion comparacion) {
        List<ProductoDto> productos = comparacion.productos().stream().map(p -> new ProductoDto(p.id(), p.titulo(),
                p.codigoTienda(), p.tienda(), p.urlImagen(), p.urlDestino(), enlace(p.enlace()),
                p.categoria().texto())).toList();
        List<FilaDto> filas = comparacion.filas().stream().map(f -> new FilaDto(f.clave(), f.etiqueta(), f.grupo(),
                f.nota(), f.celdas().stream().map(c -> new CeldaDto(c.valor(), c.numero(), c.disponible(), c.fuente()))
                .toList(), f.difiere(), f.esencial())).toList();
        return new ComparacionDto(productos, filas, comparacion.equivalentes(), comparacion.masBaratoId(),
                comparacion.avisos(), comparacion.equivalencia().name().toLowerCase(Locale.ROOT),
                comparacion.faltantes());
    }

    private static OfertaDto.EnlaceDto enlace(EnlaceCompra enlace) {
        return new OfertaDto.EnlaceDto(enlace.tipo().name().toLowerCase(Locale.ROOT), enlace.textoBoton(),
                enlace.dominio(), enlace.llevaALaTienda());
    }
}
