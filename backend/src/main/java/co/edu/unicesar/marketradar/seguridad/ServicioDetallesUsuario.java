package co.edu.unicesar.marketradar.seguridad;

import co.edu.unicesar.marketradar.dao.UsuarioDao;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Carga la cuenta por correo para que Spring Security compare la contraseña con su hash BCrypt.
 */
@Service
public class ServicioDetallesUsuario implements UserDetailsService {

    private final UsuarioDao usuarioDao;

    public ServicioDetallesUsuario(UsuarioDao usuarioDao) {
        this.usuarioDao = usuarioDao;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) {
        String limpio = correo == null ? "" : correo.strip().toLowerCase(Locale.ROOT);
        return usuarioDao.buscarPorCorreo(limpio)
                .map(UsuarioAutenticado::new)
                .orElseThrow(() -> new UsernameNotFoundException("Cuenta no encontrada"));
    }
}
