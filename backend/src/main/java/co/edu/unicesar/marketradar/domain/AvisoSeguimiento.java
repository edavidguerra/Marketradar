package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Un correo de un seguimiento: la confirmación o un aviso de precio, con su estado de envío. Es una fila de
 * {@code aviso_seguimiento}.
 */
public class AvisoSeguimiento {

    /** Qué dice el correo. */
    public enum Tipo { CONFIRMACION, BAJA_PRECIO, PRECIO_OBJETIVO }

    /** Si ya se mandó. */
    public enum Estado { PENDIENTE, ENVIADO, FALLIDO, DESCARTADO }

    private Long id;
    private long usuarioId;
    private long ofertaId;
    private Tipo tipo;
    private String claveEvento;
    private String correoDestino;
    private BigDecimal precioAnterior;
    private BigDecimal precioActual;
    private String moneda;
    private OffsetDateTime comprobadoEn;
    private Estado estado = Estado.PENDIENTE;
    private int intentos;
    private String ultimoError;
    private OffsetDateTime creadoEn;
    private OffsetDateTime enviadoEn;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public long getOfertaId() {
        return ofertaId;
    }

    public void setOfertaId(long ofertaId) {
        this.ofertaId = ofertaId;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public void setTipo(Tipo tipo) {
        this.tipo = tipo;
    }

    public String getClaveEvento() {
        return claveEvento;
    }

    public void setClaveEvento(String claveEvento) {
        this.claveEvento = claveEvento;
    }

    public String getCorreoDestino() {
        return correoDestino;
    }

    public void setCorreoDestino(String correoDestino) {
        this.correoDestino = correoDestino;
    }

    public BigDecimal getPrecioAnterior() {
        return precioAnterior;
    }

    public void setPrecioAnterior(BigDecimal precioAnterior) {
        this.precioAnterior = precioAnterior;
    }

    public BigDecimal getPrecioActual() {
        return precioActual;
    }

    public void setPrecioActual(BigDecimal precioActual) {
        this.precioActual = precioActual;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public OffsetDateTime getComprobadoEn() {
        return comprobadoEn;
    }

    public void setComprobadoEn(OffsetDateTime comprobadoEn) {
        this.comprobadoEn = comprobadoEn;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public void setUltimoError(String ultimoError) {
        this.ultimoError = ultimoError;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public OffsetDateTime getEnviadoEn() {
        return enviadoEn;
    }

    public void setEnviadoEn(OffsetDateTime enviadoEn) {
        this.enviadoEn = enviadoEn;
    }
}
