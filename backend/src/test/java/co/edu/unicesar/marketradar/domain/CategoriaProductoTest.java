package co.edu.unicesar.marketradar.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoriaProductoTest {

    @Test
    void unForroParaCelularEsAccesorioNoCelular() {
        assertEquals(CategoriaProducto.ACCESORIO, CategoriaProducto.detectar("Forro para iPhone 15 Pro silicona"));
        assertEquals(CategoriaProducto.CELULAR, CategoriaProducto.detectar("Celular iPhone 15 Pro 128 GB"));
        assertFalse(CategoriaProducto.CELULAR.compatibleCon(CategoriaProducto.ACCESORIO));
    }

    @Test
    void tiposDistintosNoSonEquivalentes() {
        assertEquals(CategoriaProducto.TELEVISOR, CategoriaProducto.detectar("Televisor Samsung 55 pulgadas"));
        assertEquals(CategoriaProducto.LICUADORA, CategoriaProducto.detectar("Licuadora Oster 600 W"));
        assertFalse(CategoriaProducto.TELEVISOR.compatibleCon(CategoriaProducto.LICUADORA));
    }

    @Test
    void desconocidaSoloEsCompatibleConProductosNoAccesorios() {
        assertTrue(CategoriaProducto.DESCONOCIDA.compatibleCon(CategoriaProducto.CELULAR));
        assertFalse(CategoriaProducto.DESCONOCIDA.compatibleCon(CategoriaProducto.ACCESORIO));
    }
}
