/**
 * Panel del asistente de IA: conversación en texto sencillo.
 */

import { preguntarAsistente } from '../comun/api.js';

const historial = [];

function agregarMensaje(lista, rol, texto) {
    const elemento = document.createElement('li');
    elemento.className = rol === 'usuario' ? 'mensaje-usuario' : 'mensaje-asistente';
    elemento.textContent = `${rol === 'usuario' ? 'Usted' : 'Asistente'}: ${texto}`;
    lista.append(elemento);
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
        try {
            const { respuesta } = await preguntarAsistente(mensaje, historial.slice(-10));
            agregarMensaje(conversacion, 'asistente', respuesta);
            historial.push({ rol: 'usuario', contenido: mensaje }, { rol: 'asistente', contenido: respuesta });
        } catch (error) {
            agregarMensaje(conversacion, 'asistente', error.message);
        } finally {
            enviar.disabled = false;
            enviar.textContent = 'Enviar pregunta';
            campo.focus();
        }
    });
}
