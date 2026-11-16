// Pruebas de la lectura de seguimientos con respuestas en el formato real de SeguimientoDto (minúsculas).
// Se corren con: node --test frontend/pruebas/
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
    alternarEstado, leerSeguimiento, normalizarEstado, normalizarTipo,
} from '../js/usuario/estado-seguimiento.js';

/** Como lo manda GET /api/v1/usuario/seguimientos (SeguimientoDto.desde). */
const dto = (cambios = {}) => ({
    oferta_id: 42,
    titulo: 'Tensiómetro Omron',
    tienda: 'googleshopping',
    vendedor: 'Éxito',
    precio_guardado: 520000,
    moneda: 'COP',
    tipo_aviso: 'baja_precio',
    precio_objetivo: null,
    estado: 'activo',
    resultado: 'sin_comprobar',
    avisos: [],
    ...cambios,
});

test('un seguimiento activo dice "Activo" y ofrece "Pausar avisos"', () => {
    const leido = leerSeguimiento(dto());
    assert.equal(leido.estado, 'ACTIVO');
    assert.equal(leido.etiqueta, 'Activo');
    assert.equal(leido.accion, 'Pausar avisos');
    assert.equal(leido.siguiente, 'PAUSADO');
});

test('un seguimiento pausado dice "En pausa" y ofrece "Reanudar avisos"', () => {
    const leido = leerSeguimiento(dto({ estado: 'pausado' }));
    assert.equal(leido.etiqueta, 'En pausa');
    assert.equal(leido.accion, 'Reanudar avisos');
    assert.equal(leido.siguiente, 'ACTIVO');
    assert.equal(leido.clase, 'estado-pausado');
});

test('con precio objetivo muestra el importe y la moneda', () => {
    const leido = leerSeguimiento(dto({ tipo_aviso: 'precio_objetivo', precio_objetivo: 450000 }));
    assert.equal(leido.tipo, 'PRECIO_OBJETIVO');
    assert.equal(leido.textoAviso.replace(/\s/g, ' '), 'Le avisamos cuando cueste $ 450.000 o menos.');
    const dolares = leerSeguimiento(dto({ tipo_aviso: 'precio_objetivo', precio_objetivo: 12.5, moneda: 'USD' }));
    assert.match(dolares.textoAviso, /US\$\s?12,50 o menos/);
});

test('la baja de precio usa el mensaje general', () => {
    assert.equal(leerSeguimiento(dto()).textoAviso, 'Le avisamos cuando baje de precio.');
});

test('un estado desconocido no se toma como activo ni como pausado', () => {
    for (const estado of ['cancelado', 'raro', '', null, undefined, 3]) {
        const leido = leerSeguimiento(dto({ estado }));
        assert.equal(leido.estado, null, String(estado));
        assert.equal(leido.accion, null, 'Sin botón de pausar ni de reanudar');
        assert.equal(leido.siguiente, null);
    }
    assert.equal(normalizarEstado(' ACTIVO '), 'ACTIVO');
    assert.equal(normalizarTipo('Precio_Objetivo'), 'PRECIO_OBJETIVO');
    assert.equal(normalizarTipo('otro'), null);
});

test('pausar manda PAUSADO, deshabilita el botón mientras espera y usa el seguimiento confirmado', async () => {
    const boton = { disabled: false };
    const llamadas = [];
    let deshabilitadoDurante = null;
    const cambiar = async (ofertaId, cambios) => {
        llamadas.push([ofertaId, cambios]);
        deshabilitadoDurante = boton.disabled;
        return dto({ estado: 'pausado' });
    };
    const resultado = await alternarEstado(dto(), cambiar, boton);
    assert.deepEqual(llamadas, [[42, { estado: 'PAUSADO' }]]);
    assert.equal(deshabilitadoDurante, true);
    assert.equal(boton.disabled, false);
    assert.equal(resultado.ok, true);
    assert.equal(leerSeguimiento(resultado.seguimiento).etiqueta, 'En pausa', 'Lo que se pinta es lo que confirmó la API');
});

test('reanudar manda ACTIVO', async () => {
    const llamadas = [];
    await alternarEstado(dto({ estado: 'pausado' }), async (id, cambios) => {
        llamadas.push(cambios);
        return dto({ estado: 'activo' });
    }, null);
    assert.deepEqual(llamadas, [{ estado: 'ACTIVO' }]);
});

test('si la API falla, el estado no cambia y el error se puede mostrar', async () => {
    const boton = { disabled: false };
    const resultado = await alternarEstado(dto(), async () => { throw new Error('Sin conexión.'); }, boton);
    assert.equal(resultado.ok, false);
    assert.equal(resultado.error.message, 'Sin conexión.');
    assert.equal(boton.disabled, false, 'Se puede volver a intentar');
});

test('si la API responde otro estado, no se da por hecho el cambio', async () => {
    const resultado = await alternarEstado(dto(), async () => dto({ estado: 'activo' }), null);
    assert.equal(resultado.ok, false);
});

test('con un estado desconocido no se llama a la API', async () => {
    let llamado = false;
    const resultado = await alternarEstado(dto({ estado: 'raro' }), async () => { llamado = true; }, null);
    assert.equal(resultado.ok, false);
    assert.equal(llamado, false);
});
