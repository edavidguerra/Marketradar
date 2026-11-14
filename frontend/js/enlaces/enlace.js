/**
 * Páginas de los enlaces de los correos: verificar el correo y dejar de seguir un producto. El token viene en el
 * ancla de la dirección (#token=...), que el navegador no manda al servidor ni deja en sus registros; se manda en el
 * cuerpo de la petición solo cuando la persona pulsa el botón (así un revisor automático de correos no lo usa).
 */

import { iniciarAccesibilidad } from '../comun/accesibilidad.js';
import { cancelarPorEnlace, verificarCorreo } from '../comun/api.js';
import { avisar } from '../comun/dom.js';

/** Lee el token del ancla: 43 caracteres base64url. */
export function leerToken(ancla) {
    const valor = new URLSearchParams(String(ancla || '').replace(/^#/, '')).get('token');
    return valor && /^[A-Za-z0-9_-]{43}$/.test(valor) ? valor : null;
}

function iniciar() {
    iniciarAccesibilidad();
    const token = leerToken(window.location.hash);
    // Se quita el token de la dirección para que no quede en el historial ni se comparta por error.
    if (window.history?.replaceState) window.history.replaceState(null, '', window.location.pathname);
    const resultado = document.getElementById('resultado-enlace');
    const confirmar = document.getElementById('confirmar-enlace');
    if (!token) {
        confirmar.hidden = true;
        avisar(resultado, 'El enlace está incompleto. Ábralo otra vez desde el correo o pida uno nuevo en Mis seguimientos.', 'error');
        return;
    }
    confirmar.addEventListener('click', async () => {
        confirmar.disabled = true;
        try {
            const respuesta = document.body.dataset.accion === 'verificar'
                ? await verificarCorreo(token) : await cancelarPorEnlace(token);
            avisar(resultado, respuesta.mensaje);
            confirmar.hidden = true;
        } catch (error) {
            avisar(resultado, error.message, 'error');
            confirmar.disabled = false;
        }
        document.querySelector('h1').focus();
    });
}

iniciar();
