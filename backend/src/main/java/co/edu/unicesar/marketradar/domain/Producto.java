package co.edu.unicesar.marketradar.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Producto comparable que agrupa varias ofertas de distintos marketplaces (agregación:
 * las ofertas existen aunque el producto se elimine).
 */
public class Producto {

    private Long id;
    private String nombreNormalizado;
    private String marca;
    private OffsetDateTime creadoEn;
    private final List<Oferta> ofertas = new ArrayList<>();

    /** Constructor vacío requerido por los DAO. */
    public Producto() {
    }

    /**
     * Crea un producto con su nombre normalizado y marca.
     *
     * @param nombreNormalizado nombre en minúsculas y sin tildes usado para agrupar ofertas
     * @param marca marca del producto, puede ser {@code null}
     */
    public Producto(String nombreNormalizado, String marca) {
        this.nombreNormalizado = nombreNormalizado;
        this.marca = marca;
    }

    /**
     * Asocia una oferta a este producto.
     *
     * @param oferta oferta a agregar
     */
    public void agregarOferta(Oferta oferta) {
        ofertas.add(oferta);
    }

    /**
     * Devuelve una copia de las ofertas asociadas.
     *
     * @return lista de ofertas
     */
    public List<Oferta> getOfertas() {
        return new ArrayList<>(ofertas);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombreNormalizado() {
        return nombreNormalizado;
    }

    public void setNombreNormalizado(String nombreNormalizado) {
        this.nombreNormalizado = nombreNormalizado;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
