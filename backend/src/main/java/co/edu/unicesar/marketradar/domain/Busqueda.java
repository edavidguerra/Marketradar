package co.edu.unicesar.marketradar.domain;

import java.time.OffsetDateTime;

/**
 * Registro estadístico de una búsqueda realizada (sin datos personales).
 */
public class Busqueda {

    private Long id;
    private String termino;
    private int totalResultados;
    private int duracionMs;
    private OffsetDateTime creadaEn;

    /** Constructor vacío requerido por los DAO. */
    public Busqueda() {
    }

    /**
     * Crea el registro de una búsqueda.
     *
     * @param termino texto buscado
     * @param totalResultados cantidad de ofertas encontradas
     * @param duracionMs duración total en milisegundos
     */
    public Busqueda(String termino, int totalResultados, int duracionMs) {
        this.termino = termino;
        this.totalResultados = totalResultados;
        this.duracionMs = duracionMs;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTermino() {
        return termino;
    }

    public void setTermino(String termino) {
        this.termino = termino;
    }

    public int getTotalResultados() {
        return totalResultados;
    }

    public void setTotalResultados(int totalResultados) {
        this.totalResultados = totalResultados;
    }

    public int getDuracionMs() {
        return duracionMs;
    }

    public void setDuracionMs(int duracionMs) {
        this.duracionMs = duracionMs;
    }

    public OffsetDateTime getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(OffsetDateTime creadaEn) {
        this.creadaEn = creadaEn;
    }
}
