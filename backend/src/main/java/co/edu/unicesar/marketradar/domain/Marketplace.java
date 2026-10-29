package co.edu.unicesar.marketradar.domain;

/**
 * Tienda en línea integrada al comparador y su estado de disponibilidad.
 */
public class Marketplace {

    private Short id;
    private String codigo;
    private String nombre;
    private String sitioWeb;
    private boolean activo;

    /** Constructor vacío requerido por los DAO. */
    public Marketplace() {
    }

    /**
     * Crea un marketplace con todos sus datos.
     *
     * @param id identificador en la base de datos
     * @param codigo código único, por ejemplo {@code aliexpress}
     * @param nombre nombre visible para el usuario
     * @param sitioWeb URL del sitio de la tienda
     * @param activo si se consulta en las búsquedas
     */
    public Marketplace(Short id, String codigo, String nombre, String sitioWeb, boolean activo) {
        this.id = id;
        this.codigo = codigo;
        this.nombre = nombre;
        this.sitioWeb = sitioWeb;
        this.activo = activo;
    }

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getSitioWeb() {
        return sitioWeb;
    }

    public void setSitioWeb(String sitioWeb) {
        this.sitioWeb = sitioWeb;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
