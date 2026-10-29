package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Registro del precio de una oferta en una fecha. Solo se crea cuando el precio cambia.
 */
public class HistorialPrecio {

    private Long id;
    private Long ofertaId;
    private BigDecimal precio;
    private String moneda;
    private OffsetDateTime registradoEn;

    /** Constructor vacío requerido por los DAO. */
    public HistorialPrecio() {
    }

    /**
     * Crea un registro de precio para una oferta.
     *
     * @param ofertaId oferta a la que pertenece
     * @param precio precio registrado
     * @param moneda código ISO 4217 de la moneda
     */
    public HistorialPrecio(Long ofertaId, BigDecimal precio, String moneda) {
        this.ofertaId = ofertaId;
        this.precio = precio;
        this.moneda = moneda;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOfertaId() {
        return ofertaId;
    }

    public void setOfertaId(Long ofertaId) {
        this.ofertaId = ofertaId;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public OffsetDateTime getRegistradoEn() {
        return registradoEn;
    }

    public void setRegistradoEn(OffsetDateTime registradoEn) {
        this.registradoEn = registradoEn;
    }
}
