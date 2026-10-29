package co.edu.unicesar.marketradar.dao;

import co.edu.unicesar.marketradar.dao.jdbc.BusquedaDaoJdbc;
import co.edu.unicesar.marketradar.dao.jdbc.PqrDaoJdbc;
import co.edu.unicesar.marketradar.dao.jdbc.UsuarioDaoJdbc;
import co.edu.unicesar.marketradar.domain.Busqueda;
import co.edu.unicesar.marketradar.domain.EstadoPqr;
import co.edu.unicesar.marketradar.domain.Pqr;
import co.edu.unicesar.marketradar.domain.Rol;
import co.edu.unicesar.marketradar.domain.TipoDocumento;
import co.edu.unicesar.marketradar.domain.TipoPqr;
import co.edu.unicesar.marketradar.domain.Usuario;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de integración de los DAO de usuarios, PQR e historial contra PostgreSQL 16 real (requiere Docker).
 */
@Testcontainers(disabledWithoutDocker = true)
class UsuarioPqrDaoJdbcTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static UsuarioDaoJdbc usuarioDao;
    private static PqrDaoJdbc pqrDao;
    private static BusquedaDaoJdbc busquedaDao;

    @BeforeAll
    static void preparar() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        usuarioDao = new UsuarioDaoJdbc(dataSource);
        pqrDao = new PqrDaoJdbc(dataSource);
        busquedaDao = new BusquedaDaoJdbc(dataSource);
    }

    @Test
    void guardaYActualizaElPerfil() {
        Usuario usuario = usuarioDao.insertar(new Usuario("rosa@correo.com", "hash", Rol.USUARIO, "Rosa", "Díaz"));

        usuario.setTipoDocumento(TipoDocumento.CC);
        usuario.setNumeroDocumento("49712345");
        usuario.setFechaNacimiento(LocalDate.of(1958, 4, 2));
        usuario.setCelular("3001234567");
        usuario.setCiudad("Valledupar");
        assertTrue(usuarioDao.actualizar(usuario));

        Usuario leido = usuarioDao.buscarPorCorreo("rosa@correo.com").orElseThrow();
        assertEquals("Rosa Díaz", leido.nombreCompleto());
        assertEquals(TipoDocumento.CC, leido.getTipoDocumento());
        assertEquals(LocalDate.of(1958, 4, 2), leido.getFechaNacimiento());
        assertTrue(usuarioDao.existeDocumento(TipoDocumento.CC, "49712345", null));
        assertFalse(usuarioDao.existeDocumento(TipoDocumento.CC, "49712345", usuario.getId()));
    }

    @Test
    void pqrTraeElNombreDeQuienLaRadica() {
        Usuario usuario = usuarioDao.insertar(new Usuario("jose@correo.com", "hash", Rol.USUARIO, "José", "Mejía"));
        Usuario admin = usuarioDao.insertar(new Usuario("admin@correo.com", "hash", Rol.ADMINISTRADOR, "Ana", "Ruiz"));
        Pqr pqr = pqrDao.insertar(new Pqr(usuario.getId(), TipoPqr.QUEJA, "Precio distinto",
                "El precio en la tienda era más alto que el que mostraba Marketradar."));

        Pqr pendiente = pqrDao.listarPorEstado(EstadoPqr.RADICADA).stream()
                .filter(p -> p.getId().equals(pqr.getId())).findFirst().orElseThrow();
        assertEquals("José Mejía", pendiente.getNombreUsuario());
        assertEquals("jose@correo.com", pendiente.getCorreoUsuario());

        pendiente.responder("Gracias, los precios pueden cambiar en la tienda.", admin.getId());
        assertTrue(pqrDao.actualizar(pendiente));
        List<Pqr> delUsuario = pqrDao.listarPorUsuario(usuario.getId());
        assertEquals(EstadoPqr.RESPONDIDA, delUsuario.get(0).getEstado());
        assertEquals(admin.getId(), delUsuario.get(0).getRespondidaPor());
    }

    @Test
    void historialDeBusquedasPorUsuario() {
        Usuario usuario = usuarioDao.insertar(new Usuario("luz@correo.com", "hash", Rol.USUARIO, "Luz", "Ortiz"));
        Busqueda primera = new Busqueda("tensiómetro", 4, 900);
        primera.setUsuarioId(usuario.getId());
        busquedaDao.insertar(primera);
        busquedaDao.insertar(new Busqueda("anónima", 1, 300));

        List<Busqueda> historial = busquedaDao.listarPorUsuario(usuario.getId(), 20);
        assertEquals(1, historial.size());
        assertEquals("tensiómetro", historial.get(0).getTermino());
        assertEquals(1, busquedaDao.eliminarPorUsuario(usuario.getId()));
    }
}
