package co.edu.unicesar.marketradar.seguridad;

import co.edu.unicesar.marketradar.domain.Rol;
import co.edu.unicesar.marketradar.domain.Usuario;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * Datos de la persona con sesión iniciada que Spring Security guarda en la sesión.
 * Los controladores lo reciben con {@code @AuthenticationPrincipal}.
 */
public class UsuarioAutenticado implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String correo;
    private final String nombre;
    private final Rol rol;
    private transient String claveHash;
    private final boolean activo;

    /**
     * @param usuario cuenta leída de la base de datos
     */
    public UsuarioAutenticado(Usuario usuario) {
        this.id = usuario.getId();
        this.correo = usuario.getCorreo();
        this.nombre = usuario.getNombres();
        this.rol = usuario.getRol();
        this.claveHash = usuario.getClaveHash();
        this.activo = usuario.isActivo();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() {
        return claveHash;
    }

    @Override
    public String getUsername() {
        return correo;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }

    /** Borra el hash de la contraseña después de verificarla, para que no quede en la sesión. */
    @Override
    public void eraseCredentials() {
        claveHash = null;
    }

    public long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Rol getRol() {
        return rol;
    }
}
