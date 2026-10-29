package co.edu.unicesar.marketradar.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Cuenta de una persona en Marketradar, con su rol y sus datos de perfil.
 * La contraseña nunca se guarda en texto plano: solo su hash BCrypt.
 */
public class Usuario {

    private Long id;
    private String correo;
    private String claveHash;
    private Rol rol = Rol.USUARIO;
    private String nombres;
    private String apellidos;
    private TipoDocumento tipoDocumento;
    private String numeroDocumento;
    private LocalDate fechaNacimiento;
    private String celular;
    private String ciudad;
    private boolean activo = true;
    private OffsetDateTime creadoEn;

    /** Constructor vacío requerido por los DAO. */
    public Usuario() {
    }

    /**
     * Crea una cuenta con los datos mínimos de registro.
     *
     * @param correo correo, que también es el nombre de usuario
     * @param claveHash hash BCrypt de la contraseña
     * @param rol rol de la cuenta
     * @param nombres nombres de la persona
     * @param apellidos apellidos de la persona
     */
    public Usuario(String correo, String claveHash, Rol rol, String nombres, String apellidos) {
        this.correo = correo;
        this.claveHash = claveHash;
        this.rol = rol;
        this.nombres = nombres;
        this.apellidos = apellidos;
    }

    /**
     * Nombre para mostrar en saludos y en la gestión de PQR.
     *
     * @return nombres y apellidos
     */
    public String nombreCompleto() {
        return nombres + " " + apellidos;
    }

    /**
     * Indica si la cuenta es de administrador.
     *
     * @return {@code true} si el rol es {@link Rol#ADMINISTRADOR}
     */
    public boolean esAdministrador() {
        return rol == Rol.ADMINISTRADOR;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getClaveHash() {
        return claveHash;
    }

    public void setClaveHash(String claveHash) {
        this.claveHash = claveHash;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(TipoDocumento tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public void setNumeroDocumento(String numeroDocumento) {
        this.numeroDocumento = numeroDocumento;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public String getCelular() {
        return celular;
    }

    public void setCelular(String celular) {
        this.celular = celular;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}
