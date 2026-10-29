package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.domain.TipoDocumento;
import co.edu.unicesar.marketradar.domain.Usuario;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import co.edu.unicesar.marketradar.validacion.ValidadorCampos;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Consulta y actualiza los datos de perfil de una persona, validando cada campo.
 */
@Service
public class ServicioPerfil {

    private final UsuarioDao usuarioDao;
    private final Clock reloj;

    public ServicioPerfil(UsuarioDao usuarioDao, Clock reloj) {
        this.usuarioDao = usuarioDao;
        this.reloj = reloj;
    }

    /**
     * @param usuarioId persona con sesión
     * @return su cuenta
     */
    public Usuario obtener(long usuarioId) {
        return usuarioDao.buscarPorId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos su cuenta."));
    }

    /**
     * Guarda el formulario de perfil. El correo y el rol no se cambian desde aquí.
     *
     * @param usuarioId persona con sesión
     * @param datos datos escritos en el formulario
     * @return cuenta actualizada
     */
    public Usuario actualizar(long usuarioId, DatosPerfil datos) {
        Usuario usuario = obtener(usuarioId);
        TipoDocumento tipo = ValidadorCampos.tipoDocumento(datos.tipoDocumento());
        String numero = ValidadorCampos.numeroDocumento(tipo, datos.numeroDocumento());
        if (tipo != null && usuarioDao.existeDocumento(tipo, numero, usuarioId)) {
            throw new ValidacionException("Ese documento ya está registrado en otra cuenta.");
        }
        usuario.setNombres(ValidadorCampos.nombre(datos.nombres(), "los nombres"));
        usuario.setApellidos(ValidadorCampos.nombre(datos.apellidos(), "los apellidos"));
        usuario.setTipoDocumento(tipo);
        usuario.setNumeroDocumento(numero);
        usuario.setFechaNacimiento(ValidadorCampos.fechaNacimiento(datos.fechaNacimiento(), reloj));
        usuario.setCelular(ValidadorCampos.celular(datos.celular()));
        usuario.setCiudad(ValidadorCampos.ciudad(datos.ciudad()));
        usuarioDao.actualizar(usuario);
        return usuario;
    }

    /**
     * Datos editables del perfil, tal como llegan del formulario.
     *
     * @param nombres nombres
     * @param apellidos apellidos
     * @param tipoDocumento CC, CE o PA; opcional
     * @param numeroDocumento número de documento; opcional
     * @param fechaNacimiento fecha de nacimiento; opcional
     * @param celular celular colombiano; opcional
     * @param ciudad ciudad de residencia; opcional
     */
    public record DatosPerfil(String nombres, String apellidos, String tipoDocumento, String numeroDocumento,
                              LocalDate fechaNacimiento, String celular, String ciudad) {
    }
}
