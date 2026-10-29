package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Oferta de un producto en un marketplace. Es experta en calcular su precio total
 * (GRASP Information Expert) y contiene su historial de precios (composición).
 */
public class Oferta {

    private Long id;
    private Long productoId;
    private Short marketplaceId;
    private String idExterno;
    private String titulo;
    private String urlDestino;
    private String urlImagen;
    private BigDecimal precioActual;
    private String moneda = "COP";
    private BigDecimal costoEnvio;
    private String condicion;
    private String vendedor;
    private BigDecimal calificacion;
    private Integer totalResenas;
    private OffsetDateTime consultadoEn;
    private final List<HistorialPrecio> historial = new ArrayList<>();

    /** Constructor vacío requerido por los DAO. */
    public Oferta() {
    }

    /**
     * Calcula el precio que paga el usuario: precio más envío (si se conoce).
     *
     * @return precio total
     */
    public BigDecimal calcularPrecioTotal() {
        BigDecimal envio = costoEnvio == null ? BigDecimal.ZERO : costoEnvio;
        return precioActual.add(envio);
    }

    /**
     * Indica si un precio nuevo es distinto del actual, para decidir si se registra en el historial.
     *
     * @param precioNuevo precio recién consultado
     * @return {@code true} si el precio cambió
     */
    public boolean cambioPrecio(BigDecimal precioNuevo) {
        return precioActual == null || precioActual.compareTo(precioNuevo) != 0;
    }

    /**
     * Agrega un registro al historial de esta oferta.
     *
     * @param registro precio registrado
     */
    public void agregarHistorial(HistorialPrecio registro) {
        historial.add(registro);
    }

    /**
     * Devuelve una copia del historial de precios.
     *
     * @return lista de registros
     */
    public List<HistorialPrecio> getHistorial() {
        return new ArrayList<>(historial);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductoId() {
        return productoId;
    }

    public void setProductoId(Long productoId) {
        this.productoId = productoId;
    }

    public Short getMarketplaceId() {
        return marketplaceId;
    }

    public void setMarketplaceId(Short marketplaceId) {
        this.marketplaceId = marketplaceId;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public void setIdExterno(String idExterno) {
        this.idExterno = idExterno;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getUrlDestino() {
        return urlDestino;
    }

    public void setUrlDestino(String urlDestino) {
        this.urlDestino = urlDestino;
    }

    public String getUrlImagen() {
        return urlImagen;
    }

    public void setUrlImagen(String urlImagen) {
        this.urlImagen = urlImagen;
    }

    public BigDecimal getPrecioActual() {
        return precioActual;
    }

    /**
     * Asigna el precio actual validando que no sea negativo.
     *
     * @param precioActual precio en la moneda de la oferta
     */
    public void setPrecioActual(BigDecimal precioActual) {
        if (precioActual != null && precioActual.signum() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo");
        }
        this.precioActual = precioActual;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getCostoEnvio() {
        return costoEnvio;
    }

    public void setCostoEnvio(BigDecimal costoEnvio) {
        this.costoEnvio = costoEnvio;
    }

    public String getCondicion() {
        return condicion;
    }

    public void setCondicion(String condicion) {
        this.condicion = condicion;
    }

    public String getVendedor() {
        return vendedor;
    }

    public void setVendedor(String vendedor) {
        this.vendedor = vendedor;
    }

    public BigDecimal getCalificacion() {
        return calificacion;
    }

    public void setCalificacion(BigDecimal calificacion) {
        this.calificacion = calificacion;
    }

    public Integer getTotalResenas() {
        return totalResenas;
    }

    public void setTotalResenas(Integer totalResenas) {
        this.totalResenas = totalResenas;
    }

    public OffsetDateTime getConsultadoEn() {
        return consultadoEn;
    }

    public void setConsultadoEn(OffsetDateTime consultadoEn) {
        this.consultadoEn = consultadoEn;
    }
}
