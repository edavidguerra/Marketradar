// Interacción de Emprender con respuestas demoradas: se pinta el DOM de verdad (con un DOM simulado) y se comprueba el
// texto final de resumen, tarjetas y detalle. Reproduce el fallo "Cantidad: 100 cajas" con tarjetas de "100 pares".
import { test, before } from 'node:test';
import assert from 'node:assert/strict';
import { crearEvento, esperar, instalarDom, instalarServidor } from './dom-simulado.mjs';

const UNIDADES = [
    { codigo: 'PIEZA', singular: 'pieza', plural: 'piezas' },
    { codigo: 'PAR', singular: 'par', plural: 'pares' },
    { codigo: 'CAJA', singular: 'caja', plural: 'cajas' },
];

const elementos = instalarDom({
    'formulario-mayoristas': 'form',
    'termino-mayorista': 'input',
    'cantidad-mayorista': 'input',
    'unidad-mayorista': 'select',
    'error-mayorista': 'p',
    'cambios-mayoristas': 'p',
    'sin-configurar-mayoristas': 'p',
    'avisos-mayoristas': 'ul',
    'estado-mayoristas': 'div',
    'detalle-mayorista': 'section',
    'estado-detalle-mayorista': 'div',
    'lista-detalle-mayorista': 'ol',
    'titulo-detalle-mayorista': 'h2',
    'lista-mayoristas': 'ol',
    'titulo-dudosas-mayoristas': 'h2',
    'lista-dudosas-mayoristas': 'ol',
    'mas-mayoristas': 'button',
});
const $ = (id) => elementos.get(id);
const enviarBoton = document.createElement('button');
enviarBoton.type = 'submit';
$('formulario-mayoristas').append(enviarBoton);

const pendientes = instalarServidor();
const modulo = await import('../js/usuario/mayoristas.js');

const plural = (codigo) => UNIDADES.find((u) => u.codigo === codigo).plural;

/** Una oferta como la manda OfertaDto (snake_case), calculada con la cantidad y unidad que llegaron al servidor. */
function oferta(id, p, precio, cambios = {}) {
    const cantidad = Number(p.cantidad);
    return {
        id,
        titulo: `${p.q ?? 'detalle'} ${id}`,
        url: `https://www.alibaba.com/product-detail/${id}.html`,
        imagen: null,
        proveedor: { nombre: 'Proveedor', pais: 'CN', anios: 3, verificado: true },
        patrocinado: false,
        pedido_minimo: 10,
        pedido_minimo_texto: `10 ${plural(p.unidad)}`,
        unidad: p.unidad,
        presentacion: null,
        escalas: [{ minimo: 10, maximo: null, precio }],
        moneda: 'USD',
        variantes: [{ id: 'v1', nombre: 'Dorado', precio, disponible: true }],
        actualizado_en: null,
        calculo: {
            estado: 'APLICABLE', motivo: null, cantidad, unidad: p.unidad,
            unidad_texto: `${cantidad.toLocaleString('es-CO')} ${cantidad === 1 ? UNIDADES.find((u) => u.codigo === p.unidad).singular : plural(p.unidad)}`,
            escala: { minimo: 10, maximo: null, precio }, precio_unitario: precio,
            subtotal: Math.round(precio * cantidad * 100) / 100, ajustes: [], nota: 'Subtotal solo de la mercancía.',
        },
        posicion: 1,
        motivo_fuera: null,
        aproximado_pesos: null,
        relevancia: 'segura',
        empate: false,
        preliminar: false,
        ...cambios,
    };
}

/** Respuesta de GET /mayoristas/busqueda: dos ofertas por página, todas las páginas cargadas juntas. */
function busqueda(p, cambios = {}) {
    const pagina = Number(p.pagina);
    const ofertas = [];
    for (let i = 1; i <= pagina * 2; i += 1) ofertas.push(oferta(`o${i}`, p, 1 + i / 10, { posicion: i }));
    return {
        termino: p.q, pagina, siguiente_pagina: pagina < 3 ? pagina + 1 : null, cantidad: Number(p.cantidad),
        unidad: p.unidad, ofertas, descartados: 0, avisos: [], nota: 'Subtotal solo de la mercancía',
        comparables: ofertas.length, verificadas: ofertas.length, ganadora_id: 'o1', parcial: false, ...cambios,
    };
}

before(async () => {
    modulo.iniciarMayoristas();
    const carga = modulo.cargarMayoristas();
    await esperar();
    pendientes.shift().responder({ configurado: true, unidades: UNIDADES });
    await carga;
});

function cambiar(id, valor) {
    $(id).value = valor;
    $(id).dispatchEvent(crearEvento('input'));
    $(id).dispatchEvent(crearEvento('change'));
}

async function buscar(termino, cantidad, unidad) {
    $('termino-mayorista').value = termino;
    $('cantidad-mayorista').value = cantidad;
    $('unidad-mayorista').value = unidad;
    $('formulario-mayoristas').dispatchEvent(crearEvento('submit'));
    await esperar();
    return pendientes.at(-1);
}

const resumen = () => $('estado-mayoristas').textContent;
const tarjetas = (lista = 'lista-mayoristas') => $(lista).children.map((li) => li.textContent);

async function responder(solicitud, datos) {
    solicitud.responder(datos ?? busqueda(solicitud.parametros));
    await esperar();
}

test('cambiar de pares a cajas mientras carga "Ver más resultados" no cambia la unidad del resumen', async () => {
    const primera = await buscar('stainless steel earrings', '100', 'PAR');
    await responder(primera);
    assert.match(resumen(), /Cantidad: 100 pares\./);

    $('mas-mayoristas').click();
    await esperar();
    const segunda = pendientes.at(-1);
    assert.equal(segunda.parametros.pagina, '2');
    assert.equal(segunda.parametros.unidad, 'PAR', 'La página 2 se pide con la unidad de la búsqueda enviada');

    cambiar('unidad-mayorista', 'CAJA');
    await responder(segunda);

    assert.match(resumen(), /Cantidad: 100 pares\. Se cargaron las primeras 2 páginas: 4 ofertas distintas/);
    assert.doesNotMatch(resumen(), /cajas/);
    const textos = tarjetas();
    assert.equal(textos.length, 4);
    textos.forEach((texto) => assert.match(texto, /100 pares a US\$/));
    // El cálculo pintado es el de la respuesta: 100 × US$1,10 = US$110,00.
    assert.match(textos[0], /US\$\s?110,00/);
    assert.equal($('cambios-mayoristas').hidden, false, 'Avisa que hay cambios sin aplicar');
    assert.match($('cambios-mayoristas').textContent, /100 pares/);
});

test('cambiar cantidad y producto con la respuesta pendiente no reinterpreta los resultados', async () => {
    const solicitud = await buscar('cotton socks', '100', 'PAR');
    cambiar('cantidad-mayorista', '500');
    cambiar('termino-mayorista', 'leather shoes');
    await responder(solicitud);
    assert.match(resumen(), /Cantidad: 100 pares\./);
    assert.match(resumen(), /"cotton socks"/);
    assert.doesNotMatch(resumen(), /500|leather shoes/);
    tarjetas().forEach((texto) => assert.match(texto, /100 pares a/));
    assert.equal($('cambios-mayoristas').hidden, false);

    // Volver a los valores enviados quita el aviso.
    cambiar('cantidad-mayorista', '100');
    cambiar('termino-mayorista', 'cotton socks');
    assert.equal($('cambios-mayoristas').hidden, true);
});

test('una nueva búsqueda B invalida la respuesta tardía de A', async () => {
    const a = await buscar('cotton socks', '100', 'PAR');
    const b = await buscar('paper bags', '3', 'CAJA');
    await responder(b);
    await responder(a);
    assert.match(resumen(), /Cantidad: 3 cajas\./);
    assert.match(resumen(), /"paper bags"/);
    assert.doesNotMatch(resumen(), /socks|pares/);
    tarjetas().forEach((texto) => assert.match(texto, /3 cajas a/));
    assert.equal($('cambios-mayoristas').hidden, true, 'Lo que se ve es lo que está en el formulario');

    // Y si A responde primero, tampoco se pinta: B ya la reemplazó.
    const a2 = await buscar('cotton socks', '100', 'PAR');
    const b2 = await buscar('paper bags', '1', 'CAJA');
    await responder(a2);
    assert.doesNotMatch(resumen(), /Cantidad: 100 pares/);
    await responder(b2);
    assert.match(resumen(), /Cantidad: 1 caja\./, 'Una sola unidad va en singular, según el catálogo');
});

test('el detalle abierto después de editar el formulario usa la búsqueda enviada', async () => {
    const solicitud = await buscar('stainless steel earrings', '100', 'PAR');
    await responder(solicitud);
    cambiar('unidad-mayorista', 'CAJA');
    cambiar('cantidad-mayorista', '7');

    const verDetalle = $('lista-mayoristas').children[0].querySelectorAll('button')
        .find((boton) => boton.textContent === 'Ver variantes y detalle');
    verDetalle.click();
    await esperar();
    const ficha = pendientes.at(-1);
    assert.match(ficha.url.pathname, /mayoristas\/producto$/);
    assert.equal(ficha.parametros.cantidad, '100');
    assert.equal(ficha.parametros.unidad, 'PAR');
    await responder(ficha, oferta('o1', { ...ficha.parametros, q: 'stainless steel earrings' }, 1.1));
    assert.match($('lista-detalle-mayorista').textContent, /100 pares a US\$/);

    // La ficha recalcula la lista en silencio con la misma búsqueda enviada.
    const recarga = pendientes.at(-1);
    assert.match(recarga.url.pathname, /mayoristas\/busqueda$/);
    assert.equal(recarga.parametros.unidad, 'PAR');
    assert.equal(recarga.parametros.cantidad, '100');
    await responder(recarga);
    assert.match(resumen(), /Cantidad: 100 pares\./);
    assert.doesNotMatch(resumen(), /cajas|Cantidad: 7/);

    // Cambiar de variante tampoco toma la unidad del formulario.
    const variantes = $('lista-detalle-mayorista').querySelector('select');
    cambiar('unidad-mayorista', 'PIEZA');
    variantes.value = 'v1';
    variantes.dispatchEvent(crearEvento('change'));
    await esperar();
    assert.equal(pendientes.at(-1).parametros.unidad, 'PAR');
    assert.equal(pendientes.at(-1).parametros.variante, 'v1');
    await responder(pendientes.at(-1), oferta('o1', { ...pendientes.at(-1).parametros, q: 'x' }, 1.1));
    await responder(pendientes.at(-1));
    assert.match(resumen(), /Cantidad: 100 pares\./);
});

test('preliminar de US$40 y verificada de US$60: estrella solo para la verificada y sin decir que es la más barata de todas', async () => {
    const solicitud = await buscar('stainless steel earrings', '1', 'PIEZA');
    const p = solicitud.parametros;
    const preliminar = oferta('barata', p, 40, { posicion: 1, preliminar: true });
    const verificada = oferta('verificada', p, 60, { posicion: 2 });
    await responder(solicitud, busqueda(p, {
        ofertas: [preliminar, verificada], comparables: 2, verificadas: 1, ganadora_id: 'verificada', siguiente_pagina: null,
    }));
    const [primera, segunda] = $('lista-mayoristas').children;
    assert.match(primera.textContent, /Precio preliminar, pendiente de verificar/);
    assert.doesNotMatch(primera.textContent, /★/);
    assert.ok(!primera.className.includes('mejor'));
    assert.match(segunda.textContent, /★ Subtotal más bajo entre las ofertas verificadas/);
    assert.ok(segunda.className.includes('mejor'));
    assert.match(resumen(), /1 con precio verificado para su cantidad y 1 con precio preliminar/);
    assert.match(resumen(), /La estrella es la de menor subtotal entre las verificadas/);
    assert.match(resumen(), /una oferta con precio preliminar puede verse más barata/);
    assert.doesNotMatch(resumen(), /El más bajo es entre las ofertas cargadas/);

    // Sin verificadas no hay ganadora ni estrella.
    const sinVerificar = await buscar('stainless steel earrings', '2', 'PIEZA');
    const q = sinVerificar.parametros;
    await responder(sinVerificar, busqueda(q, {
        ofertas: [oferta('a', q, 40, { preliminar: true }), oferta('b', q, 60, { preliminar: true, posicion: 2 })],
        comparables: 2, verificadas: 0, ganadora_id: null, siguiente_pagina: null,
    }));
    assert.doesNotMatch($('lista-mayoristas').textContent, /★/);
    assert.match(resumen(), /Todavía no hay una oferta verificada para recomendar/);
    assert.doesNotMatch(resumen(), /La estrella es/);
});
