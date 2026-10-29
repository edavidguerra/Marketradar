package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.domain.Rol;
import co.edu.unicesar.marketradar.domain.Usuario;
import co.edu.unicesar.marketradar.exception.CredencialesInvalidasException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import co.edu.unicesar.marketradar.seguridad.ProteccionIntentosIngreso;
import co.edu.unicesar.marketradar.seguridad.UsuarioAutenticado;
import co.edu.unicesar.marketradar.validacion.PoliticaContrasena;
import co.edu.unicesar.marketradar.validacion.ValidadorCampos;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Reglas de negocio de las cuentas: registro con contraseña segura, ingreso con bloqueo por intentos
 * fallidos y creación del administrador inicial.
 */
@Service
public class ServicioCuentas {

    private final UsuarioDao usuarioDao;
    private final PasswordEncoder codificador;
    private final PoliticaContrasena politicaContrasena;
    private final AuthenticationManager gestorAutenticacion;
    private final ProteccionIntentosIngreso proteccionIntentos;

    public ServicioCuentas(UsuarioDao usuarioDao, PasswordEncoder codificador, PoliticaContrasena politicaContrasena,
                           AuthenticationManager gestorAutenticacion, ProteccionIntentosIngreso proteccionIntentos) {
        this.usuarioDao = usuarioDao;
        this.codificador = codificador;
        this.politicaContrasena = politicaContrasena;
        this.gestorAutenticacion = gestorAutenticacion;
        this.proteccionIntentos = proteccionIntentos;
    }

    /**
     * Crea una cuenta de usuario. Quien se registra desde la página siempre queda con rol USUARIO.
     *
     * @param nombres nombres
     * @param apellidos apellidos
     * @param correo correo, que será su usuario
     * @param clave contraseña
     * @param confirmacionClave contraseña repetida
     * @return cuenta creada
     */
    public Usuario registrar(String nombres, String apellidos, String correo, String clave, String confirmacionClave) {
        return crear(Rol.USUARIO, nombres, apellidos, correo, clave, confirmacionClave);
    }

    /**
     * Crea la cuenta de administrador si todavía no existe una con ese correo.
     *
     * @param nombres nombres
     * @param apellidos apellidos
     * @param correo correo del administrador
     * @param clave contraseña, que debe cumplir la misma política
     * @return {@code true} si se creó la cuenta
     */
    public boolean crearAdministradorSiNoExiste(String nombres, String apellidos, String correo, String clave) {
        String correoLimpio = ValidadorCampos.correo(correo);
        if (usuarioDao.buscarPorCorreo(correoLimpio).isPresent()) {
            return false;
        }
        crear(Rol.ADMINISTRADOR, nombres, apellidos, correoLimpio, clave, clave);
        return true;
    }

    /**
     * Verifica correo y contraseña. Después de 5 fallos seguidos la cuenta se bloquea 15 minutos.
     *
     * @param correo correo escrito
     * @param clave contraseña escrita
     * @return autenticación con la persona y su rol
     */
    public Authentication ingresar(String correo, String clave) {
        String correoLimpio = correo == null ? "" : correo.strip();
        if (proteccionIntentos.estaBloqueado(correoLimpio)) {
            throw new CredencialesInvalidasException("Por seguridad, su cuenta quedó bloqueada 15 minutos por varios "
                    + "intentos fallidos. Espere un momento e intente de nuevo.", true);
        }
        try {
            Authentication autenticacion = gestorAutenticacion.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(correoLimpio, clave == null ? "" : clave));
            proteccionIntentos.registrarExito(correoLimpio);
            return autenticacion;
        } catch (AuthenticationException e) {
            proteccionIntentos.registrarFallo(correoLimpio);
            throw new CredencialesInvalidasException("El correo o la contraseña no son correctos. Revíselos e "
                    + "intente de nuevo.", false);
        }
    }

    /**
     * Cambia la contraseña verificando la actual.
     *
     * @param usuario persona con sesión
     * @param claveActual contraseña actual
     * @param claveNueva contraseña nueva
     * @param confirmacion contraseña nueva repetida
     */
    public void cambiarClave(UsuarioAutenticado usuario, String claveActual, String claveNueva, String confirmacion) {
        Usuario cuenta = usuarioDao.buscarPorId(usuario.getId())
                .orElseThrow(() -> new ValidacionException("No encontramos su cuenta."));
        if (claveActual == null || !codificador.matches(claveActual, cuenta.getClaveHash())) {
            throw new ValidacionException("La contraseña actual no es correcta.");
        }
        validarClaveNueva(claveNueva, confirmacion, cuenta.getCorreo());
        usuarioDao.actualizarClave(cuenta.getId(), codificador.encode(claveNueva));
    }

    private Usuario crear(Rol rol, String nombres, String apellidos, String correo, String clave, String confirmacion) {
        String correoLimpio = ValidadorCampos.correo(correo);
        Usuario usuario = new Usuario(correoLimpio, null, rol,
                ValidadorCampos.nombre(nombres, "los nombres"),
                ValidadorCampos.nombre(apellidos, "los apellidos"));
        validarClaveNueva(clave, confirmacion, correoLimpio);
        if (usuarioDao.buscarPorCorreo(correoLimpio).isPresent()) {
            throw new ValidacionException("Ya existe una cuenta con ese correo. Ingrese con su contraseña.");
        }
        usuario.setClaveHash(codificador.encode(clave));
        return usuarioDao.insertar(usuario);
    }

    private void validarClaveNueva(String clave, String confirmacion, String correo) {
        politicaContrasena.validar(clave, correo);
        if (!Objects.equals(clave, confirmacion)) {
            throw new ValidacionException("Las dos contraseñas no son iguales. Escríbalas de nuevo.");
        }
    }
}
