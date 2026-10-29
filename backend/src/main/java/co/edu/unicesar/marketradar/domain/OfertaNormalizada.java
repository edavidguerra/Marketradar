package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Modelo común de oferta que producen todos los adaptadores, sin importar el formato de cada API.
 * Se crea con {@link #builder()} porque tiene muchos campos opcionales.
 */
public final class OfertaNormalizada {

    private static final BigDecimal CALIFICACION_MAXIMA = new BigDecimal("5");

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
    private final BigDecimal calificacion;
    private final Integer totalResenas;
    private final OffsetDateTime consultadoEn;

    private OfertaNormalizada(Builder builder) {
        this.codigoMarketplace = Objects.requireNonNull(builder.codigoMarketplace, "codigoMarketplace");
        this.idExterno = Objects.requireNonNull(builder.idExterno, "idExterno");
        this.titulo = Objects.requireNonNull(builder.titulo, "titulo");
        this.precio = Objects.requireNonNull(builder.precio, "precio");
        this.moneda = Objects.requireNonNull(builder.moneda, "moneda");
        this.costoEnvio = builder.costoEnvio;
        this.condicion = builder.condicion;
        this.vendedor = builder.vendedor;
        this.urlDestino = Objects.requireNonNull(builder.urlDestino, "urlDestino");
        this.urlImagen = builder.urlImagen;
        this.calificacion = validarCalificacion(builder.calificacion);
        this.totalResenas = validarTotalResenas(builder.totalResenas);
        this.consultadoEn = builder.consultadoEn == null ? OffsetDateTime.now() : builder.consultadoEn;
        if (precio.signum() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo");
        }
    }

    /**
     * Inicia la construcción de una oferta.
     *
     * @return builder vacío
     */
    public static Builder builder() {
        return new Builder();
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
     * Indica si la tienda informó calificación y cantidad de reseñas.
     *
     * @return {@code true} si hay reseñas
     */
    public boolean tieneResenas() {
        return calificacion != null && totalResenas != null && totalResenas > 0;
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
        return aBuilder().precio(nuevoPrecio).costoEnvio(nuevoEnvio).moneda(nuevaMoneda).build();
    }

    private Builder aBuilder() {
        return builder()
                .codigoMarketplace(codigoMarketplace).idExterno(idExterno).titulo(titulo)
                .precio(precio).moneda(moneda).costoEnvio(costoEnvio).condicion(condicion)
                .vendedor(vendedor).urlDestino(urlDestino).urlImagen(urlImagen)
                .calificacion(calificacion).totalResenas(totalResenas).consultadoEn(consultadoEn);
    }

    private static BigDecimal validarCalificacion(BigDecimal calificacion) {
        if (calificacion != null && (calificacion.signum() < 0 || calificacion.compareTo(CALIFICACION_MAXIMA) > 0)) {
            throw new IllegalArgumentException("La calificación debe estar entre 0 y 5");
        }
        return calificacion;
    }

    private static Integer validarTotalResenas(Integer totalResenas) {
        if (totalResenas != null && totalResenas < 0) {
            throw new IllegalArgumentException("El total de reseñas no puede ser negativo");
        }
        return totalResenas;
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

    public BigDecimal getCalificacion() {
        return calificacion;
    }

    public Integer getTotalResenas() {
        return totalResenas;
    }

    public OffsetDateTime getConsultadoEn() {
        return consultadoEn;
    }

    /**
     * Construye ofertas normalizadas paso a paso (patrón Builder).
     * Obligatorios: marketplace, id externo, título, precio, moneda y enlace a la tienda.
     */
    public static final class Builder {

        private String codigoMarketplace;
        private String idExterno;
        private String titulo;
        private BigDecimal precio;
        private String moneda;
        private BigDecimal costoEnvio;
        private String condicion;
        private String vendedor;
        private String urlDestino;
        private String urlImagen;
        private BigDecimal calificacion;
        private Integer totalResenas;
        private OffsetDateTime consultadoEn;

        private Builder() {
        }

        public Builder codigoMarketplace(String codigoMarketplace) {
            this.codigoMarketplace = codigoMarketplace;
            return this;
        }

        public Builder idExterno(String idExterno) {
            this.idExterno = idExterno;
            return this;
        }

        public Builder titulo(String titulo) {
            this.titulo = titulo;
            return this;
        }

        public Builder precio(BigDecimal precio) {
            this.precio = precio;
            return this;
        }

        public Builder moneda(String moneda) {
            this.moneda = moneda;
            return this;
        }

        public Builder costoEnvio(BigDecimal costoEnvio) {
            this.costoEnvio = costoEnvio;
            return this;
        }

        public Builder condicion(String condicion) {
            this.condicion = condicion;
            return this;
        }

        public Builder vendedor(String vendedor) {
            this.vendedor = vendedor;
            return this;
        }

        public Builder urlDestino(String urlDestino) {
            this.urlDestino = urlDestino;
            return this;
        }

        public Builder urlImagen(String urlImagen) {
            this.urlImagen = urlImagen;
            return this;
        }

        public Builder calificacion(BigDecimal calificacion) {
            this.calificacion = calificacion;
            return this;
        }

        public Builder totalResenas(Integer totalResenas) {
            this.totalResenas = totalResenas;
            return this;
        }

        public Builder consultadoEn(OffsetDateTime consultadoEn) {
            this.consultadoEn = consultadoEn;
            return this;
        }

        /**
         * Crea la oferta validando los campos obligatorios y los rangos.
         *
         * @return oferta normalizada inmutable
         */
        public OfertaNormalizada build() {
            return new OfertaNormalizada(this);
        }
    }
}
