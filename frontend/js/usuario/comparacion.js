/**
 * Comparar productos: la persona elige de 2 a 4 ofertas de la misma búsqueda con "Comparar" y las ve lado a lado.
 * La selección se mantiene al pulsar "Ver más" o filtrar por precio, y se borra al buscar otra cosa. Los datos que
 * no se conocen dicen "No disponible": no se completan con suposiciones.
 */

import { compararOfertas } from '../comun/api.js';
import { boton, crear, crearIcono, enlaceTienda, mostrarEstado } from '../comun/dom.js';
import { tiendaDeOferta } from '../comun/formato.js';
import { irA } from './navegacion.js';

export const MAXIMO = 4;
const MINIMO = 2;
/** id de la oferta → { id, titulo, imagen, tienda } */
const seleccion = new Map();
let ultimaComparacion = null;

const $ = (id) => document.getElementById(id);

/** Lo que hace la selección, sin tocar la página: se prueba aparte. */
export const reglasSeleccion = {
    puedeAgregar: (cantidad) => cantidad < MAXIMO,
    puedeComparar: (cantidad) => cantidad >= MINIMO && cantidad <= MAXIMO,
};

/** Borra la selección (búsqueda nueva). */
export function reiniciarSeleccion() {
    seleccion.clear();
    actualizarBarra();
    actualizarBotones();
}

function alternar(oferta) {
    if (seleccion.has(oferta.id)) {
        seleccion.delete(oferta.id);
        anunciar(`Quitó "${oferta.titulo}" de la comparación. Lleva ${seleccion.size} de ${MAXIMO}.`);
    } else if (!reglasSeleccion.puedeAgregar(seleccion.size)) {
        anunciar(`Ya eligió ${MAXIMO} productos, el máximo. Quite uno para agregar otro.`);
        return;
    } else {
        seleccion.set(oferta.id, {
            id: oferta.id, titulo: oferta.titulo, imagen: oferta.url_imagen, tienda: tiendaDeOferta(oferta),
        });
        anunciar(`Agregó "${oferta.titulo}" a la comparación. Lleva ${seleccion.size} de ${MAXIMO}.`);
    }
    actualizarBarra();
    actualizarBotones();
}

function anunciar(texto) {
    const zona = $('anuncio-comparacion');
    if (zona) zona.textContent = texto;
}

/**
 * Botón "Comparar" de una tarjeta. Solo se ofrece si la oferta quedó guardada (tiene id).
 *
 * @param oferta oferta de la búsqueda
 */
export function botonComparar(oferta) {
    if (oferta.id == null) return null;
    const elemento = boton('Comparar', 'boton-secundario boton-comparar', () => alternar(oferta));
    elemento.dataset.comparar = String(oferta.id);
    elemento.setAttribute('aria-label', `Comparar: ${oferta.titulo}`);
    pintarBoton(elemento);
    return elemento;
}

function pintarBoton(elemento) {
    const id = Number(elemento.dataset.comparar);
    const elegido = seleccion.has(id);
    elemento.setAttribute('aria-pressed', String(elegido));
    elemento.replaceChildren(crearIcono(elegido ? 'check' : 'balanza'), elegido ? ' Elegido para comparar' : ' Comparar');
    elemento.disabled = !elegido && !reglasSeleccion.puedeAgregar(seleccion.size);
    elemento.title = elemento.disabled ? `Ya eligió ${MAXIMO} productos` : '';
}

function actualizarBotones() {
    document.querySelectorAll('[data-comparar]').forEach(pintarBoton);
}

function actualizarBarra() {
    const barra = $('barra-comparacion');
    if (!barra) return;
    barra.hidden = seleccion.size === 0;
    $('contador-comparacion').textContent = `${seleccion.size} de ${MAXIMO} productos para comparar`;
    const lista = $('miniaturas-comparacion');
    lista.replaceChildren(...[...seleccion.values()].map((elegido) => {
        const item = crear('li', 'miniatura');
        if (elegido.imagen) {
            const foto = document.createElement('img');
            foto.src = elegido.imagen;
            foto.alt = '';
            foto.loading = 'lazy';
            item.append(foto);
        } else {
            item.append(crearIcono('etiqueta'));
        }
        item.append(crear('span', 'miniatura-texto', elegido.titulo));
        const quitar = boton('×', 'boton-chip boton-quitar', () => {
            seleccion.delete(elegido.id);
            anunciar(`Quitó "${elegido.titulo}" de la comparación.`);
            actualizarBarra();
            actualizarBotones();
            $('abrir-comparacion').focus();
        });
        quitar.setAttribute('aria-label', `Quitar ${elegido.titulo} de la comparación`);
        item.append(quitar);
        return item;
    }));
    const abrir = $('abrir-comparacion');
    abrir.disabled = !reglasSeleccion.puedeComparar(seleccion.size);
    $('ayuda-comparacion').textContent = seleccion.size < MINIMO
        ? 'Elija al menos 2 productos con el botón "Comparar".' : 'Pulse "Ver comparación" para verlos lado a lado.';
}

async function abrirComparacion() {
    const ids = [...seleccion.keys()];
    if (!reglasSeleccion.puedeComparar(ids.length)) return;
    irA('comparar');
    const zona = $('estado-comparacion');
    $('tabla-comparacion').replaceChildren();
    $('avisos-comparacion').hidden = true;
    mostrarEstado(zona, 'Preparando la comparación…');
    try {
        ultimaComparacion = await compararOfertas(ids);
        zona.replaceChildren();
        pintarComparacion();
    } catch (error) {
        mostrarEstado(zona, error.message, abrirComparacion);
    }
}

function pintarComparacion() {
    const datos = ultimaComparacion;
    if (!datos) return;
    const avisos = $('avisos-comparacion');
    avisos.replaceChildren(...datos.avisos.map((texto) => crear('li', null, texto)));
    avisos.hidden = datos.avisos.length === 0;
    const soloDiferencias = $('solo-diferencias').checked;

    const tabla = crear('table', 'comparacion');
    tabla.append(crear('caption', 'solo-lectores',
        `Comparación de ${datos.productos.length} productos. Cada columna es un producto y cada fila una característica.`));
    const cabecera = crear('thead');
    const filaCabecera = crear('tr');
    const esquina = crear('td');
    filaCabecera.append(esquina);
    datos.productos.forEach((producto) => {
        const celda = crear('th', datos.mas_barato_id === producto.id ? 'mas-barato' : null);
        celda.scope = 'col';
        if (producto.url_imagen) {
            const foto = document.createElement('img');
            foto.src = producto.url_imagen;
            foto.alt = '';
            foto.loading = 'lazy';
            celda.append(foto);
        }
        celda.append(crear('span', 'comparacion-titulo', producto.titulo));
        if (datos.mas_barato_id === producto.id) celda.append(crear('span', 'etiqueta-mejor', '★ Más barato con envío'));
        const texto = producto.enlace?.texto ?? `Ver en ${tiendaDeOferta(producto)}`;
        celda.append(enlaceTienda(texto, producto.url_destino, tiendaDeOferta(producto), 'boton boton-secundario',
            producto.enlace));
        filaCabecera.append(celda);
    });
    cabecera.append(filaCabecera);
    const cuerpo = crear('tbody');
    let grupoActual = null;
    datos.filas.forEach((fila) => {
        if (soloDiferencias && !fila.difiere) return;
        if (fila.grupo !== grupoActual) {
            grupoActual = fila.grupo;
            const separador = crear('tr', 'grupo');
            const titulo = crear('th', null, fila.grupo === 'especificaciones' ? 'Características' : 'Oferta');
            titulo.scope = 'colgroup';
            titulo.colSpan = datos.productos.length + 1;
            separador.append(titulo);
            cuerpo.append(separador);
        }
        const tr = crear('tr', fila.difiere ? 'difiere' : null);
        const etiqueta = crear('th', null, fila.etiqueta);
        etiqueta.scope = 'row';
        if (fila.nota) etiqueta.append(crear('span', 'ayuda nota-fila', fila.nota));
        tr.append(etiqueta);
        fila.valores.forEach((valor) => {
            const celda = crear('td', valor.disponible ? null : 'no-disponible', valor.valor);
            if (valor.fuente === 'titulo') {
                celda.append(crear('span', 'fuente-dato', ' (según el título)'));
            }
            tr.append(celda);
        });
        cuerpo.append(tr);
    });
    tabla.append(cabecera, cuerpo);
    const contenedor = crear('div', 'tabla-desplazable');
    contenedor.tabIndex = 0;
    contenedor.setAttribute('role', 'region');
    contenedor.setAttribute('aria-label', 'Tabla de comparación; deslice o use las flechas para ver todas las columnas');
    contenedor.append(tabla);
    $('tabla-comparacion').replaceChildren(contenedor);
    if (soloDiferencias && !datos.filas.some((fila) => fila.difiere)) {
        $('tabla-comparacion').append(crear('p', 'ayuda', 'Con lo que sabemos, no hay diferencias entre estos productos.'));
    }
}

export function iniciarComparacion() {
    $('abrir-comparacion').addEventListener('click', abrirComparacion);
    $('limpiar-comparacion').addEventListener('click', () => {
        reiniciarSeleccion();
        anunciar('Se borró la selección para comparar.');
    });
    $('solo-diferencias').addEventListener('change', pintarComparacion);
    $('volver-de-comparacion').addEventListener('click', () => irA('buscar'));
    actualizarBarra();
}
