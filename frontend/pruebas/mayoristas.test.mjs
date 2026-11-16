// Etiquetas, contadores y protección contra respuestas viejas de Emprender (sin navegador).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { detalleVigente, etiquetaPuesto, textoContadores } from '../js/usuario/mayoristas.js';

const oferta = (cambios) => ({ id: '1600000000044', posicion: 1, preliminar: false, empate: false, ...cambios });

test('una oferta con precio preliminar nunca se anuncia como ganadora', () => {
    const etiqueta = etiquetaPuesto(oferta({ preliminar: true }), '1600000000044');
    assert.equal(etiqueta.texto, 'Precio preliminar, pendiente de verificar');
    assert.equal(etiqueta.clase, 'etiqueta-preliminar');
});

test('solo la ganadora verificada lleva la estrella', () => {
    assert.match(etiquetaPuesto(oferta(), '1600000000044').texto, /^★ Subtotal verificado más bajo/);
    assert.equal(etiquetaPuesto(oferta({ id: 'otra', posicion: 2 }), '1600000000044').texto,
        'Puesto 2 por subtotal, precio verificado');
    assert.equal(etiquetaPuesto(oferta(), null).clase, 'ayuda', 'Sin ganadora verificada no hay estrella');
    assert.equal(etiquetaPuesto(oferta({ posicion: null }), null), null);
});

test('los contadores separan verificadas y preliminares', () => {
    assert.equal(textoContadores({ comparables: 5, verificadas: 2, ganadora_id: 'x' }),
        '2 con precio verificado para su cantidad y 3 con precio preliminar, pendiente de verificar, ordenadas de menor a mayor subtotal.');
    assert.match(textoContadores({ comparables: 3, verificadas: 0, ganadora_id: null }),
        /Todavía no hay una oferta verificada para recomendar/);
});

test('una respuesta de detalle vieja (A) no se aplica después de pedir B', () => {
    const a = { numero: 1, busqueda: 'socks|100|PAR' };
    const b = { numero: 2, busqueda: 'socks|100|PAR' };
    const actual = { numero: 2, busqueda: 'socks|100|PAR' };
    assert.equal(detalleVigente(b, actual), true);
    assert.equal(detalleVigente(a, actual), false, 'A llega tarde: se ignora, sea éxito o error');
    assert.equal(detalleVigente(b, { numero: 2, busqueda: 'shoes|100|PAR' }), false, 'Cambió la búsqueda');
});
