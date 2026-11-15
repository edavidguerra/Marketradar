package co.edu.unicesar.marketradar.domain.mayorista;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** "stainless steel earrings" ya no trae collares, pulseras, anillos ni piezas de otro material. */
class RelevanciaMayoristaTest {

    @ParameterizedTest(name = "{1} → {2}")
    @CsvSource(delimiter = '|', value = {
            // Lo buscado, con sinónimos, plural y recubrimientos que no son el material
            "stainless steel earrings | 316L Stainless Steel Hoop Earrings for Women | COINCIDE",
            "stainless steel earrings | Stainless Steel 18K Gold Plated Huggie Earrings | COINCIDE",
            "stainless steel earrings | Surgical steel ear studs hypoallergenic | COINCIDE",
            "stainless steel earrings | Stainless steel earring | COINCIDE",
            "aretes de acero inoxidable | Stainless Steel Stud Earrings Wholesale | COINCIDE",
            // Otro tipo de producto
            "stainless steel earrings | Stainless Steel Cuban Chain Necklace | INCOMPATIBLE",
            "stainless steel earrings | Stainless Steel Bangle Bracelet for Women | INCOMPATIBLE",
            "stainless steel earrings | Stainless Steel Ring Band | INCOMPATIBLE",
            "stainless steel earrings | Stainless steel nose ring piercing | INCOMPATIBLE",
            "aretes de acero inoxidable | Collar de acero inoxidable | INCOMPATIBLE",
            // Otro material
            "stainless steel earrings | 925 Sterling Silver Stud Earrings | INCOMPATIBLE",
            "stainless steel earrings | Brass Gold Plated Drop Earrings | INCOMPATIBLE",
            "stainless steel earrings | Zinc Alloy Rhinestone Earrings | INCOMPATIBLE",
            // Lo que el título no dice no se da por cumplido
            "stainless steel earrings | Fashion Pearl Drop Earrings for Women | DUDOSA",
            "stainless steel earrings | Titanium Steel Earrings | DUDOSA",
            "stainless steel earrings | Stainless Steel Jewelry Wholesale | DUDOSA",
            "stainless steel earrings | Stainless Steel Earrings Necklace Set | DUDOSA",
            // Sin tipo ni material reconocidos: basta la mitad de las palabras, con singular o plural
            "cotton socks | Cotton socks men | COINCIDE",
            "bluetooth adapter usb | USB Bluetooth adapter 5.0 | COINCIDE",
            "bluetooth adapter usb | Wireless mouse | DUDOSA"})
    void distingueTipoYMaterial(String termino, String titulo, RelevanciaMayorista.Nivel esperado) {
        assertEquals(esperado, RelevanciaMayorista.evaluar(termino, titulo).nivel(),
                () -> RelevanciaMayorista.evaluar(termino, titulo).motivo());
    }
}
