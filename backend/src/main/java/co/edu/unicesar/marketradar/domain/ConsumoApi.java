package co.edu.unicesar.marketradar.domain;

import java.time.OffsetDateTime;

/**
 * Registro de una llamada a la API de un marketplace, para vigilar los límites de uso.
 */
public class ConsumoApi {

    private Long id;
    private Short marketplaceId;
    private boolean exitoso;
    private Integer codigoHttp;
    private int duracionMs;
    private OffsetDateTime registradoEn;

    /** Constructor vacío requerido por los DAO. */
    public ConsumoApi() {
    }

    /**
     * Crea el registro de una llamada.
     *
     * @param marketplaceId marketplace consultado
     * @param exitoso si la llamada respondió correctamente
     * @param codigoHttp código HTTP de la respuesta, {@code null} si no hubo respuesta
     * @param duracionMs duración en milisegundos
     */
    public ConsumoApi(Short marketplaceId, boolean exitoso, Integer codigoHttp, int duracionMs) {
        this.marketplaceId = marketplaceId;
        this.exitoso = exitoso;
        this.codigoHttp = codigoHttp;
        this.duracionMs = duracionMs;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Short getMarketplaceId() {
        return marketplaceId;
    }

    public void setMarketplaceId(Short marketplaceId) {
        this.marketplaceId = marketplaceId;
    }

    public boolean isExitoso() {
        return exitoso;
    }

    public void setExitoso(boolean exitoso) {
        this.exitoso = exitoso;
    }

    public Integer getCodigoHttp() {
        return codigoHttp;
    }

    public void setCodigoHttp(Integer codigoHttp) {
        this.codigoHttp = codigoHttp;
    }

    public int getDuracionMs() {
        return duracionMs;
    }

    public void setDuracionMs(int duracionMs) {
        this.duracionMs = duracionMs;
    }

    public OffsetDateTime getRegistradoEn() {
        return registradoEn;
    }

    public void setRegistradoEn(OffsetDateTime registradoEn) {
        this.registradoEn = registradoEn;
    }
}
