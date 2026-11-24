// Respuestas de búsqueda en desorden: se pinta la sección Buscar de verdad (con un DOM simulado) y el servidor
// responde cuando la prueba lo decide. Una búsqueda vieja que responde o falla tarde no puede cambiar lo que se ve.
import { test, before, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { crearEvento, esperar, instalarDom, instalarServidor, registrarSelector } from './dom-simulado.mjs';

const elementos = instalarDom({
    'formulario-busqueda': 'form',
    'campo-busqueda': 'input',
    'error-busqueda': 'p',
    'lista-sugerencias': 'ul',
    'estado-sugerencias': 'p',
    'estado-resultados': 'div',
    'lista-resultados': 'ol',
    'ver-mas': 'button',
    'volver-resultados': 'button',
    advertencias: 'ul',
    'resumen-ia': 'section',
    'texto-resumen-ia': 'p',
    'producto-mvp': 'section',
    'requisitos-busqueda': 'div',
    'sin-recomendacion': 'p',
    alternativas: 'section',
    'ayuda-alternativas': 'p',
    'lista-alternativas': 'ol',
    quizas: 'p',
    'nota-totales': 'p',
    'filtro-precio': 'form',
    'precio-minimo': 'input',
    'precio-maximo': 'input',
    'error-precio': 'p',
    'quitar-filtro-precio': 'button',
    'contenido-detalle': 'div',
});
const $ = (id) => elementos.get(id);
const botonBuscar = document.createElement('button');
botonBuscar.type = 'submit';
$('formulario-busqueda').append(botonBuscar);
$('campo-busqueda').form = $('formulario-busqueda');
registrarSelector('#formulario-busqueda [type="submit"]', botonBuscar);

const pendientes = instalarServidor();
const busqueda = await import('../js/usuario/busqueda.js');

before(() => busqueda.iniciarBusqueda());
beforeEach(() => {
    pendientes.length = 0;
});

/** Respuesta como la manda RespuestaBusquedaDto (snake_case). */
function respuesta(termino, titulos, cambios = {}) {
    const ofertas = titulos.map((titulo, i) => ({
        id: `${termino}-${i}`, tienda: 'googleshopping', vendedor: 'Alkosto', titulo,
        precio: 1000000 + i, moneda: 'COP', url: `https://www.alkosto.com/${i}`, condicion: 'nuevo',
    }));
    return {
        termino, ofertas, total_resultados: ofertas.length, total_sin_filtro: ofertas.length, advertencias: [],
        producto_mvp: null, alternativas: [], requisitos: [], resumen_ia: null, motivo_sin_recomendacion: null,
        descartadas: 0, ...cambios,
    };
}

const titulosEnPantalla = () => $('lista-resultados').children
    .filter((tarjeta) => !tarjeta.className.includes('esqueleto'))
    .map((tarjeta) => tarjeta.querySelector('h2')?.textContent ?? tarjeta.querySelector('h3')?.textContent);

const buscarConFormulario = (texto) => {
    $('campo-busqueda').value = texto;
    $('formulario-busqueda').dispatchEvent(crearEvento('submit'));
};

test('si la búsqueda A responde después de la B, solo queda visible la B', async () => {
    buscarConFormulario('licuadora');
    buscarConFormulario('playstation 5');
    assert.equal(pendientes.length, 2);
    const [a, b] = pendientes;
    assert.equal(a.parametros.q, 'licuadora');
    assert.equal(b.parametros.q, 'playstation 5');
    assert.equal(a.senal?.aborted, true, 'la solicitud vieja se cancela');

    b.responder(respuesta('playstation 5', ['Consola PlayStation 5 Slim']));
    await esperar();
    a.responder(respuesta('licuadora', ['Licuadora Oster', 'Licuadora Imusa']));
    await esperar();

    assert.deepEqual(titulosEnPantalla(), ['Consola PlayStation 5 Slim']);
    assert.match($('estado-resultados').textContent, /Encontramos 1 resultado para "playstation 5"/);
    assert.equal($('lista-resultados').children.filter((t) => t.className.includes('esqueleto')).length, 0);
    assert.ok(!botonBuscar.classList.contains('buscando'));
});

test('si A falla después de que B respondió bien, B conserva su estado', async () => {
    buscarConFormulario('iphone 13');
    buscarConFormulario('televisor 55');
    const [a, b] = pendientes;

    b.responder(respuesta('televisor 55', ['Televisor Samsung 55 Pulgadas'], {
        producto_mvp: null, motivo_sin_recomendacion: 'Motivo de B',
    }));
    await esperar();
    a.responder({ mensaje_usuario: 'La búsqueda A falló' }, 500);
    await esperar();

    assert.deepEqual(titulosEnPantalla(), ['Televisor Samsung 55 Pulgadas']);
    assert.doesNotMatch($('estado-resultados').textContent, /falló/);
    assert.equal($('sin-recomendacion').textContent, 'Motivo de B');
    assert.ok(!botonBuscar.classList.contains('buscando'));
});

test('la limpieza de una búsqueda vieja no apaga el indicador de la búsqueda en curso', async () => {
    buscarConFormulario('licuadora');
    buscarConFormulario('nevera');
    const [a, b] = pendientes;

    a.fallar();
    await esperar();
    assert.ok(botonBuscar.classList.contains('buscando'), 'B sigue buscando');
    assert.match($('estado-resultados').textContent, /Buscando/);

    b.responder(respuesta('nevera', ['Nevera Haceb 250 L']));
    await esperar();
    assert.ok(!botonBuscar.classList.contains('buscando'));
    assert.deepEqual(titulosEnPantalla(), ['Nevera Haceb 250 L']);
});

test('cambiar el filtro de precio deja sin efecto la respuesta sin filtro que venía en camino', async () => {
    buscarConFormulario('licuadora');
    const [sinFiltro] = pendientes;
    $('precio-maximo').value = '200.000';
    $('filtro-precio').dispatchEvent(crearEvento('submit'));
    const conFiltro = pendientes[1];
    assert.equal(conFiltro.parametros.precio_maximo, '200000');

    conFiltro.responder(respuesta('licuadora', ['Licuadora Imusa'], { total_sin_filtro: 3 }));
    await esperar();
    sinFiltro.responder(respuesta('licuadora', ['Licuadora Oster', 'Licuadora Imusa', 'Licuadora Kalley']));
    await esperar();

    assert.deepEqual(titulosEnPantalla(), ['Licuadora Imusa']);
    assert.match($('estado-resultados').textContent, /1 de 3 resultados/);
    $('quitar-filtro-precio').click();
    pendientes.at(-1).responder(respuesta('licuadora', ['Licuadora Oster']));
    await esperar();
});

test('dos clics seguidos en "Ver más" piden la página siguiente una sola vez', async () => {
    buscarConFormulario('audifonos');
    pendientes[0].responder(respuesta('audifonos', Array.from({ length: 10 }, (_, i) => `Audífonos ${i}`),
        { total_resultados: 25, total_sin_filtro: 25 }));
    await esperar();
    assert.equal($('ver-mas').hidden, false);

    $('ver-mas').click();
    $('ver-mas').click();

    assert.equal(pendientes.length, 2);
    assert.equal(pendientes[1].parametros.pagina, '2');
    pendientes[1].responder(respuesta('audifonos', ['Audífonos 10'], { total_resultados: 25, total_sin_filtro: 25 }));
    await esperar();
    assert.equal(titulosEnPantalla().length, 11);
});

test('sin coincidencias se dice que no hay y por qué, sin inventar resultados', async () => {
    buscarConFormulario('playstation 5');
    pendientes[0].responder(respuesta('playstation 5', [], {
        descartadas: 2,
        motivo_sin_recomendacion: 'Las tiendas mostraron 2 ofertas, pero ninguna es lo que buscó.',
    }));
    await esperar();

    assert.deepEqual(titulosEnPantalla(), []);
    assert.match($('estado-resultados').textContent, /No encontramos coincidencias con "playstation 5"/);
    assert.match($('estado-resultados').textContent, /2 ofertas, pero eran otros productos/);
});

test('el vendedor con marcador técnico nunca se muestra en la tarjeta', async () => {
    buscarConFormulario('consola retro');
    const datos = respuesta('consola retro', ['Consola Retro Q21']);
    datos.ofertas[0].vendedor = 'mc_9f3a77c1e2b44d0a8 · merchant_id=8841377';
    pendientes[0].responder(datos);
    await esperar();

    const texto = $('lista-resultados').textContent;
    assert.doesNotMatch(texto, /merchant_id|mc_9f3a/);
    assert.match(texto, /Vendedor no identificado/);
});

test('solicitudVigente compara la generación de la solicitud con la actual', () => {
    assert.equal(busqueda.solicitudVigente({ generacion: 3 }, 3), true);
    assert.equal(busqueda.solicitudVigente({ generacion: 2 }, 3), false);
});
