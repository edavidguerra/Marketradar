/**
 * Verifica la sesión al abrir una interfaz y lleva a cada rol a la página que le corresponde.
 */

import { obtenerSesion, salir } from './api.js';

/**
 * @param {'USUARIO'|'ADMINISTRADOR'} rolEsperado rol que puede ver esta página
 * @returns {Promise<object|null>} la sesión, o null si se redirigió a otra página
 */
export async function exigirSesion(rolEsperado) {
    let sesion = null;
    try {
        sesion = await obtenerSesion();
    } catch {
        sesion = null;
    }
    if (!sesion) {
        window.location.replace('/index.html');
        return null;
    }
    if (sesion.rol !== rolEsperado) {
        window.location.replace(sesion.interfaz);
        return null;
    }
    return sesion;
}

/** Conecta el botón "Salir" de la página. */
export function conectarSalida(idBoton = 'salir') {
    document.getElementById(idBoton).addEventListener('click', async () => {
        try {
            await salir();
        } finally {
            window.location.replace('/index.html?sesion=cerrada');
        }
    });
}

/** Saludo según la hora del día en Colombia. */
export function saludo(nombre) {
    const hora = new Date().getHours();
    const parte = hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
    return `${parte}, ${nombre}`;
}
