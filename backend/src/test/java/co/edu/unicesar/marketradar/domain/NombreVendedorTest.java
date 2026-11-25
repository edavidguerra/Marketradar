package co.edu.unicesar.marketradar.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NombreVendedorTest {

    @ParameterizedTest(name = "«{0}» → «{1}»")
    @CsvSource(delimiter = '|', nullValues = "NULO", value = {
        "Éxito                                          | Éxito",
        "Mercado Libre - Tienda Oficial Samsung         | Mercado Libre - Tienda Oficial Samsung",
        "Black+Decker Store                             | Black+Decker Store",
        "3M Colombia                                    | 3M Colombia",
        "alkosto.com                                    | alkosto.com",
        "D1                                             | D1",
        "Alkosto · merchant_id=12345                    | Alkosto",
        "Falabella (seller_id: 99812731)                | Falabella",
        "mc_9f3a77c1e2b44d0a8 · merchant_id=8841377     | NULO",
        "{{seller_name}}                                | NULO",
        "${vendedor}                                    | NULO",
        "undefined                                      | NULO",
        "null                                           | NULO",
        "[object Object]                                | NULO",
        "srsltid=AfmBOoq3xX9fR2kLmN7pQ                  | NULO",
        "AfmBOoq3xX9fR2kLmN7pQwErTy                     | NULO",
        "8841377123                                     | NULO",
        "https://www.google.com/aclk?sa=l&ai=DChcSEw    | NULO",
        "                                               | NULO",
        "eBay - item_title_google_cloud_translation_test_for_us_es | eBay",
        "item_title_google_cloud_translation_test_for_us_es | NULO",
        "tienda_la_14                                   | tienda_la_14",
        "Mi_Tienda                                      | Mi_Tienda",
        "seller_12345                                   | NULO",
    })
    void soloSeConservaElNombreComercialFiable(String recibido, String esperado) {
        assertEquals(esperado, NombreVendedor.limpiar(recibido).orElse(null));
    }

    @org.junit.jupiter.api.Test
    void sinNombreFiableSeMuestraVendedorNoIdentificado() {
        assertEquals("Vendedor no identificado",
                NombreVendedor.paraMostrar("item_title_google_cloud_translation_test_for_us_es"));
        assertEquals("eBay", NombreVendedor.paraMostrar("eBay - item_title_google_cloud_translation_test_for_us_es"));
    }
}
