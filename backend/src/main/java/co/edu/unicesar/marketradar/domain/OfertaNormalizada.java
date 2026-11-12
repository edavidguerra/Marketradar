package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    private final String tokenResenas;
    private final EstadoEnvio estadoEnvio;
    private final String precioTexto;
    private final BigDecimal precioOriginal;
    private final String monedaOriginal;
    private final TasaCambio tasaAplicada;
    private final String alertaPrecio;

    private OfertaNormalizada(Builder builder) {
        this.codigoMarketplace = Objects.requireNonNull(builder.codigoMarketplace, "codigoMarketplace");
        this.idExterno = Objects.requireNonNull(builder.idExterno, "idExterno");
        this.titulo = Objects.requireNonNull(builder.titulo, "titulo");
        this.precio = Objects.requireNonNull(builder.precio, "precio");
        this.moneda = Objects.requireNonNull(builder.moneda, "moneda");
        this.condicion = builder.condicion;
        this.vendedor = builder.vendedor;
        this.urlDestino = Objects.requireNonNull(builder.urlDestino, "urlDestino");
        this.urlImagen = builder.urlImagen;
        this.calificacion = validarCalificacion(builder.calificacion);
        this.totalResenas = validarTotalResenas(builder.totalResenas);
        this.consultadoEn = builder.consultadoEn == null ? OffsetDateTime.now() : builder.consultadoEn;
        this.tokenResenas = builder.tokenResenas;
        this.estadoEnvio = estadoEnvio(builder.estadoEnvio, builder.costoEnvio);
        this.costoEnvio = estadoEnvio == EstadoEnvio.DESCONOCIDO ? null
                : estadoEnvio == EstadoEnvio.GRATIS ? BigDecimal.ZERO : builder.costoEnvio;
        this.precioTexto = builder.precioTexto;
        this.precioOriginal = builder.precioOriginal;
        this.monedaOriginal = builder.monedaOriginal;
        this.tasaAplicada = builder.tasaAplicada;
        this.alertaPrecio = builder.alertaPrecio;
        if (precio.signum() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo");
        }
        if (!urlDestino.startsWith("https://") && !urlDestino.startsWith("http://")) {
            throw new IllegalArgumentException("El enlace a la tienda debe ser una dirección web");
        }
    }

    /**
     * Clave para pedir después las reseñas de este producto a la tienda.
     *
     * @return clave, o {@code null} si la tienda no la dio
     */
    public String getTokenResenas() {
        return tokenResenas;
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
     * Desde dónde vende la tienda, deducido de su nombre y su dirección web.
     *
     * @return Colombia, otro país o desconocida
     */
    public UbicacionVendedor ubicacion() {
        return UbicacionVendedor.de(vendedor, urlDestino);
    }

    /**
     * Lo que se sabe que se paga como mínimo: el precio más el envío cuando la tienda lo informa (gratis, con valor o
     * estimado). Si el envío no se conoce, es solo el precio. Sirve para ordenar y filtrar, pero no es un total
     * confirmado: para eso está {@link #totalConfirmado()}.
     *
     * @return precio más el envío conocido
     */
    public BigDecimal costoConocido() {
        return costoEnvio == null ? precio : precio.add(costoEnvio);
    }

    /**
     * Precio total confirmado: solo cuando el envío es gratis o tiene valor, la tienda no vende desde el exterior
     * (allá pueden cobrar impuestos que no se conocen) y el precio no tiene ninguna alerta.
     *
     * @return precio más envío, o vacío si falta algo para confirmarlo
     */
    public Optional<BigDecimal> totalConfirmado() {
        if (!estadoEnvio.confirmado() || ubicacion().desdeExterior() || alertaPrecio != null) {
            return Optional.empty();
        }
        return Optional.of(costoConocido());
    }

    /**
     * Qué falta para que el precio mostrado sea el total, en palabras sencillas.
     *
     * @return por ejemplo "no incluye el envío, que la tienda no informa", o vacío si el total está confirmado
     */
    public Optional<String> faltaParaElTotal() {
        if (alertaPrecio != null) {
            return Optional.of(alertaPrecio);
        }
        List<String> faltan = new java.util.ArrayList<>();
        switch (estadoEnvio) {
            case DESCONOCIDO -> faltan.add("no incluye el envío, que la tienda no informa");
            case ESTIMADO -> faltan.add("el envío es un valor aproximado y puede costar más");
            default -> { }
        }
        if (ubicacion().desdeExterior()) {
            faltan.add("no incluye impuestos de importación, que no se conocen");
        }
        return faltan.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", faltan));
    }

    /** @return estado del envío */
    public EstadoEnvio getEstadoEnvio() {
        return estadoEnvio;
    }

    /** @return condición del producto, o vacío si la tienda no la dice */
    public Optional<CondicionProducto> condicionProducto() {
        return CondicionProducto.deCodigo(condicion);
    }

    /** @return a dónde lleva el enlace de la oferta */
    public EnlaceCompra enlace() {
        return EnlaceCompra.de(urlDestino);
    }

    /** @return {@code true} si el precio no tiene ninguna alerta y se puede usar para recomendar */
    public boolean precioVerificado() {
        return alertaPrecio == null;
    }

    /** @return por qué el precio no se pudo verificar, o {@code null} */
    public String getAlertaPrecio() {
        return alertaPrecio;
    }

    /** @return el precio tal como lo escribió la tienda, por ejemplo "US$ 899,99"; {@code null} si no vino */
    public String getPrecioTexto() {
        return precioTexto;
    }

    /** @return precio antes de convertirlo a pesos, o {@code null} si ya venía en pesos */
    public BigDecimal getPrecioOriginal() {
        return precioOriginal;
    }

    /** @return moneda en que vino el precio, o {@code null} si ya venía en pesos */
    public String getMonedaOriginal() {
        return monedaOriginal;
    }

    /** @return tasa con que se convirtió el precio, o {@code null} si no se convirtió */
    public TasaCambio getTasaAplicada() {
        return tasaAplicada;
    }

    /**
     * Copia con una alerta sobre el precio. La oferta se sigue mostrando, con la alerta, pero no se recomienda.
     *
     * @param alerta por qué no se puede confiar en el precio
     * @return copia con la alerta
     */
    public OfertaNormalizada conAlertaPrecio(String alerta) {
        return aBuilder().alertaPrecio(alerta).build();
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

    /**
     * Copia convertida a otra moneda, que recuerda el precio original y la tasa usada, para poder rastrear el valor.
     * Una oferta ya convertida no se vuelve a convertir.
     *
     * @param nuevoPrecio precio convertido
     * @param nuevoEnvio envío convertido, o {@code null}
     * @param nuevaMoneda moneda destino
     * @param tasa tasa aplicada
     * @return oferta convertida
     * @throws IllegalStateException si la oferta ya se había convertido
     */
    public OfertaNormalizada convertida(BigDecimal nuevoPrecio, BigDecimal nuevoEnvio, String nuevaMoneda,
                                       TasaCambio tasa) {
        if (precioOriginal != null) {
            throw new IllegalStateException("La oferta ya se convirtió desde " + monedaOriginal);
        }
        return aBuilder().precio(nuevoPrecio).costoEnvio(nuevoEnvio).moneda(nuevaMoneda)
                .precioOriginal(precio).monedaOriginal(moneda).tasaAplicada(tasa).build();
    }

    private static EstadoEnvio estadoEnvio(EstadoEnvio pedido, BigDecimal costo) {
        if (pedido == EstadoEnvio.ESTIMADO && costo != null && costo.signum() > 0) {
            return EstadoEnvio.ESTIMADO;
        }
        if (pedido == EstadoEnvio.DESCONOCIDO || costo == null || costo.signum() < 0) {
            return EstadoEnvio.DESCONOCIDO;
        }
        return costo.signum() == 0 ? EstadoEnvio.GRATIS : EstadoEnvio.CONOCIDO;
    }

    private Builder aBuilder() {
        return builder()
                .codigoMarketplace(codigoMarketplace).idExterno(idExterno).titulo(titulo)
                .precio(precio).moneda(moneda).costoEnvio(costoEnvio).condicion(condicion)
                .vendedor(vendedor).urlDestino(urlDestino).urlImagen(urlImagen)
                .calificacion(calificacion).totalResenas(totalResenas).consultadoEn(consultadoEn)
                .tokenResenas(tokenResenas).estadoEnvio(estadoEnvio).precioTexto(precioTexto)
                .precioOriginal(precioOriginal).monedaOriginal(monedaOriginal).tasaAplicada(tasaAplicada)
                .alertaPrecio(alertaPrecio);
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
        private String tokenResenas;
        private EstadoEnvio estadoEnvio;
        private String precioTexto;
        private BigDecimal precioOriginal;
        private String monedaOriginal;
        private TasaCambio tasaAplicada;
        private String alertaPrecio;

        private Builder() {
        }

        /**
         * Estado del envío. Si no se da, se deduce del costo: sin costo es desconocido, 0 es gratis y otro valor es
         * conocido. {@link EstadoEnvio#ESTIMADO} necesita un costo mayor que cero.
         *
         * @param estadoEnvio estado
         * @return este builder
         */
        public Builder estadoEnvio(EstadoEnvio estadoEnvio) {
            this.estadoEnvio = estadoEnvio;
            return this;
        }

        public Builder precioTexto(String precioTexto) {
            this.precioTexto = precioTexto;
            return this;
        }

        public Builder precioOriginal(BigDecimal precioOriginal) {
            this.precioOriginal = precioOriginal;
            return this;
        }

        public Builder monedaOriginal(String monedaOriginal) {
            this.monedaOriginal = monedaOriginal;
            return this;
        }

        public Builder tasaAplicada(TasaCambio tasaAplicada) {
            this.tasaAplicada = tasaAplicada;
            return this;
        }

        public Builder alertaPrecio(String alertaPrecio) {
            this.alertaPrecio = alertaPrecio;
            return this;
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
         * Clave con que la tienda entrega después las reseñas de este producto (Google Shopping la llama
         * {@code immersive_product_page_token}). No se guarda en la base de datos.
         *
         * @param tokenResenas clave, o {@code null} si la tienda no la da
         * @return este builder
         */
        public Builder tokenResenas(String tokenResenas) {
            this.tokenResenas = tokenResenas;
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
