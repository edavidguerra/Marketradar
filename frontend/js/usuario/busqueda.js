/**
 * Sección Buscar: resultados de todas las tiendas, la mejor opción de compra (producto MVP) con su
 * enlace directo, y el historial de precio de cada oferta.
 */

import { buscar, obtenerHistorialPrecio, obtenerOferta } from '../comun/api.js';
import { crear, crearIcono, boton, enlaceTienda, mostrarEstado } from '../comun/dom.js';
import { decimal, estrellas, fechaCorta, haceCuanto, nombreTienda, pesos } from '../comun/formato.js';
import { irA } from './navegacion.js';

const TAMANO_PAGINA = 10;
const estado = { termino: '', pagina: 1 };

const $ = (id) => document.getElementById(id);

function validarTermino(termino) {
    if (termino.length < 2) return 'Escriba al menos 2 letras del producto que busca.';
    if (termino.length > 100) return 'Escriba un nombre más corto, de máximo 100 letras.';
    return '';
}

function crearCalificacion(oferta) {
    if (oferta.calificacion == null || !oferta.total_resenas) {
        return crear('p', 'ayuda', 'Sin opiniones de compradores');
    }
    const parrafo = crear('p', 'calificacion');
    const figura = crear('span', 'estrellas', estrellas(oferta.calificacion));
    figura.setAttribute('aria-hidden', 'true');
    parrafo.append(figura, ` ${decimal(oferta.calificacion)} de 5 (${oferta.total_resenas.toLocaleString('es-CO')} opiniones)`);
    return parrafo;
}

function crearDato(icono, texto) {
    const dato = crear('li', 'dato');
    dato.append(crearIcono(icono), texto);
    return dato;
}

function crearTarjeta(oferta) {
    const tienda = nombreTienda(oferta.tienda);
    const tarjeta = crear('li', oferta.mejor_precio ? 'tarjeta mejor' : 'tarjeta');
    const imagen = crear('div', 'tarjeta-imagen');
    if (oferta.url_imagen) {
        const foto = document.createElement('img');
        foto.src = oferta.url_imagen;
        foto.alt = oferta.titulo;
        foto.loading = 'lazy';
        imagen.append(foto);
    } else {
        imagen.append(crearIcono('etiqueta'));
    }
    const cuerpo = crear('div', 'tarjeta-cuerpo');
    if (oferta.mejor_precio) cuerpo.append(crear('p', 'etiqueta-mejor', '★ Precio más bajo'));
    const precio = crear('p', 'precio', pesos(oferta.precio_total));
    precio.append(crear('span', 'precio-nota', ' en total'));
    const datos = crear('ul', 'datos');
    datos.append(
        crearDato('tienda', `Tienda: ${tienda}`),
        crearDato('camion', oferta.costo_envio == null ? 'Envío: consultar en la tienda'
            : Number(oferta.costo_envio) === 0 ? 'Envío gratis' : `Incluye envío de ${pesos(oferta.costo_envio)}`),
    );
    cuerpo.append(
        crear('h2', null, oferta.titulo),
        precio,
        crearCalificacion(oferta),
        datos,
        crear('p', 'ayuda', `Precio consultado ${haceCuanto(oferta.consultado_en)}. Puede cambiar en la tienda.`),
    );
    const acciones = crear('div', 'acciones');
    acciones.append(enlaceTienda(`Ver en ${tienda}`, oferta.url_destino, tienda));
    if (oferta.id != null) {
        acciones.append(boton('Ver historial de precio', 'boton-secundario', () => verHistorial(oferta.id)));
    }
    cuerpo.append(acciones);
    tarjeta.append(imagen, cuerpo);
    return tarjeta;
}

/** Tarjetas grises animadas que ocupan el lugar de los resultados mientras llegan. */
function crearEsqueletos(cantidad) {
    return Array.from({ length: cantidad }, () => {
        const esqueleto = crear('li', 'tarjeta esqueleto');
        esqueleto.setAttribute('aria-hidden', 'true');
        esqueleto.append(crear('div', 'tarjeta-imagen'), crear('div', 'tarjeta-cuerpo'));
        return esqueleto;
    });
}

function mostrarMvp(mvp) {
    const contenedor = $('producto-mvp');
    if (!mvp) {
        contenedor.replaceChildren();
        contenedor.hidden = true;
        return;
    }
    const { oferta } = mvp;
    const tienda = nombreTienda(oferta.tienda);
    const razones = crear('ul');
    mvp.razones.forEach((razon) => razones.append(crear('li', null, razon)));
    const sello = crear('p', 'sello');
    sello.append(crearIcono('estrella'), 'Nuestra recomendación: la mejor opción de compra');
    const barra = crear('div', 'puntaje');
    barra.setAttribute('aria-hidden', 'true');
    const relleno = crear('span');
    relleno.style.setProperty('--puntaje', `${Math.max(0, Math.min(100, Number(mvp.puntaje)))}%`);
    barra.append(relleno);
    contenedor.replaceChildren(
        sello,
        crear('h2', null, oferta.titulo),
        crear('p', 'precio', `${pesos(oferta.precio_total)} en ${tienda}`),
        crearCalificacion(oferta),
        crear('p', 'razones-titulo', '¿Por qué se la recomendamos?'),
        razones,
        crear('p', 'ayuda', `Puntaje de compra: ${mvp.puntaje} de 100, según precio, opiniones y envío.`),
        barra,
        enlaceTienda(`Comprar este producto en ${tienda}`, mvp.enlace_compra, tienda),
    );
    contenedor.hidden = false;
}

function mostrarAdvertencias(advertencias) {
    const lista = $('advertencias');
    lista.replaceChildren(...advertencias.map((texto) => crear('li', null, texto)));
    lista.hidden = advertencias.length === 0;
}

async function cargarPagina() {
    const estadoResultados = $('estado-resultados');
    mostrarEstado(estadoResultados, 'Buscando los mejores precios, un momento por favor…');
    $('ver-mas').hidden = true;
    const esqueletos = crearEsqueletos(estado.pagina === 1 ? 3 : 1);
    $('lista-resultados').append(...esqueletos);
    try {
        const respuesta = await buscar(estado.termino, estado.pagina, TAMANO_PAGINA);
        mostrarAdvertencias(respuesta.advertencias || []);
        if (estado.pagina === 1) mostrarMvp(respuesta.producto_mvp);
        $('lista-resultados').append(...respuesta.ofertas.map(crearTarjeta));
        mostrarEstado(estadoResultados, respuesta.total_resultados === 0
            ? `No encontramos resultados para "${estado.termino}". Pruebe con otra palabra.`
            : `Encontramos ${respuesta.total_resultados} resultados para "${estado.termino}". Primero le mostramos `
              + 'la mejor opción de compra y después todas las ofertas, de la más barata a la más cara.');
        $('ver-mas').hidden = estado.pagina * TAMANO_PAGINA >= respuesta.total_resultados;
    } catch (error) {
        mostrarEstado(estadoResultados, error.message, cargarPagina);
    } finally {
        esqueletos.forEach((esqueleto) => esqueleto.remove());
    }
}

/** Busca un término; se usa también desde Inicio e Historial. */
export function buscarTermino(termino) {
    $('campo-busqueda').value = termino;
    estado.termino = termino;
    estado.pagina = 1;
    $('lista-resultados').replaceChildren();
    mostrarMvp(null);
    irA('buscar');
    cargarPagina();
}

function crearGrafico(historial) {
    const ancho = 600;
    const alto = 240;
    const margen = 20;
    const precios = historial.map((punto) => Number(punto.precio));
    const minimo = Math.min(...precios);
    const rango = Math.max(...precios) - minimo || 1;
    const pasoX = historial.length > 1 ? (ancho - 2 * margen) / (historial.length - 1) : 0;
    const puntos = precios.map((precio, i) => {
        const x = margen + i * pasoX;
        const y = alto - margen - ((precio - minimo) / rango) * (alto - 2 * margen);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
    });
    const svgNs = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNs, 'svg');
    svg.setAttribute('viewBox', `0 0 ${ancho} ${alto}`);
    svg.setAttribute('class', 'grafico');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', 'Gráfico del precio a lo largo del tiempo');
    const linea = document.createElementNS(svgNs, 'polyline');
    linea.setAttribute('points', puntos.join(' '));
    linea.setAttribute('pathLength', '1');
    svg.append(linea);
    return svg;
}

function explicarHistorial(historial) {
    if (historial.length < 2) {
        return 'Todavía no tenemos cambios de precio para este producto. Vuelva a consultarlo otro día.';
    }
    const diferencia = Number(historial[historial.length - 1].precio) - Number(historial[0].precio);
    const desde = fechaCorta(historial[0].registrado_en);
    if (diferencia < 0) return `El precio bajó ${pesos(-diferencia)} desde el ${desde}.`;
    if (diferencia > 0) return `El precio subió ${pesos(diferencia)} desde el ${desde}.`;
    return `El precio es el mismo que el ${desde}.`;
}

async function verHistorial(id) {
    const contenedor = $('contenido-detalle');
    irA('detalle');
    mostrarEstado(contenedor, 'Cargando el historial de precio…');
    try {
        const [oferta, historial] = await Promise.all([obtenerOferta(id), obtenerHistorialPrecio(id)]);
        const lista = crear('ul');
        historial.forEach((punto) => lista.append(crear('li', null,
            `${fechaCorta(punto.registrado_en)}: ${pesos(punto.precio)}`)));
        contenedor.replaceChildren(
            crear('h2', null, oferta.titulo),
            crear('p', 'precio', `Precio actual: ${pesos(oferta.precio_total)}`),
            ...(historial.length > 0 ? [crearGrafico(historial)] : []),
            crear('p', null, explicarHistorial(historial)),
            lista,
        );
    } catch (error) {
        mostrarEstado(contenedor, error.message, () => verHistorial(id));
    }
}

export function iniciarBusqueda() {
    const formulario = $('formulario-busqueda');
    const campo = $('campo-busqueda');
    const error = $('error-busqueda');
    formulario.addEventListener('submit', (evento) => {
        evento.preventDefault();
        const termino = campo.value.trim();
        const mensaje = validarTermino(termino);
        error.textContent = mensaje;
        error.hidden = !mensaje;
        campo.setAttribute('aria-invalid', String(Boolean(mensaje)));
        if (mensaje) {
            campo.focus();
            return;
        }
        buscarTermino(termino);
    });
    $('ver-mas').addEventListener('click', () => {
        estado.pagina += 1;
        cargarPagina();
    });
    $('volver-resultados').addEventListener('click', () => irA('buscar'));
}
