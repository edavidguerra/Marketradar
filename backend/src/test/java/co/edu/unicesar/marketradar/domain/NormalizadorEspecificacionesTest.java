package co.edu.unicesar.marketradar.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NormalizadorEspecificacionesTest {

    private static Map<String, Especificacion> porClave(String titulo, List<String> detalles) {
        return NormalizadorEspecificaciones.extraer(titulo, detalles).stream()
                .collect(Collectors.toMap(Especificacion::clave, e -> e));
    }

    @Test
    void escriturasDistintasQuedanEnLaMismaUnidad() {
        assertEquals("256 GB", porClave("Celular Samsung A55 256GB", List.of()).get("almacenamiento").valor());
        assertEquals("256 GB", porClave("Celular Samsung A55 256 gigas", List.of()).get("almacenamiento").valor());
        assertEquals("1 TB", porClave("Portátil Lenovo 1TB SSD 16GB RAM", List.of()).get("almacenamiento").valor());
        assertEquals("16 GB", porClave("Portátil Lenovo 1TB SSD 16GB RAM", List.of()).get("ram").valor());
    }

    @Test
    void lasPulgadasSeLeenSinConfundirConOtrasCosas() {
        assertEquals("55 pulgadas", porClave("Televisor LG 55\" 4K UHD", List.of()).get("pantalla").valor());
        assertEquals("55 pulgadas", porClave("Televisor LG 55 pulgadas 4K", List.of()).get("pantalla").valor());
        assertNull(porClave("Cargador 2 in 1 USB", List.of()).get("pantalla"));
    }

    @Test
    void loQueInformaLaTiendaMandaSobreElTitulo() {
        Map<String, Especificacion> specs = porClave("Celular Motorola 128GB", List.of("256 GB", "Color: Negro"));
        assertEquals("256 GB", specs.get("almacenamiento").valor());
        assertEquals(Especificacion.FUENTE_TIENDA, specs.get("almacenamiento").fuente());
    }

    @Test
    void sinDatoNoSeInventaNada() {
        Map<String, Especificacion> specs = porClave("Tensiómetro digital de brazo", List.of());
        assertNull(specs.get("almacenamiento"));
        assertNull(specs.get("ram"));
        assertNull(specs.get("pantalla"));
    }

    @Test
    void distingueLaVersionDelModelo() {
        String pro = NormalizadorEspecificaciones.claveVariante("Apple iPhone 15 Pro 128GB", List.of());
        String base = NormalizadorEspecificaciones.claveVariante("Apple iPhone 15 128GB", List.of());
        assertFalse(NormalizadorEspecificaciones.mismaVariante(pro, base));
        assertTrue(NormalizadorEspecificaciones.mismaVariante(base,
                NormalizadorEspecificaciones.claveVariante("Apple iPhone 15 128 GB", List.of())));
    }

    @Test
    void otraCapacidadEsOtraVariante() {
        String a = NormalizadorEspecificaciones.claveVariante("Samsung Galaxy A55 128GB", List.of());
        String b = NormalizadorEspecificaciones.claveVariante("Samsung Galaxy A55 256GB", List.of());
        assertFalse(NormalizadorEspecificaciones.mismaVariante(a, b));
    }
}
