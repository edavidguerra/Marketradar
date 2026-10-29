package co.edu.unicesar.marketradar.domain;

import java.time.OffsetDateTime;

/**
 * Petición, queja, reclamo o sugerencia que radica un usuario y responde un administrador.
 * Conoce sus propias reglas de cambio de estado (GRASP Information Expert).
 */
public class Pqr {

    private Long id;
    private Long usuarioId;
    private String nombreUsuario;
    private String correoUsuario;
    private TipoPqr tipo;
    private String asunto;
    private String descripcion;
    private EstadoPqr estado = EstadoPqr.RADICADA;
    private String respuesta;
    private Long respondidaPor;
    private OffsetDateTime creadaEn;
    private OffsetDateTime respondidaEn;

    /** Constructor vacío requerido por los DAO. */
    public Pqr() {
    }

    /**
     * Crea una PQR nueva en estado {@link EstadoPqr#RADICADA}.
     *
     * @param usuarioId usuario que la radica
     * @param tipo clase de solicitud
     * @param asunto resumen corto
     * @param descripcion detalle de la solicitud
     */
    public Pqr(Long usuarioId, TipoPqr tipo, String asunto, String descripcion) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.asunto = asunto;
        this.descripcion = descripcion;
    }

    /**
     * Marca la PQR como leída por un administrador, si aún no tiene respuesta.
     */
    public void marcarEnProceso() {
        if (estado == EstadoPqr.RADICADA) {
            estado = EstadoPqr.EN_PROCESO;
        }
    }

    /**
     * Registra la respuesta de un administrador.
     *
     * @param texto respuesta para el usuario
     * @param administradorId administrador que responde
     */
    public void responder(String texto, Long administradorId) {
        this.respuesta = texto;
        this.respondidaPor = administradorId;
        this.estado = EstadoPqr.RESPONDIDA;
        this.respondidaEn = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getCorreoUsuario() {
        return correoUsuario;
    }

    public void setCorreoUsuario(String correoUsuario) {
        this.correoUsuario = correoUsuario;
    }

    public TipoPqr getTipo() {
        return tipo;
    }

    public void setTipo(TipoPqr tipo) {
        this.tipo = tipo;
    }

    public String getAsunto() {
        return asunto;
    }

    public void setAsunto(String asunto) {
        this.asunto = asunto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public EstadoPqr getEstado() {
        return estado;
    }

    public void setEstado(EstadoPqr estado) {
        this.estado = estado;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(String respuesta) {
        this.respuesta = respuesta;
    }

    public Long getRespondidaPor() {
        return respondidaPor;
    }

    public void setRespondidaPor(Long respondidaPor) {
        this.respondidaPor = respondidaPor;
    }

    public OffsetDateTime getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(OffsetDateTime creadaEn) {
        this.creadaEn = creadaEn;
    }

    public OffsetDateTime getRespondidaEn() {
        return respondidaEn;
    }

    public void setRespondidaEn(OffsetDateTime respondidaEn) {
        this.respondidaEn = respondidaEn;
    }
}
