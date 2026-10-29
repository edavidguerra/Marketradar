package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.dao.UsuarioDao;
import co.edu.unicesar.marketradar.domain.Rol;
import co.edu.unicesar.marketradar.domain.TipoDocumento;
import co.edu.unicesar.marketradar.domain.Usuario;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicioPerfilTest {

    private UsuarioDao usuarioDao;
    private ServicioPerfil servicio;

    @BeforeEach
    void preparar() {
        usuarioDao = mock(UsuarioDao.class);
        Usuario usuario = new Usuario("rosa@correo.com", "hash", Rol.USUARIO, "Rosa", "Díaz");
        usuario.setId(5L);
        when(usuarioDao.buscarPorId(5L)).thenReturn(Optional.of(usuario));
        servicio = new ServicioPerfil(usuarioDao,
                Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneId.of("America/Bogota")));
    }

    @Test
    void guardaElFormularioCompleto() {
        Usuario actualizado = servicio.actualizar(5L, new ServicioPerfil.DatosPerfil("Rosa Elena", "Díaz Mejía",
                "cc", "49.712.345", LocalDate.of(1958, 4, 2), "300 123 4567", "Valledupar"));

        assertEquals("Rosa Elena", actualizado.getNombres());
        assertEquals(TipoDocumento.CC, actualizado.getTipoDocumento());
        assertEquals("49712345", actualizado.getNumeroDocumento());
        assertEquals("3001234567", actualizado.getCelular());
        verify(usuarioDao).actualizar(actualizado);
    }

    @Test
    void rechazaDocumentoDeOtraCuenta() {
        when(usuarioDao.existeDocumento(TipoDocumento.CC, "49712345", 5L)).thenReturn(true);
        assertThrows(ValidacionException.class, () -> servicio.actualizar(5L, new ServicioPerfil.DatosPerfil(
                "Rosa", "Díaz", "CC", "49712345", null, null, null)));
        verify(usuarioDao, never()).actualizar(any());
    }

    @Test
    void rechazaMenoresDeEdad() {
        assertThrows(ValidacionException.class, () -> servicio.actualizar(5L, new ServicioPerfil.DatosPerfil(
                "Rosa", "Díaz", null, null, LocalDate.of(2012, 1, 1), null, null)));
    }
}
