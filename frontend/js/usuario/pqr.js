/**
 * Sección PQR: radicar peticiones, quejas, reclamos o sugerencias y ver la respuesta del administrador.
 */

import { obtenerMisPqr, radicarPqr } from '../comun/api.js';
import { avisar, crear, mostrarEstado } from '../comun/dom.js';
import { fechaHora, nombreEstadoPqr, nombreTipoPqr } from '../comun/formato.js';
import { enfocarPrimerError, marcarCampo, validar } from '../comun/validacion.js';

const $ = (id) => document.getElementById(id);

function crearElementoPqr(pqr) {
    const elemento = crear('li');
    const estado = crear('span', `estado estado-${pqr.estado}`, nombreEstadoPqr(pqr.estado));
    const cabecera = crear('p');
    cabecera.append(`${nombreTipoPqr(pqr.tipo)} n.º ${pqr.id} · `, estado);
    elemento.append(
        cabecera,
        crear('p', 'precio', pqr.asunto),
        crear('p', null, pqr.descripcion),
        crear('p', 'ayuda', `Enviada el ${fechaHora(pqr.creada_en)}`),
    );
    if (pqr.respuesta) {
        const respuesta = crear('div', 'respuesta');
        respuesta.append(
            crear('p', null, `Respuesta del ${fechaHora(pqr.respondida_en)}:`),
            crear('p', null, pqr.respuesta),
        );
        elemento.append(respuesta);
    } else {
        elemento.append(crear('p', 'ayuda', 'Le responderemos pronto. La respuesta aparecerá aquí.'));
    }
    return elemento;
}

export async function cargarPqr() {
    const estado = $('estado-pqr');
    const lista = $('lista-pqr');
    lista.replaceChildren();
    mostrarEstado(estado, 'Cargando sus solicitudes…');
    try {
        const solicitudes = await obtenerMisPqr();
        mostrarEstado(estado, solicitudes.length === 0
            ? 'Todavía no ha enviado solicitudes.' : `Tiene ${solicitudes.length} solicitudes.`);
        lista.append(...solicitudes.map(crearElementoPqr));
    } catch (error) {
        mostrarEstado(estado, error.message, cargarPqr);
    }
}

export function iniciarPqr() {
    const formulario = $('formulario-pqr');
    const tipo = $('pqr-tipo');
    const asunto = $('pqr-asunto');
    const descripcion = $('pqr-descripcion');
    const contador = $('contador-pqr');
    descripcion.addEventListener('input', () => {
        contador.textContent = `${descripcion.value.length} de 2000 caracteres`;
    });

    formulario.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        avisar($('resultado-pqr'), '');
        const valido = [
            marcarCampo(tipo, tipo.value ? '' : 'Elija el tipo de solicitud.'),
            marcarCampo(asunto, validar.texto(asunto.value.trim(), 'el asunto', 5, 120)),
            marcarCampo(descripcion, validar.texto(descripcion.value.trim(), 'la descripción', 20, 2000)),
        ].every(Boolean);
        if (!valido) {
            enfocarPrimerError(formulario);
            return;
        }
        try {
            const pqr = await radicarPqr({
                tipo: tipo.value, asunto: asunto.value.trim(), descripcion: descripcion.value.trim(),
            });
            formulario.reset();
            contador.textContent = '0 de 2000 caracteres';
            avisar($('resultado-pqr'), `Recibimos su ${nombreTipoPqr(pqr.tipo).toLowerCase()} con el número ${pqr.id}. `
                + 'Le responderemos pronto.');
            await cargarPqr();
        } catch (error) {
            avisar($('resultado-pqr'), error.message, 'error');
        }
    });
}
