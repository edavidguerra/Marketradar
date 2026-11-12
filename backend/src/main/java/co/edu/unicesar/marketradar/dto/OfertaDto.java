package co.edu.unicesar.marketradar.dto;

import co.edu.unicesar.marketradar.domain.CondicionProducto;
import co.edu.unicesar.marketradar.domain.EnlaceCompra;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import co.edu.unicesar.marketradar.domain.UbicacionVendedor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Oferta tal como la recibe el frontend en los resultados de búsqueda.
 *
 * @param id identificador para consultar detalle e historial; {@code null} si no se guardó
 * @param tienda código del marketplace
 * @param titulo nombre del producto
 * @param precio precio del producto, sin envío
 * @param costoEnvio costo de envío; {@code null} si no se conoce (nunca se cuenta como 0)
 * @param estadoEnvio {@code gratis}, {@code conocido}, {@code estimado} o {@code desconocido}
 * @param costoConocido precio más el envío que informa la tienda; si no lo informa, solo el precio
 * @param totalConfirmado precio total confirmado, o {@code null} si falta algún dato (envío, impuestos o una alerta)
 * @param faltaParaElTotal qué le falta al precio para ser el total, o {@code null} si está confirmado
 * @param moneda moneda de los precios (COP)
 * @param condicion código de la condición ({@code nuevo}, {@code usado}, {@code reacondicionado}, {@code exhibicion}),
 *                  o {@code null} si la tienda no la dice
 * @param condicionTexto la condición para mostrar, por ejemplo "De exhibición", o "No informada"
 * @param vendedor nombre del vendedor
 * @param urlDestino enlace de la oferta
 * @param enlace a dónde lleva el enlace y qué debe decir el botón
 * @param urlImagen enlace a la foto
 * @param calificacion estrellas de 0 a 5 según las opiniones del producto, {@code null} si no se informan
 * @param totalResenas cantidad de opiniones
 * @param consultadoEn momento de la consulta, para mostrar qué tan reciente es el precio
 * @param mejorPrecio si es la oferta más barata de la búsqueda
 * @param resenasDisponibles si se pueden pedir las reseñas del producto ({@code GET /api/v1/ofertas/{id}/resenas})
 * @param ubicacion desde dónde vende la tienda ("Colombia", "Estados Unidos", "el exterior"), {@code null} si no se
 *                  sabe
 * @param desdeExterior si se sabe que la tienda vende desde otro país
 * @param alertaPrecio por qué el precio no se pudo verificar, o {@code null}
 * @param conversion de dónde salió el precio en pesos, o {@code null} si la tienda ya lo daba en pesos
 */
public record OfertaDto(Long id, String tienda, String titulo, BigDecimal precio, BigDecimal costoEnvio,
                        String estadoEnvio, BigDecimal costoConocido, BigDecimal totalConfirmado,
                        String faltaParaElTotal, String moneda, String condicion, String condicionTexto,
                        String vendedor, String urlDestino, EnlaceDto enlace, String urlImagen,
                        BigDecimal calificacion, Integer totalResenas, OffsetDateTime consultadoEn,
                        boolean mejorPrecio, boolean resenasDisponibles, String ubicacion, boolean desdeExterior,
                        String alertaPrecio, ConversionDto conversion) {

    /**
     * @param tipo {@code directo}, {@code redireccion}, {@code pagina_google} o {@code busqueda_google}
     * @param texto texto del botón, que dice a dónde lleva ("Buscar esta oferta en Google")
     * @param dominio servidor de destino
     * @param llevaALaTienda si abre la oferta en la tienda
     */
    public record EnlaceDto(String tipo, String texto, String dominio, boolean llevaALaTienda) {
        static EnlaceDto desde(EnlaceCompra enlace) {
            return new EnlaceDto(enlace.tipo().name().toLowerCase(Locale.ROOT), enlace.textoBoton(), enlace.dominio(),
                    enlace.llevaALaTienda());
        }
    }

    /**
     * Rastro de un precio convertido a pesos.
     *
     * @param precioOriginal precio en la moneda de la tienda
     * @param monedaOriginal moneda de la tienda
     * @param precioTexto el precio tal como lo escribió la tienda
     * @param tasa pesos por unidad de la moneda original
     * @param fuente de dónde salió la tasa
     * @param fecha desde cuándo rige la tasa
     */
    public record ConversionDto(BigDecimal precioOriginal, String monedaOriginal, String precioTexto, BigDecimal tasa,
                                String fuente, LocalDate fecha) {
    }

    /**
     * Convierte una oferta normalizada en el DTO de respuesta.
     *
     * @param oferta oferta del resultado
     * @param id identificador guardado
     * @param mejorPrecio si es la más barata
     * @return DTO
     */
    public static OfertaDto desde(OfertaNormalizada oferta, Long id, boolean mejorPrecio) {
        UbicacionVendedor ubicacion = oferta.ubicacion();
        TasaCambio tasa = oferta.getTasaAplicada();
        ConversionDto conversion = tasa == null ? null : new ConversionDto(oferta.getPrecioOriginal(),
                oferta.getMonedaOriginal(), oferta.getPrecioTexto(), tasa.valor(), tasa.fuente(), tasa.vigenteDesde());
        return new OfertaDto(id, oferta.getCodigoMarketplace(), oferta.getTitulo(), oferta.getPrecio(),
                oferta.getCostoEnvio(), oferta.getEstadoEnvio().codigo(), oferta.costoConocido(),
                oferta.totalConfirmado().orElse(null), oferta.faltaParaElTotal().orElse(null), oferta.getMoneda(),
                oferta.condicionProducto().map(CondicionProducto::codigo).orElse(null),
                oferta.condicionProducto().map(CondicionProducto::texto).orElse("No informada"),
                oferta.getVendedor(), oferta.getUrlDestino(), EnlaceDto.desde(oferta.enlace()), oferta.getUrlImagen(),
                oferta.getCalificacion(), oferta.getTotalResenas(), oferta.getConsultadoEn(), mejorPrecio,
                id != null && oferta.getTokenResenas() != null, ubicacion.texto(), ubicacion.desdeExterior(),
                oferta.getAlertaPrecio(), conversion);
    }
}
