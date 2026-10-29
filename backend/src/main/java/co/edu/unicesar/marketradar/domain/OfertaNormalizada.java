package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Modelo común de oferta que producen todos los adaptadores, sin importar el formato de cada API.
 */
public final class OfertaNormalizada {

    private final String codigoMarketplace;
    private final String idExterno;
    private final String titulo;
    private final BigDecimal precio;
    private final String moneda;
    private final BigDecimal costoEnvio;
    private final String condicion;
    private final String vendedor;
    private final String urlDestino;
    private final String urlImagen;
    private final OffsetDateTime consultadoEn;

    /**
     * Crea una oferta normalizada validando los campos obligatorios.
     *
     * @param codigoMarketplace código del marketplace de origen
     * @param idExterno identificador de la oferta en el marketplace
     * @param titulo título del producto
     * @param precio precio sin envío
     * @param moneda código ISO 4217
     * @param costoEnvio costo de envío, {@code null} si no se conoce
     * @param condicion nuevo, usado, etc.
     * @param vendedor nombre del vendedor
     * @param urlDestino enlace a la oferta en el sitio original
     * @param urlImagen enlace a la imagen del producto
     * @param consultadoEn momento en que se consultó el precio
     */
    public OfertaNormalizada(String codigoMarketplace, String idExterno, String titulo, BigDecimal precio,
                             String moneda, BigDecimal costoEnvio, String condicion, String vendedor,
                             String urlDestino, String urlImagen, OffsetDateTime consultadoEn) {
        this.codigoMarketplace = Objects.requireNonNull(codigoMarketplace, "codigoMarketplace");
        this.idExterno = Objects.requireNonNull(idExterno, "idExterno");
        this.titulo = Objects.requireNonNull(titulo, "titulo");
        this.precio = Objects.requireNonNull(precio, "precio");
        this.moneda = Objects.requireNonNull(moneda, "moneda");
        this.costoEnvio = costoEnvio;
        this.condicion = condicion;
        this.vendedor = vendedor;
        this.urlDestino = Objects.requireNonNull(urlDestino, "urlDestino");
        this.urlImagen = urlImagen;
        this.consultadoEn = consultadoEn == null ? OffsetDateTime.now() : consultadoEn;
    }

    /**
     * Precio que paga el usuario: precio más envío, si se conoce.
     *
     * @return precio total
     */
    public BigDecimal precioTotal() {
        return costoEnvio == null ? precio : precio.add(costoEnvio);
    }

    /**
     * Clave única de la oferta entre todos los marketplaces.
     *
     * @return {@code codigoMarketplace|idExterno}
     */
    public String clave() {
        return codigoMarketplace + "|" + idExterno;
    }

    /**
     * Devuelve una copia con precio, envío y moneda convertidos.
     *
     * @param nuevoPrecio precio convertido
     * @param nuevoEnvio envío convertido
     * @param nuevaMoneda moneda destino
     * @return oferta convertida
     */
    public OfertaNormalizada conPrecio(BigDecimal nuevoPrecio, BigDecimal nuevoEnvio, String nuevaMoneda) {
        return new OfertaNormalizada(codigoMarketplace, idExterno, titulo, nuevoPrecio, nuevaMoneda, nuevoEnvio,
                condicion, vendedor, urlDestino, urlImagen, consultadoEn);
    }

    public String getCodigoMarketplace() {
        return codigoMarketplace;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public String getTitulo() {
        return titulo;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public String getMoneda() {
        return moneda;
    }

    public BigDecimal getCostoEnvio() {
        return costoEnvio;
    }

    public String getCondicion() {
        return condicion;
    }

    public String getVendedor() {
        return vendedor;
    }

    public String getUrlDestino() {
        return urlDestino;
    }

    public String getUrlImagen() {
        return urlImagen;
    }

    public OffsetDateTime getConsultadoEn() {
        return consultadoEn;
    }
}
