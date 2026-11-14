package co.edu.unicesar.marketradar.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Una persona sigue una oferta concreta (tienda, publicación, vendedor y condición) para enterarse por correo cuando
 * baja de precio o llega al precio que ella eligió. Es una fila de {@code seguimiento_oferta}.
 */
public class Seguimiento {

    /** Qué se le avisa a la persona. */
    public enum TipoAviso { BAJA_PRECIO, PRECIO_OBJETIVO }

    /** Si se están revisando los precios. Uno cancelado no se revisa ni se le manda nada. */
    public enum Estado { ACTIVO, PAUSADO, CANCELADO }

    /** Qué pasó en la última comprobación del precio. */
    public enum Resultado {
        /** Todavía no se ha comprobado. */
        SIN_COMPROBAR,
        /** Primera comprobación: se guardó el precio con que se comparan las siguientes. */
        REFERENCIA,
        SIN_CAMBIO,
        SUBIO,
        BAJO,
        OBJETIVO_ALCANZADO,
        /** La tienda ya no muestra esta publicación en la búsqueda. */
        NO_ENCONTRADA,
        /** Cambió el vendedor, la condición, la moneda o la variante: el precio nuevo es de otra oferta. */
        VARIANTE_DISTINTA,
        /** El precio llegó con una alerta ("desde", por cuotas, distinto al de la tienda): no se usa. */
        PRECIO_DUDOSO,
        /** La tienda no respondió. */
        ERROR_TIENDA
    }

    private long usuarioId;
    private long ofertaId;
    private TipoAviso tipoAviso = TipoAviso.BAJA_PRECIO;
    private BigDecimal precioObjetivo;
    private Estado estado = Estado.ACTIVO;
    private BigDecimal precioReferencia;
    private String monedaReferencia;
    private BigDecimal precioOriginalReferencia;
    private String monedaOriginalReferencia;
    private String vendedorReferencia;
    private String condicionReferencia;
    private String varianteReferencia;
    private OffsetDateTime referenciaDesde;
    private String terminoBusqueda;
    private BigDecimal ultimoPrecio;
    private OffsetDateTime ultimaComprobacion;
    private Resultado resultado = Resultado.SIN_COMPROBAR;
    private int fallosConsecutivos;
    private OffsetDateTime proximaComprobacion;
    private boolean objetivoAvisado;
    private String tokenCancelacion;
    private int version;
    private OffsetDateTime creadoEn;

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

    public TipoAviso getTipoAviso() {
        return tipoAviso;
    }

    public void setTipoAviso(TipoAviso tipoAviso) {
        this.tipoAviso = tipoAviso;
    }

    public BigDecimal getPrecioObjetivo() {
        return precioObjetivo;
    }

    public void setPrecioObjetivo(BigDecimal precioObjetivo) {
        this.precioObjetivo = precioObjetivo;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public BigDecimal getPrecioReferencia() {
        return precioReferencia;
    }

    public void setPrecioReferencia(BigDecimal precioReferencia) {
        this.precioReferencia = precioReferencia;
    }

    public String getMonedaReferencia() {
        return monedaReferencia;
    }

    public void setMonedaReferencia(String monedaReferencia) {
        this.monedaReferencia = monedaReferencia;
    }

    public BigDecimal getPrecioOriginalReferencia() {
        return precioOriginalReferencia;
    }

    public void setPrecioOriginalReferencia(BigDecimal precioOriginalReferencia) {
        this.precioOriginalReferencia = precioOriginalReferencia;
    }

    public String getMonedaOriginalReferencia() {
        return monedaOriginalReferencia;
    }

    public void setMonedaOriginalReferencia(String monedaOriginalReferencia) {
        this.monedaOriginalReferencia = monedaOriginalReferencia;
    }

    public String getVendedorReferencia() {
        return vendedorReferencia;
    }

    public void setVendedorReferencia(String vendedorReferencia) {
        this.vendedorReferencia = vendedorReferencia;
    }

    public String getCondicionReferencia() {
        return condicionReferencia;
    }

    public void setCondicionReferencia(String condicionReferencia) {
        this.condicionReferencia = condicionReferencia;
    }

    public String getVarianteReferencia() {
        return varianteReferencia;
    }

    public void setVarianteReferencia(String varianteReferencia) {
        this.varianteReferencia = varianteReferencia;
    }

    public OffsetDateTime getReferenciaDesde() {
        return referenciaDesde;
    }

    public void setReferenciaDesde(OffsetDateTime referenciaDesde) {
        this.referenciaDesde = referenciaDesde;
    }

    public String getTerminoBusqueda() {
        return terminoBusqueda;
    }

    public void setTerminoBusqueda(String terminoBusqueda) {
        this.terminoBusqueda = terminoBusqueda;
    }

    public BigDecimal getUltimoPrecio() {
        return ultimoPrecio;
    }

    public void setUltimoPrecio(BigDecimal ultimoPrecio) {
        this.ultimoPrecio = ultimoPrecio;
    }

    public OffsetDateTime getUltimaComprobacion() {
        return ultimaComprobacion;
    }

    public void setUltimaComprobacion(OffsetDateTime ultimaComprobacion) {
        this.ultimaComprobacion = ultimaComprobacion;
    }

    public Resultado getResultado() {
        return resultado;
    }

    public void setResultado(Resultado resultado) {
        this.resultado = resultado;
    }

    public int getFallosConsecutivos() {
        return fallosConsecutivos;
    }

    public void setFallosConsecutivos(int fallosConsecutivos) {
        this.fallosConsecutivos = fallosConsecutivos;
    }

    public OffsetDateTime getProximaComprobacion() {
        return proximaComprobacion;
    }

    public void setProximaComprobacion(OffsetDateTime proximaComprobacion) {
        this.proximaComprobacion = proximaComprobacion;
    }

    public boolean isObjetivoAvisado() {
        return objetivoAvisado;
    }

    public void setObjetivoAvisado(boolean objetivoAvisado) {
        this.objetivoAvisado = objetivoAvisado;
    }

    public String getTokenCancelacion() {
        return tokenCancelacion;
    }

    public void setTokenCancelacion(String tokenCancelacion) {
        this.tokenCancelacion = tokenCancelacion;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
