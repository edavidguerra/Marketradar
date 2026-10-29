package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.domain.Rol;
import co.edu.unicesar.marketradar.domain.Usuario;
import co.edu.unicesar.marketradar.exception.CredencialesInvalidasException;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import co.edu.unicesar.marketradar.seguridad.ProteccionIntentosIngreso;
import co.edu.unicesar.marketradar.validacion.PoliticaContrasenaSegura;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioCuentasTest {

    private static final String CLAVE = "Radar#2026";

    private UsuarioDao usuarioDao;
    private AuthenticationManager gestor;
    private final PasswordEncoder codificador = new BCryptPasswordEncoder(4);
    private ServicioCuentas servicio;

    @BeforeEach
    void preparar() {
        usuarioDao = mock(UsuarioDao.class);
        gestor = mock(AuthenticationManager.class);
        when(usuarioDao.buscarPorCorreo(any())).thenReturn(Optional.empty());
        when(usuarioDao.insertar(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        servicio = new ServicioCuentas(usuarioDao, codificador, new PoliticaContrasenaSegura(), gestor,
                new ProteccionIntentosIngreso());
    }

    @Test
    void registraUsuarioConClaveCifradaYRolUsuario() {
        servicio.registrar(" Carmen ", "Rojas", "Carmen@Correo.com", CLAVE, CLAVE);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioDao).insertar(guardado.capture());
        assertEquals("carmen@correo.com", guardado.getValue().getCorreo());
        assertEquals("Carmen", guardado.getValue().getNombres());
        assertEquals(Rol.USUARIO, guardado.getValue().getRol());
        assertNotEquals(CLAVE, guardado.getValue().getClaveHash());
        assertTrue(codificador.matches(CLAVE, guardado.getValue().getClaveHash()));
    }

    @Test
    void rechazaClaveDebil() {
        assertThrows(ValidacionException.class,
                () -> servicio.registrar("Carmen", "Rojas", "carmen@correo.com", "carmen123", "carmen123"));
        verify(usuarioDao, never()).insertar(any());
    }

    @Test
    void rechazaConfirmacionDistinta() {
        assertThrows(ValidacionException.class,
                () -> servicio.registrar("Carmen", "Rojas", "carmen@correo.com", CLAVE, "Radar#2027"));
    }

    @Test
    void rechazaCorreoRepetido() {
        when(usuarioDao.buscarPorCorreo("carmen@correo.com"))
                .thenReturn(Optional.of(new Usuario("carmen@correo.com", "x", Rol.USUARIO, "Carmen", "Rojas")));
        assertThrows(ValidacionException.class,
                () -> servicio.registrar("Carmen", "Rojas", "carmen@correo.com", CLAVE, CLAVE));
    }

    @Test
    void bloqueaDespuesDeCincoIntentosFallidos() {
        when(gestor.authenticate(any())).thenThrow(new BadCredentialsException("mal"));
        for (int i = 0; i < ProteccionIntentosIngreso.INTENTOS_MAXIMOS; i++) {
            CredencialesInvalidasException fallo = assertThrows(CredencialesInvalidasException.class,
                    () -> servicio.ingresar("carmen@correo.com", "otra"));
            assertFalse(fallo.isBloqueada());
        }
        CredencialesInvalidasException bloqueo = assertThrows(CredencialesInvalidasException.class,
                () -> servicio.ingresar("carmen@correo.com", CLAVE));
        assertTrue(bloqueo.isBloqueada());
        verify(gestor, times(ProteccionIntentosIngreso.INTENTOS_MAXIMOS)).authenticate(any());
    }

    @Test
    void creaElAdministradorSoloUnaVez() {
        assertTrue(servicio.crearAdministradorSiNoExiste("Ana", "Ruiz", "admin@correo.com", CLAVE));
        when(usuarioDao.buscarPorCorreo("admin@correo.com"))
                .thenReturn(Optional.of(new Usuario("admin@correo.com", "x", Rol.ADMINISTRADOR, "Ana", "Ruiz")));
        assertFalse(servicio.crearAdministradorSiNoExiste("Ana", "Ruiz", "admin@correo.com", CLAVE));
        verify(usuarioDao, times(1)).insertar(any());
    }
}
