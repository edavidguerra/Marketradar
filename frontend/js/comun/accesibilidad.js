/**
 * Controles de tamaño de letra y alto contraste. La preferencia se recuerda en este navegador.
 */

const TAMANO_MINIMO = 20;
const TAMANO_MAXIMO = 32;
const PASO = 2;

function leer(clave, porDefecto) {
    try {
        return localStorage.getItem(clave) ?? porDefecto;
    } catch {
        return porDefecto;
    }
}

function guardar(clave, valor) {
    try {
        localStorage.setItem(clave, valor);
    } catch {
        /* Sin almacenamiento disponible: la preferencia dura solo esta visita. */
    }
}

function aplicarTamano(tamano) {
    document.documentElement.style.setProperty('--tamano-base', `${tamano}px`);
}

export function iniciarAccesibilidad() {
    let tamano = Number(leer('marketradar.tamano', TAMANO_MINIMO));
    aplicarTamano(tamano);

    const raiz = document.documentElement;
    const botonContraste = document.getElementById('contraste');
    const contrasteActivo = leer('marketradar.contraste', 'no') === 'si';
    raiz.classList.toggle('alto-contraste', contrasteActivo);
    botonContraste.setAttribute('aria-pressed', String(contrasteActivo));

    document.getElementById('letra-mas').addEventListener('click', () => {
        tamano = Math.min(TAMANO_MAXIMO, tamano + PASO);
        aplicarTamano(tamano);
        guardar('marketradar.tamano', tamano);
    });

    document.getElementById('letra-menos').addEventListener('click', () => {
        tamano = Math.max(TAMANO_MINIMO, tamano - PASO);
        aplicarTamano(tamano);
        guardar('marketradar.tamano', tamano);
    });

    botonContraste.addEventListener('click', () => {
        const activo = raiz.classList.toggle('alto-contraste');
        botonContraste.setAttribute('aria-pressed', String(activo));
        guardar('marketradar.contraste', activo ? 'si' : 'no');
    });
}
