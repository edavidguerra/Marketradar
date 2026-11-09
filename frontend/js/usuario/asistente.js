/**
 * Panel del asistente de IA: conversación en texto sencillo, con enlaces de compra que se pueden presionar.
 */

import { preguntarAsistente } from '../comun/api.js';
import { crear } from '../comun/dom.js';
import { formatearRespuesta } from './formato-asistente.js';

const historial = [];

function agregarMensaje(lista, rol, texto) {
    const elemento = crear('li', rol === 'usuario' ? 'mensaje-usuario' : 'mensaje-asistente');
    const autor = crear('span', 'autor', `${rol === 'usuario' ? 'Usted' : 'Asistente'}: `);
    // La respuesta del asistente se muestra con negritas y con enlaces que se pueden presionar.
    elemento.append(autor, rol === 'usuario' ? crear('span', null, texto) : formatearRespuesta(texto));
    lista.append(elemento);
    elemento.scrollIntoView({ block: 'nearest' });
}

/** Tres puntos animados mientras el asistente responde; el botón ya dice "Pensando…" al lector de pantalla. */
function mostrarEscribiendo(lista) {
    const elemento = crear('li', 'escribiendo');
    elemento.setAttribute('aria-hidden', 'true');
    elemento.append(crear('span'), crear('span'), crear('span'));
    lista.append(elemento);
    elemento.scrollIntoView({ block: 'nearest' });
    return elemento;
}

export function iniciarAsistente() {
    const botonAbrir = document.getElementById('abrir-asistente');
    const panel = document.getElementById('panel-asistente');
    const formulario = document.getElementById('formulario-asistente');
    const campo = document.getElementById('campo-asistente');
    const conversacion = document.getElementById('conversacion');

    function mostrar(abierto) {
        panel.hidden = !abierto;
        botonAbrir.setAttribute('aria-expanded', String(abierto));
        if (abierto) campo.focus();
        else botonAbrir.focus();
    }

    botonAbrir.addEventListener('click', () => mostrar(panel.hidden));
    document.getElementById('cerrar-asistente').addEventListener('click', () => mostrar(false));

    formulario.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        const mensaje = campo.value.trim();
        if (!mensaje) return;
        agregarMensaje(conversacion, 'usuario', mensaje);
        campo.value = '';
        const enviar = formulario.querySelector('[type="submit"]');
        enviar.disabled = true;
        enviar.textContent = 'Pensando…';
        const escribiendo = mostrarEscribiendo(conversacion);
        try {
            const { respuesta } = await preguntarAsistente(mensaje, historial.slice(-10));
            agregarMensaje(conversacion, 'asistente', respuesta);
            historial.push({ rol: 'usuario', contenido: mensaje }, { rol: 'asistente', contenido: respuesta });
        } catch (error) {
            agregarMensaje(conversacion, 'asistente', error.message);
        } finally {
            escribiendo.remove();
            enviar.disabled = false;
            enviar.textContent = 'Enviar pregunta';
            campo.focus();
        }
    });
}
