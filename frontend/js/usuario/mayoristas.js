/**
 * Sección "Emprender" (compras al por mayor): busca en Alibaba (a través del servidor, que guarda la clave de API.SH) y calcula el
 * subtotal de la mercancía para la cantidad pedida, con el pedido mínimo y las escalas de precio del proveedor.
 * Nunca muestra envío, impuestos ni fechas de entrega: no se conocen. La compra se hace en Alibaba.
 */

import { buscarMayoristas, obtenerDetalleMayorista, obtenerEstadoMayoristas } from '../comun/api.js';
import { boton, crear, crearIcono, enlaceTienda, mostrarEstado } from '../comun/dom.js';
import { dinero, fechaHora, pesos } from '../comun/formato.js';

const $ = (id) => document.getElementById(id);
const estado = {
    termino: '', cantidad: 0, unidad: 'PIEZA', pagina: 1, siguiente: null, configurado: null, variante: null,
    /** Variante elegida por oferta (id → variante): la lista y el detalle calculan con la misma. */
    variantes: new Map(),
    /** Número de la última solicitud: las respuestas de solicitudes anteriores se ignoran. */
    solicitud: 0,
    cargando: false,
};

export const CANTIDAD_MAXIMA = 10_000_000;

/**
 * Lee la cantidad con las mismas reglas que el servidor (ControladorMayoristas.cantidad): un entero entre 1 y
 * 10.000.000. Se aceptan los miles con punto solo en grupos completos de tres ("10.000"); "1.5", "1,5", "1 5",
 * negativos y decimales se rechazan en vez de leerse como otra cantidad.
 *
 * @returns {{ valor: number|null, error: string|null }}
 */
export function interpretarCantidad(texto) {
    const limpio = String(texto ?? '').trim();
    if (!limpio) return { valor: null, error: 'Escriba la cantidad que quiere comprar.' };
    if (/^-\s*[\d.,]+$/.test(limpio)) return { valor: null, error: 'La cantidad debe estar entre 1 y 10.000.000.' };
    if (!/^(\d{1,8}|\d{1,3}(\.\d{3}){1,2})$/.test(limpio)) {
        return {
            valor: null,
            error: 'Escriba la cantidad con números enteros, sin decimales (por ejemplo 500 o 10.000). Los miles van con punto.',
        };
    }
    const valor = Number(limpio.replace(/\./g, ''));
    if (valor < 1 || valor > CANTIDAD_MAXIMA) {
        return { valor: null, error: 'La cantidad debe estar entre 1 y 10.000.000.' };
    }
    return { valor, error: null };
}

/** Lee la cantidad: el número, o NaN si no es válida. */
export function leerCantidad(texto) {
    const { valor } = interpretarCantidad(texto);
    return valor == null ? NaN : valor;
}

const textoCantidad = (cantidad) => cantidad.toLocaleString('es-CO');

function crearEscalas(oferta, calculo) {
    if (!oferta.escalas.length) return crear('p', 'ayuda', 'Escalas de precio: No disponible');
    const tabla = crear('table', 'escalas');
    tabla.append(crear('caption', null, 'Precio por cantidad'));
    const cabecera = crear('tr');
    ['Cantidad', 'Precio por unidad'].forEach((texto) => {
        const th = crear('th', null, texto);
        th.scope = 'col';
        cabecera.append(th);
    });
    const thead = crear('thead');
    thead.append(cabecera);
    const tbody = crear('tbody');
    oferta.escalas.forEach((escala) => {
        const actual = calculo.escala && calculo.escala.minimo === escala.minimo;
        const fila = crear('tr', actual ? 'escala-actual' : null);
        const hasta = escala.maximo == null ? ' o más' : ` a ${escala.maximo.toLocaleString('es-CO')}`;
        fila.append(crear('td', null, `${escala.minimo.toLocaleString('es-CO')}${hasta}${actual ? ' (su pedido)' : ''}`),
            crear('td', null, dinero(escala.precio, oferta.moneda)));
        tbody.append(fila);
    });
    tabla.append(thead, tbody);
    return tabla;
}

function crearCalculo(oferta) {
    const { calculo } = oferta;
    const caja = crear('div', `calculo-mayorista${calculo.estado === 'APLICABLE' ? '' : ' sin-precio'}`);
    if (calculo.estado === 'APLICABLE') {
        caja.append(crear('p', 'precio', dinero(calculo.subtotal, oferta.moneda)));
        caja.append(crear('p', null, `${calculo.unidad_texto} a ${dinero(calculo.precio_unitario, oferta.moneda)} c/u.`));
        if (oferta.aproximado_pesos) {
            const p = oferta.aproximado_pesos;
            caja.append(crear('p', 'ayuda', `Aproximadamente ${pesos(p.valor)} con ${pesos(p.tasa)} por `
                + `${oferta.moneda === 'USD' ? 'dólar' : oferta.moneda} (${p.fuente}). El cobro real lo hace Alibaba.`));
        }
    } else {
        const alerta = crear('p', 'alerta-precio');
        alerta.append(crearIcono('escudo'), ` ${calculo.motivo}`);
        caja.append(alerta);
    }
    calculo.ajustes.forEach((ajuste) => caja.append(crear('p', 'ayuda', ajuste)));
    caja.append(crear('p', 'nota-subtotal', calculo.nota));
    return caja;
}

function crearProveedor(proveedor) {
    const partes = [proveedor.nombre || 'Proveedor: No disponible'];
    if (proveedor.pais) partes.push(proveedor.pais);
    if (proveedor.anios != null) partes.push(`${proveedor.anios} ${proveedor.anios === 1 ? 'año' : 'años'} en Alibaba`);
    if (proveedor.verificado) partes.push('verificado por Alibaba');
    return partes.join(' · ');
}

function crearOferta(oferta, { conVariantes = false } = {}) {
    const item = crear('li', `tarjeta mayorista${oferta.posicion === 1 ? ' mejor' : ''}${oferta.motivo_fuera ? ' fuera-orden' : ''}`);
    const imagen = crear('div', 'tarjeta-imagen');
    if (oferta.imagen) {
        const foto = document.createElement('img');
        foto.src = oferta.imagen;
        foto.alt = oferta.titulo;
        foto.loading = 'lazy';
        imagen.append(foto);
    } else {
        imagen.append(crearIcono('caja'));
    }
    const cuerpo = crear('div', 'tarjeta-cuerpo');
    if (oferta.patrocinado) cuerpo.append(crear('p', 'etiqueta-anuncio', 'Anuncio pagado'));
    if (oferta.posicion === 1 && !oferta.empate) {
        cuerpo.append(crear('p', 'etiqueta-mejor', '★ Subtotal más bajo entre las ofertas cargadas'));
    } else if (oferta.posicion === 1) {
        cuerpo.append(crear('p', 'etiqueta-mejor', '★ Empate en el subtotal más bajo entre las ofertas cargadas'));
    } else if (oferta.posicion) {
        cuerpo.append(crear('p', 'ayuda', `Puesto ${oferta.posicion} por subtotal${oferta.empate ? ' (empate)' : ''}`));
    }
    if (oferta.relevancia === 'dudosa' && oferta.motivo_relevancia) {
        cuerpo.append(crear('p', 'ayuda', `Coincidencia dudosa: ${oferta.motivo_relevancia}`));
    }
    if (oferta.motivo_fuera) cuerpo.append(crear('p', 'ayuda', `Fuera del orden: ${oferta.motivo_fuera}`));
    cuerpo.append(crear('h2', null, oferta.titulo), crearCalculo(oferta));
    const datos = crear('ul', 'datos');
    const dato = (icono, texto) => {
        const li = crear('li', 'dato');
        li.append(crearIcono(icono), texto);
        return li;
    };
    datos.append(
        dato('tienda', crearProveedor(oferta.proveedor)),
        dato('caja', `Pedido mínimo: ${oferta.pedido_minimo_texto || 'No disponible'}`),
        dato('etiqueta', `Moneda: ${oferta.moneda || 'No disponible'}`),
    );
    if (oferta.presentacion) datos.append(dato('caja', oferta.presentacion));
    cuerpo.append(datos, crearEscalas(oferta, oferta.calculo));
    if (conVariantes) cuerpo.append(crearVariantes(oferta));
    cuerpo.append(crear('p', 'ayuda', `Datos de Alibaba${oferta.actualizado_en ? ` consultados el ${fechaHora(oferta.actualizado_en)}` : ''}`
        + ' · Envío, impuestos, disponibilidad para Colombia y fecha de entrega: pendientes, consúltelos con el proveedor.'));
    const acciones = crear('div', 'acciones');
    acciones.append(enlaceTienda('Ver en Alibaba', oferta.url, 'Alibaba', 'boton boton-principal'));
    if (!conVariantes) {
        acciones.append(boton('Ver variantes y detalle', 'boton-secundario',
            () => verDetalle(oferta.url, estado.variantes.get(oferta.id) ?? null, oferta.id)));
    }
    cuerpo.append(acciones);
    item.append(imagen, cuerpo);
    return item;
}

function crearVariantes(oferta) {
    const caja = crear('div', 'variantes');
    if (!oferta.variantes.length) {
        caja.append(crear('p', 'ayuda', 'Variantes: el proveedor no publica variantes con precio propio.'));
        return caja;
    }
    const etiqueta = crear('label', null, 'Variante');
    etiqueta.htmlFor = 'variante-mayorista';
    const lista = crear('select');
    lista.id = 'variante-mayorista';
    lista.append(new Option('Sin elegir', ''));
    oferta.variantes.forEach((variante) => {
        const texto = `${variante.nombre || variante.id}${variante.precio != null ? ` · ${dinero(variante.precio, oferta.moneda)}` : ''}`
            + `${variante.disponible === false ? ' (no disponible)' : ''}`;
        lista.append(new Option(texto, variante.id));
    });
    lista.value = estado.variante || '';
    lista.addEventListener('change', () => verDetalle(oferta.url, lista.value || null, oferta.id));
    caja.append(etiqueta, lista);
    return caja;
}

async function verDetalle(url, variante = null, id = null) {
    estado.variante = variante;
    const clave = claveBusqueda();
    const zona = $('detalle-mayorista');
    zona.hidden = false;
    mostrarEstado($('estado-detalle-mayorista'), 'Consultando la ficha del producto en Alibaba…');
    try {
        const oferta = await obtenerDetalleMayorista(url, estado.cantidad, estado.unidad, variante);
        if (clave !== claveBusqueda()) return; // La persona cambió la búsqueda mientras tanto.
        $('estado-detalle-mayorista').replaceChildren();
        const lista = $('lista-detalle-mayorista');
        lista.replaceChildren(crearOferta(oferta, { conVariantes: true }));
        if (!variante) $('titulo-detalle-mayorista').focus();
        const ofertaId = id ?? oferta.id;
        if (variante) estado.variantes.set(ofertaId, variante);
        else estado.variantes.delete(ofertaId);
        // La ficha puede cambiar el precio de la tarjeta y su lugar: se recalcula la lista con lo ya cargado (sale de la
        // caché del servidor, no gasta consultas).
        if (estado.termino) cargar(estado.pagina, { silencioso: true });
    } catch (error) {
        mostrarEstado($('estado-detalle-mayorista'), error.message, () => verDetalle(url, variante, id));
    }
}

const claveBusqueda = () => `${estado.termino}|${estado.cantidad}|${estado.unidad}`;

/**
 * Carga las páginas 1 a {@code hasta} en una sola respuesta: el servidor las junta, quita repetidos y ordena todas
 * las ofertas cargadas. Si llega una respuesta de una solicitud anterior (otra búsqueda, otra cantidad), se ignora.
 */
async function cargar(hasta = 1, { silencioso = false } = {}) {
    const zona = $('estado-mayoristas');
    const numero = ++estado.solicitud;
    const clave = claveBusqueda();
    estado.cargando = true;
    const mas = $('mas-mayoristas');
    mas.disabled = true;
    if (!silencioso) mostrarEstado(zona, 'Buscando en Alibaba, puede tardar hasta un minuto…');
    try {
        const variantes = [...estado.variantes].map(([id, variante]) => `${id}:${variante}`).join(',');
        const respuesta = await buscarMayoristas(estado.termino, estado.cantidad, estado.unidad, hasta, variantes);
        if (numero !== estado.solicitud || clave !== claveBusqueda()) return;
        estado.pagina = respuesta.pagina;
        const avisos = $('avisos-mayoristas');
        avisos.replaceChildren(...respuesta.avisos.map((texto) => crear('li', null, texto)));
        avisos.hidden = respuesta.avisos.length === 0;
        const principales = respuesta.ofertas.filter((oferta) => oferta.relevancia !== 'dudosa');
        const dudosas = respuesta.ofertas.filter((oferta) => oferta.relevancia === 'dudosa');
        $('lista-mayoristas').replaceChildren(...principales.map((oferta) => crearOferta(oferta)));
        $('lista-dudosas-mayoristas').replaceChildren(...dudosas.map((oferta) => crearOferta(oferta)));
        $('lista-dudosas-mayoristas').hidden = dudosas.length === 0;
        $('titulo-dudosas-mayoristas').hidden = dudosas.length === 0;
        const unidad = $('unidad-mayorista').selectedOptions[0]?.textContent || estado.unidad.toLowerCase();
        const paginas = respuesta.pagina === 1 ? 'Se cargó la primera página'
            : `Se cargaron las primeras ${respuesta.pagina} páginas`;
        mostrarEstado(zona, respuesta.ofertas.length === 0
            ? `No encontramos ofertas al por mayor para "${respuesta.termino}". Pruebe con palabras en inglés.`
            : `Cantidad: ${textoCantidad(respuesta.cantidad)} ${unidad}. ${paginas}: ${respuesta.ofertas.length} `
              + `ofertas distintas para "${respuesta.termino}"; ${respuesta.comparables} con precio claro para su `
              + 'cantidad, ordenadas de menor a mayor subtotal. El más bajo es entre las ofertas cargadas, no entre todo '
              + `Alibaba. ${respuesta.nota}.`);
        estado.siguiente = respuesta.siguiente_pagina;
        mas.hidden = !estado.siguiente;
    } catch (error) {
        if (numero !== estado.solicitud) return;
        mostrarEstado(zona, error.message, () => cargar(hasta));
    } finally {
        if (numero === estado.solicitud) {
            estado.cargando = false;
            mas.disabled = false;
        }
    }
}

function enviar(evento) {
    evento.preventDefault();
    const termino = $('termino-mayorista').value.trim();
    const lectura = interpretarCantidad($('cantidad-mayorista').value);
    const cantidad = lectura.valor;
    const error = $('error-mayorista');
    let mensaje = '';
    if (termino.length < 2 || termino.length > 100) mensaje = 'Escriba qué producto busca (entre 2 y 100 letras).';
    else if (lectura.error) mensaje = lectura.error;
    error.textContent = mensaje;
    error.hidden = !mensaje;
    if (mensaje) {
        (termino.length < 2 || termino.length > 100 ? $('termino-mayorista') : $('cantidad-mayorista')).focus();
        return;
    }
    Object.assign(estado, {
        termino, cantidad, unidad: $('unidad-mayorista').value, pagina: 1, siguiente: null, variante: null,
    });
    estado.variantes.clear();
    $('lista-mayoristas').replaceChildren();
    $('lista-dudosas-mayoristas').replaceChildren();
    $('lista-dudosas-mayoristas').hidden = true;
    $('titulo-dudosas-mayoristas').hidden = true;
    $('detalle-mayorista').hidden = true;
    cargar(1);
}

/** Al mostrar la sección: dice si el servidor tiene la clave de API.SH y llena las unidades. */
export async function cargarMayoristas() {
    if (estado.configurado !== null) return;
    try {
        const info = await obtenerEstadoMayoristas();
        estado.configurado = info.configurado;
        const lista = $('unidad-mayorista');
        lista.replaceChildren(...info.unidades.map((unidad) => new Option(unidad.plural, unidad.codigo)));
        lista.value = 'PIEZA';
        $('sin-configurar-mayoristas').hidden = info.configurado;
        $('formulario-mayoristas').querySelector('[type="submit"]').disabled = !info.configurado;
    } catch (error) {
        mostrarEstado($('estado-mayoristas'), error.message);
    }
}

export function iniciarMayoristas() {
    $('formulario-mayoristas').addEventListener('submit', enviar);
    $('mas-mayoristas').addEventListener('click', () => {
        // Varias pulsaciones seguidas no piden varias veces la misma página.
        if (!estado.siguiente || estado.cargando) return;
        cargar(estado.siguiente);
    });
}
