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
    /**
     * Búsqueda enviada con "Buscar" (término, cantidad y unidad), congelada. Los campos del formulario se pueden editar
     * después sin pulsar Buscar: los resultados, el detalle y las páginas siguientes se piden y se describen siempre con
     * esta búsqueda, nunca con lo que esté escrito en ese momento en el formulario.
     */
    enviada: null,
    pagina: 1, siguiente: null, configurado: null, variante: null,
    /** Catálogo de unidades del servidor (código → { singular, plural }). */
    unidades: new Map(),
    /** Variante elegida por oferta (id → variante): la lista y el detalle calculan con la misma. */
    variantes: new Map(),
    /** Número de la última solicitud: las respuestas de solicitudes anteriores se ignoran. */
    solicitud: 0,
    /** Número de la última solicitud de detalle (ficha o variante): una respuesta anterior no reemplaza a la nueva. */
    solicitudDetalle: 0,
    /** Id de la oferta de menor subtotal entre las verificadas con su ficha, o null. */
    ganadora: null,
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

/**
 * Nombre de una unidad para una cantidad, a partir de su código (el que confirmó el servidor) y del catálogo de
 * unidades. No se lee del formulario: el formulario puede tener otra unidad elegida que todavía no se buscó.
 *
 * @param {string} codigo código de la unidad, por ejemplo "PAR"
 * @param {number} cantidad cantidad (1 va en singular)
 * @param {Map<string, {singular: string, plural: string}>} catalogo unidades conocidas
 */
export function textoUnidad(codigo, cantidad, catalogo = estado.unidades) {
    const unidad = catalogo.get(codigo);
    if (!unidad) return String(codigo ?? '').toLowerCase();
    return cantidad === 1 ? unidad.singular : unidad.plural;
}

/** "100 pares", con miles con punto y la unidad del catálogo. */
export const textoPedido = (cantidad, codigo, catalogo = estado.unidades) =>
    `${textoCantidad(cantidad)} ${textoUnidad(codigo, cantidad, catalogo)}`;

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

/**
 * Etiqueta del lugar de una oferta. Solo la de menor subtotal entre las verificadas con su ficha se presenta como la
 * más baja; una oferta con precio preliminar (sin contrastar con la ficha) nunca se anuncia como ganadora.
 *
 * @param oferta oferta de la respuesta
 * @param ganadora id de la ganadora verificada, o null
 * @returns {{ clase: string, texto: string }|null}
 */
export function etiquetaPuesto(oferta, ganadora) {
    if (oferta.posicion == null) return null;
    if (oferta.preliminar) return { clase: 'etiqueta-preliminar', texto: 'Precio preliminar, pendiente de verificar' };
    if (ganadora != null && oferta.id === ganadora) {
        return {
            clase: 'etiqueta-mejor',
            texto: oferta.empate ? '★ Empate en el subtotal más bajo entre las ofertas verificadas'
                : '★ Subtotal más bajo entre las ofertas verificadas',
        };
    }
    return { clase: 'ayuda', texto: `Puesto ${oferta.posicion} por subtotal, precio verificado${oferta.empate ? ' (empate)' : ''}` };
}

function crearOferta(oferta, { conVariantes = false, ganadora = estado.ganadora } = {}) {
    const esGanadora = ganadora != null && oferta.id === ganadora && !oferta.preliminar;
    const item = crear('li', `tarjeta mayorista${esGanadora ? ' mejor' : ''}${oferta.motivo_fuera ? ' fuera-orden' : ''}`
        + `${oferta.preliminar ? ' preliminar' : ''}`);
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
    const puesto = etiquetaPuesto(oferta, ganadora);
    if (puesto) cuerpo.append(crear('p', puesto.clase, puesto.texto));
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

/**
 * Pide la ficha de una oferta (o una variante). Cada solicitud lleva un número y guarda lo que se pidió (producto,
 * variante, cantidad, unidad y búsqueda): el resultado o el error solo se aplican si siguen siendo la última solicitud y
 * la misma búsqueda. Así una respuesta lenta de una variante anterior no reemplaza a la más reciente.
 */
async function verDetalle(url, variante = null, id = null) {
    const pedida = {
        numero: ++estado.solicitudDetalle, url, variante, id, cantidad: estado.enviada.cantidad,
        unidad: estado.enviada.unidad, busqueda: claveBusqueda(),
    };
    estado.variante = variante;
    const zona = $('detalle-mayorista');
    zona.hidden = false;
    mostrarEstado($('estado-detalle-mayorista'), 'Consultando la ficha del producto en Alibaba…');
    try {
        const oferta = await obtenerDetalleMayorista(url, pedida.cantidad, pedida.unidad, pedida.variante);
        if (!sigueVigente(pedida)) return;
        aplicarDetalle(oferta, pedida);
    } catch (error) {
        if (!sigueVigente(pedida)) return;
        mostrarEstado($('estado-detalle-mayorista'), error.message, () => verDetalle(url, variante, id));
    }
}

/** Una respuesta de detalle solo vale si es la última pedida y la búsqueda no cambió. */
export function detalleVigente(pedida, actual) {
    return pedida.numero === actual.numero && pedida.busqueda === actual.busqueda;
}

const sigueVigente = (pedida) => detalleVigente(pedida, { numero: estado.solicitudDetalle, busqueda: claveBusqueda() });

function aplicarDetalle(oferta, pedida) {
    $('estado-detalle-mayorista').replaceChildren();
    // Se pinta con la variante de esta solicitud, no con el estado global, que pudo cambiar mientras tanto.
    estado.variante = pedida.variante;
    const lista = $('lista-detalle-mayorista');
    lista.replaceChildren(crearOferta(oferta, { conVariantes: true, ganadora: null }));
    if (!pedida.variante) $('titulo-detalle-mayorista').focus();
    const ofertaId = pedida.id ?? oferta.id;
    if (pedida.variante) estado.variantes.set(ofertaId, pedida.variante);
    else estado.variantes.delete(ofertaId);
    // La ficha puede cambiar el precio de la tarjeta, su lugar, la ganadora y los contadores: se recalcula toda la lista
    // con lo ya cargado (sale de la caché del servidor, no gasta consultas).
    if (estado.enviada) cargar(estado.pagina, { silencioso: true });
}

const claveBusqueda = () => (estado.enviada
    ? `${estado.enviada.numero}|${estado.enviada.termino}|${estado.enviada.cantidad}|${estado.enviada.unidad}` : '');

/** Cuántas tienen el precio verificado con la ficha y cuántas solo el precio preliminar de la búsqueda. */
export function textoContadores(respuesta) {
    const verificadas = respuesta.verificadas ?? 0;
    const preliminares = Math.max(0, (respuesta.comparables ?? 0) - verificadas);
    const partes = [`${verificadas} con precio verificado para su cantidad`];
    if (preliminares > 0) partes.push(`${preliminares} con precio preliminar, pendiente de verificar`);
    return `${partes.join(' y ')}, ordenadas de menor a mayor subtotal.`
        + (respuesta.ganadora_id ? '' : ' Todavía no hay una oferta verificada para recomendar.');
}

/**
 * Qué significa la estrella. Solo compite entre las verificadas: una preliminar más barata no se esconde, pero tampoco se
 * dice que la recomendada sea el precio más bajo de todas las ofertas.
 */
export function textoRecomendacion(respuesta) {
    if (!respuesta.ganadora_id) return '';
    const preliminares = Math.max(0, (respuesta.comparables ?? 0) - (respuesta.verificadas ?? 0));
    return ' La estrella es la de menor subtotal entre las verificadas de las ofertas cargadas, no entre todo Alibaba.'
        + (preliminares > 0 ? ' Mientras no se verifique su ficha, una oferta con precio preliminar puede verse más barata.' : '');
}

/**
 * Lee lo que hay en el formulario sin enviarlo.
 *
 * @returns {{ termino: string, cantidad: number|null, unidad: string }}
 */
function leerFormulario() {
    return {
        termino: $('termino-mayorista').value.trim(),
        cantidad: interpretarCantidad($('cantidad-mayorista').value).valor,
        unidad: $('unidad-mayorista').value,
    };
}

/** Si lo escrito en el formulario es distinto de la búsqueda que se ve: avisa que hay cambios sin aplicar. */
function revisarCambios() {
    const aviso = $('cambios-mayoristas');
    if (!aviso) return;
    const enviada = estado.enviada;
    const formulario = leerFormulario();
    const distinto = enviada != null && (formulario.termino !== enviada.termino
        || formulario.cantidad !== enviada.cantidad || formulario.unidad !== enviada.unidad);
    aviso.hidden = !distinto;
    aviso.textContent = distinto
        ? `Tiene cambios sin aplicar. Los resultados siguen siendo para "${enviada.termino}", `
          + `${textoPedido(enviada.cantidad, enviada.unidad)}. Pulse "Buscar al por mayor" para aplicarlos.`
        : '';
}

/**
 * Carga las páginas 1 a {@code hasta} en una sola respuesta: el servidor las junta, quita repetidos y ordena todas
 * las ofertas cargadas. Si llega una respuesta de una solicitud anterior (otra búsqueda, otra cantidad), se ignora.
 */
async function cargar(hasta = 1, { silencioso = false } = {}) {
    const zona = $('estado-mayoristas');
    // Instantánea de lo que se pide: la búsqueda enviada y las variantes elegidas en este momento.
    const solicitud = Object.freeze({
        numero: ++estado.solicitud,
        busqueda: estado.enviada,
        variantes: [...estado.variantes].map(([id, variante]) => `${id}:${variante}`).join(','),
    });
    estado.cargando = true;
    const mas = $('mas-mayoristas');
    mas.disabled = true;
    if (!silencioso) mostrarEstado(zona, 'Buscando en Alibaba, puede tardar hasta un minuto…');
    try {
        const { termino, cantidad, unidad } = solicitud.busqueda;
        const respuesta = await buscarMayoristas(termino, cantidad, unidad, hasta, solicitud.variantes);
        if (solicitud.numero !== estado.solicitud || solicitud.busqueda !== estado.enviada) return;
        estado.pagina = respuesta.pagina;
        estado.ganadora = respuesta.ganadora_id ?? null;
        const avisos = $('avisos-mayoristas');
        avisos.replaceChildren(...respuesta.avisos.map((texto) => crear('li', null, texto)));
        avisos.hidden = respuesta.avisos.length === 0;
        const principales = respuesta.ofertas.filter((oferta) => oferta.relevancia !== 'dudosa');
        const dudosas = respuesta.ofertas.filter((oferta) => oferta.relevancia === 'dudosa');
        $('lista-mayoristas').replaceChildren(...principales.map((oferta) => crearOferta(oferta)));
        $('lista-dudosas-mayoristas').replaceChildren(...dudosas.map((oferta) => crearOferta(oferta)));
        $('lista-dudosas-mayoristas').hidden = dudosas.length === 0;
        $('titulo-dudosas-mayoristas').hidden = dudosas.length === 0;
        // La unidad y la cantidad salen de la respuesta (lo que confirmó el servidor) y, si faltan, de la misma solicitud.
        const pedido = textoPedido(respuesta.cantidad ?? cantidad, respuesta.unidad ?? unidad);
        const paginas = respuesta.pagina === 1 ? 'Se cargó la primera página'
            : `Se cargaron las primeras ${respuesta.pagina} páginas`;
        mostrarEstado(zona, respuesta.ofertas.length === 0
            ? `No encontramos ofertas al por mayor para "${respuesta.termino}". Pruebe con palabras en inglés.`
            : `Cantidad: ${pedido}. ${paginas}: ${respuesta.ofertas.length} `
              + `ofertas distintas para "${respuesta.termino}". ${textoContadores(respuesta)}${textoRecomendacion(respuesta)} `
              + `${respuesta.nota}.`);
        estado.siguiente = respuesta.siguiente_pagina;
        mas.hidden = !estado.siguiente;
    } catch (error) {
        if (solicitud.numero !== estado.solicitud) return;
        mostrarEstado(zona, error.message, () => cargar(hasta));
    } finally {
        if (solicitud.numero === estado.solicitud) {
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
    estado.enviada = Object.freeze({
        numero: (estado.enviada?.numero ?? 0) + 1, termino, cantidad, unidad: $('unidad-mayorista').value,
    });
    Object.assign(estado, { pagina: 1, siguiente: null, variante: null });
    revisarCambios();
    estado.variantes.clear();
    // Una ficha pedida para la búsqueda anterior ya no se aplica.
    estado.solicitudDetalle += 1;
    estado.ganadora = null;
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
        estado.unidades = new Map(info.unidades.map((unidad) => [unidad.codigo, unidad]));
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
    // Editar sin pulsar Buscar no cambia los resultados: solo avisa que hay cambios sin aplicar.
    ['termino-mayorista', 'cantidad-mayorista', 'unidad-mayorista'].forEach((id) => {
        $(id).addEventListener('input', revisarCambios);
        $(id).addEventListener('change', revisarCambios);
    });
    $('mas-mayoristas').addEventListener('click', () => {
        // Varias pulsaciones seguidas no piden varias veces la misma página.
        if (!estado.siguiente || estado.cargando) return;
        cargar(estado.siguiente);
    });
}
