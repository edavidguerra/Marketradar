/**
 * Navegación entre las secciones de la interfaz de usuario con el ancla de la dirección (#inicio,
 * #buscar...), para que el botón "Atrás" del navegador funcione como la persona espera.
 */

const SECCIONES = ['inicio', 'buscar', 'detalle', 'historial', 'perfil', 'pqr', 'tutorial'];
const TITULOS = {
    inicio: 'Inicio', buscar: 'Buscar', detalle: 'Historial de precio', historial: 'Mis búsquedas',
    perfil: 'Mi perfil', pqr: 'PQR', tutorial: 'Tutorial',
};
const alMostrar = new Map();

/** Registra lo que se hace cada vez que se muestra una sección (por ejemplo, cargar datos). */
export function alMostrarSeccion(nombre, accion) {
    alMostrar.set(nombre, accion);
}

function mostrar(nombre) {
    const seccion = SECCIONES.includes(nombre) ? nombre : 'inicio';
    for (const otra of SECCIONES) {
        document.getElementById(`seccion-${otra}`).hidden = otra !== seccion;
    }
    document.querySelectorAll('.menu a').forEach((enlace) => {
        if (enlace.getAttribute('href') === `#${seccion}`) enlace.setAttribute('aria-current', 'page');
        else enlace.removeAttribute('aria-current');
    });
    document.title = `${TITULOS[seccion]} · Marketradar`;
    document.querySelector(`#seccion-${seccion} h1`).focus();
    alMostrar.get(seccion)?.();
}

/** Muestra una sección. */
export function irA(nombre) {
    if (window.location.hash === `#${nombre}`) mostrar(nombre);
    else window.location.hash = nombre;
}

export function iniciarNavegacion() {
    window.addEventListener('hashchange', () => mostrar(window.location.hash.slice(1)));
    mostrar(window.location.hash.slice(1) || 'inicio');
}
