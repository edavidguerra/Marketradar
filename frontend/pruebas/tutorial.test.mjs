import { test } from 'node:test';
import assert from 'node:assert/strict';
import { textoIrALaTienda } from '../js/usuario/tutorial.js';

test('"Ir a la tienda" dice a dónde lleva el botón de verdad', () => {
    assert.match(textoIrALaTienda('directo'), /abre la publicación de esta oferta en la tienda/);
    assert.match(textoIrALaTienda('redireccion'), /abre la publicación/);
    assert.match(textoIrALaTienda('pagina_google'), /Google Shopping/);
    assert.match(textoIrALaTienda('busqueda_google'), /no abre la tienda directamente: busca la oferta en Google/);
});

test('sin resultados cargados explica los dos tipos de botón', () => {
    const texto = textoIrALaTienda(undefined);
    assert.match(texto, /abren directamente la publicación/);
    assert.match(texto, /buscan la oferta en Google/);
});
