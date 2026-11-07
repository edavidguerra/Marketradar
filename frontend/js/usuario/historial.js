/**
 * Sección Historial: las búsquedas que hizo la persona, para repetirlas con un clic.
 */

import { borrarHistorialBusquedas, obtenerHistorialBusquedas } from '../comun/api.js';
import { avisar, boton, crear, crearIcono, mostrarEstado } from '../comun/dom.js';
import { fechaHora } from '../comun/formato.js';
import { buscarTermino } from './busqueda.js';

const $ = (id) => document.getElementById(id);

export async function cargarHistorial() {
    const estado = $('estado-historial');
    const lista = $('lista-historial');
    lista.replaceChildren();
    $('borrar-historial').hidden = true;
    mostrarEstado(estado, 'Cargando sus búsquedas…');
    try {
        const busquedas = await obtenerHistorialBusquedas();
        if (busquedas.length === 0) {
            mostrarEstado(estado, 'Todavía no ha hecho búsquedas. Cuando busque un producto, aparecerá aquí.');
            return;
        }
        mostrarEstado(estado, `Estas son sus últimas ${busquedas.length} búsquedas, de la más reciente a la más antigua.`);
        lista.append(...busquedas.map((busqueda) => {
            const elemento = crear('li', 'busqueda-historial');
            const datos = crear('div', 'busqueda-historial-datos');
            datos.append(
                crear('p', 'precio', busqueda.termino),
                crear('p', 'ayuda', `${fechaHora(busqueda.creada_en)} · ${busqueda.total_resultados} resultados`),
                boton(`Buscar "${busqueda.termino}" otra vez`, 'boton-principal', () => buscarTermino(busqueda.termino)),
            );
            elemento.append(fotoDeBusqueda(busqueda), datos);
            return elemento;
        }));
        $('borrar-historial').hidden = false;
    } catch (error) {
        mostrarEstado(estado, error.message, cargarHistorial);
    }
}

/**
 * Foto de un producto que salió en la búsqueda, para reconocerla de un vistazo. Es decorativa (alt vacío)
 * porque el término ya está escrito al lado. Si no hay foto o no carga, se ve el ícono de búsqueda.
 */
function fotoDeBusqueda(busqueda) {
    const marco = crear('div', 'busqueda-historial-foto');
    const icono = () => marco.replaceChildren(crearIcono('buscar'));
    if (!busqueda.url_imagen || !busqueda.url_imagen.startsWith('https://')) {
        icono();
        return marco;
    }
    const foto = document.createElement('img');
    foto.src = busqueda.url_imagen;
    foto.alt = '';
    foto.loading = 'lazy';
    foto.decoding = 'async';
    foto.addEventListener('error', icono, { once: true });
    marco.append(foto);
    return marco;
}

export function iniciarHistorial() {
    $('borrar-historial').addEventListener('click', async () => {
        if (!window.confirm('¿Seguro que quiere borrar todas sus búsquedas? Esta acción no se puede deshacer.')) return;
        try {
            await borrarHistorialBusquedas();
            await cargarHistorial();
            avisar($('resultado-historial'), 'Su historial quedó borrado.');
        } catch (error) {
            avisar($('resultado-historial'), error.message, 'error');
        }
    });
}
