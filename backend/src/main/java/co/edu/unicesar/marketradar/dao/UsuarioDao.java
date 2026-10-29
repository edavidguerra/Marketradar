package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.domain.TipoDocumento;
import co.edu.unicesar.marketradar.domain.Usuario;

import java.util.Optional;

/**
 * Acceso a la tabla {@code usuario}.
 */
public interface UsuarioDao extends Dao<Usuario> {

    /**
     * Busca una cuenta por su correo, que es el nombre de usuario.
     *
     * @param correo correo en minúsculas
     * @return la cuenta, o vacío si no existe
     */
    Optional<Usuario> buscarPorCorreo(String correo);

    /**
     * Indica si otra cuenta ya usa el documento.
     *
     * @param tipo tipo de documento
     * @param numero número de documento
     * @param excluirId cuenta que se está editando, o {@code null} al registrar
     * @return {@code true} si el documento pertenece a otra cuenta
     */
    boolean existeDocumento(TipoDocumento tipo, String numero, Long excluirId);

    /**
     * Cambia el hash de la contraseña de una cuenta.
     *
     * @param id cuenta
     * @param claveHash hash BCrypt nuevo
     * @return {@code true} si se actualizó
     */
    boolean actualizarClave(long id, String claveHash);
}
