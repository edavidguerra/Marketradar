// Nombre del vendedor en las tarjetas: los marcadores técnicos del proveedor de datos no llegan a la pantalla, y los
// nombres legítimos con cifras o signos se muestran tal cual.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SIN_VENDEDOR, limpiarVendedor, tiendaDeOferta } from '../js/comun/formato.js';

const casos = [
    ['mc_9f3a77c1e2b44d0a8 · merchant_id=8841377', null],
    ['Alkosto · merchant_id=12345', 'Alkosto'],
    ['seller_id: 99812731', null],
    ['{{seller_name}}', null],
    ['undefined', null],
    ['[object Object]', null],
    ['https://www.google.com/shopping?srsltid=AfmBOox', null],
    ['Éxito', 'Éxito'],
    ['3M Colombia', '3M Colombia'],
    ['Black+Decker Store', 'Black+Decker Store'],
    ['D1', 'D1'],
    ['alkosto.com', 'alkosto.com'],
    ['Tienda (Bogotá)', 'Tienda (Bogotá)'],
    ['eBay - item_title_google_cloud_translation_test_for_us_es', 'eBay'],
    ['item_title_google_cloud_translation_test_for_us_es', null],
    ['tienda_la_14', 'tienda_la_14'],
    ['Mi_Tienda', 'Mi_Tienda'],
    ['seller_12345', null],
];

for (const [recibido, esperado] of casos) {
    test(`«${recibido}» se muestra como ${esperado ?? 'sin nombre'}`, () => {
        assert.equal(limpiarVendedor(recibido), esperado);
    });
}

test('Google Shopping sin un nombre fiable dice "Vendedor no identificado", nunca el marcador', () => {
    const oferta = { tienda: 'googleshopping', vendedor: 'mc_9f3a77c1e2b44d0a8 · merchant_id=8841377' };
    assert.equal(tiendaDeOferta(oferta), SIN_VENDEDOR);
    assert.equal(tiendaDeOferta({ tienda: 'googleshopping', vendedor: null }), SIN_VENDEDOR);
    assert.equal(tiendaDeOferta({ tienda: 'googleshopping', vendedor: 'Falabella' }), 'Falabella');
});

test('las demás tiendas conservan su nombre visible', () => {
    assert.equal(tiendaDeOferta({ tienda: 'mercadolibre', vendedor: 'merchant_id=1' }), 'Mercado Libre');
});
