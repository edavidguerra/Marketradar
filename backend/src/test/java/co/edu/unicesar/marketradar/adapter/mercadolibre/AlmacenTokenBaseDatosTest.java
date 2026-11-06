package co.edu.unicesar.marketradar.adapter.mercadolibre;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlmacenTokenBaseDatosTest {

    private final CredencialesEnMemoria dao = new CredencialesEnMemoria();
    private final AlmacenTokenBaseDatos almacen = new AlmacenTokenBaseDatos(dao);

    @Test
    void devuelveElTokenRenovadoSiPartioDelMismoTokenConfigurado() {
        almacen.guardar("TG-renovado", "TG-inicial");

        assertEquals(Optional.of("TG-renovado"), almacen.leer("TG-inicial"));
        assertEquals(64, dao.datos.get(AlmacenTokenBaseDatos.CODIGO).valorInicialHash().length());
    }

    @Test
    void siCambiaronElTokenConfiguradoSeIgnoraElGuardado() {
        almacen.guardar("TG-renovado", "TG-viejo");

        assertTrue(almacen.leer("TG-nuevo-de-la-variable").isEmpty());
    }

    @Test
    void sinNadaGuardadoDevuelveVacio() {
        assertTrue(almacen.leer("TG-inicial").isEmpty());
    }

    @Test
    void siLaBaseDeDatosFallaNoRompeLaBusqueda() {
        dao.falla = true;

        assertTrue(almacen.leer("TG-inicial").isEmpty());
        almacen.guardar("TG-renovado", "TG-inicial");
    }

    @Test
    void elHashEsElSha256EnHexadecimal() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                AlmacenTokenBaseDatos.sha256(""));
    }
}
