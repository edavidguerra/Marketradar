/**
 * Gestión de PQR: lista con el nombre de quien la radicó, filtro por estado y respuesta.
 */

import { abrirPqrAdmin, listarPqrAdmin, obtenerResumen, responderPqrAdmin } from '../comun/api.js';
import { avisar, boton, crear, mostrarEstado } from '../comun/dom.js';
import { fechaHora, nombreEstadoPqr, nombreTipoPqr } from '../comun/formato.js';
import { marcarCampo, validar } from '../comun/validacion.js';

const $ = (id) => document.getElementById(id);
let filtro = '';
let abierta = null;

function mostrarSeccion(nombre) {
    $('seccion-lista').hidden = nombre !== 'lista';
    $('seccion-detalle').hidden = nombre !== 'detalle';
    document.querySelector(`#seccion-${nombre} h1`).focus();
}

async function cargarResumen() {
    const cifras = $('cifras');
    try {
        const resumen = await obtenerResumen();
        const dato = (valor, texto) => {
            const elemento = crear('li');
            elemento.append(crear('strong', null, String(valor)), texto);
            return elemento;
        };
        cifras.replaceChildren(
            dato(resumen.pqr_radicadas, 'PQR nuevas'),
            dato(resumen.pqr_en_proceso, 'En revisión'),
            dato(resumen.pqr_respondidas, 'Respondidas'),
            dato(resumen.usuarios_registrados, 'Usuarios registrados'),
        );
    } catch (error) {
        cifras.replaceChildren(crear('li', null, error.message));
    }
}

function cabeceraPqr(pqr) {
    const cabecera = crear('p');
    cabecera.append(`${nombreTipoPqr(pqr.tipo)} n.º ${pqr.id} · `,
        crear('span', `estado estado-${pqr.estado}`, nombreEstadoPqr(pqr.estado)));
    return cabecera;
}

function crearElementoLista(pqr) {
    const elemento = crear('li');
    elemento.append(
        cabeceraPqr(pqr),
        crear('p', 'precio', pqr.asunto),
        crear('p', null, `De: ${pqr.nombre_usuario} (${pqr.correo_usuario})`),
        crear('p', 'ayuda', `Recibida el ${fechaHora(pqr.creada_en)}`),
        boton(pqr.estado === 'RESPONDIDA' ? 'Ver solicitud y respuesta' : 'Abrir y responder', 'boton-principal',
            () => abrir(pqr.id)),
    );
    return elemento;
}

async function cargarLista() {
    const lista = $('lista-pqr-admin');
    lista.replaceChildren();
    mostrarEstado($('estado-lista'), 'Cargando solicitudes…');
    try {
        const solicitudes = await listarPqrAdmin(filtro);
        mostrarEstado($('estado-lista'), solicitudes.length === 0
            ? 'No hay solicitudes con este filtro.'
            : `${solicitudes.length} solicitudes. Primero las más antiguas sin responder.`);
        lista.append(...solicitudes.map(crearElementoLista));
    } catch (error) {
        mostrarEstado($('estado-lista'), error.message, cargarLista);
    }
}

function mostrarDetalle(pqr) {
    abierta = pqr;
    const detalle = $('detalle-pqr');
    const datos = crear('div');
    datos.append(
        cabeceraPqr(pqr),
        crear('h2', null, pqr.asunto),
        crear('p', null, `Usuario: ${pqr.nombre_usuario}`),
        crear('p', null, `Correo: ${pqr.correo_usuario}`),
        crear('p', 'ayuda', `Recibida el ${fechaHora(pqr.creada_en)}`),
        crear('p', null, pqr.descripcion),
    );
    if (pqr.respuesta) {
        const respuesta = crear('div', 'respuesta');
        respuesta.append(crear('p', null, `Respondida el ${fechaHora(pqr.respondida_en)}:`),
            crear('p', null, pqr.respuesta));
        datos.append(respuesta);
    }
    detalle.replaceChildren(datos);
    $('formulario-respuesta').hidden = pqr.estado === 'RESPONDIDA';
    $('titulo-detalle-pqr').textContent = `Solicitud n.º ${pqr.id} de ${pqr.nombre_usuario}`;
}

async function abrir(id) {
    mostrarSeccion('detalle');
    avisar($('resultado-respuesta'), '');
    $('formulario-respuesta').reset();
    $('contador-respuesta').textContent = '0 de 2000 caracteres';
    mostrarEstado($('detalle-pqr'), 'Abriendo la solicitud…');
    $('formulario-respuesta').hidden = true;
    try {
        mostrarDetalle(await abrirPqrAdmin(id));
    } catch (error) {
        mostrarEstado($('detalle-pqr'), error.message, () => abrir(id));
    }
}

function conectarRespuesta() {
    const formulario = $('formulario-respuesta');
    const campo = $('respuesta-pqr');
    campo.addEventListener('input', () => {
        $('contador-respuesta').textContent = `${campo.value.length} de 2000 caracteres`;
    });
    formulario.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        if (!marcarCampo(campo, validar.texto(campo.value.trim(), 'la respuesta', 10, 2000))) {
            campo.focus();
            return;
        }
        try {
            mostrarDetalle(await responderPqrAdmin(abierta.id, campo.value.trim()));
            avisar($('resultado-respuesta'), 'La respuesta quedó enviada. El usuario la verá en su sección de PQR.');
        } catch (error) {
            avisar($('resultado-respuesta'), error.message, 'error');
        }
    });
}

export function iniciarPqrAdmin() {
    document.querySelectorAll('[data-estado]').forEach((botonFiltro) => {
        botonFiltro.addEventListener('click', () => {
            filtro = botonFiltro.dataset.estado;
            document.querySelectorAll('[data-estado]').forEach((otro) =>
                otro.setAttribute('aria-pressed', String(otro === botonFiltro)));
            cargarLista();
        });
    });
    $('volver-lista').addEventListener('click', () => {
        mostrarSeccion('lista');
        cargarResumen();
        cargarLista();
    });
    conectarRespuesta();
    cargarResumen();
    cargarLista();
}
