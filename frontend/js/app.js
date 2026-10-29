/**
 * Punto de entrada del frontend: conecta las pantallas con la API.
 */

import { buscar, obtenerHistorial, obtenerOferta } from './api.js';
import { iniciarAccesibilidad } from './accesibilidad.js';
import { iniciarAsistente } from './asistente.js';
import {
    crearTarjeta, mostrarAdvertencias, mostrarDetalle, mostrarEstado, mostrarPantalla,
} from './vistas.js';

const TAMANO_PAGINA = 10;

const estado = { termino: '', pagina: 1 };

const formulario = document.getElementById('formulario-busqueda');
const campo = document.getElementById('campo-busqueda');
const errorBusqueda = document.getElementById('error-busqueda');
const estadoResultados = document.getElementById('estado-resultados');
const listaResultados = document.getElementById('lista-resultados');
const advertencias = document.getElementById('advertencias');
const botonVerMas = document.getElementById('ver-mas');
const contenidoDetalle = document.getElementById('contenido-detalle');

function validarTermino(termino) {
    if (termino.length < 2) return 'Escriba al menos 2 letras del producto que busca.';
    if (termino.length > 100) return 'Escriba un nombre más corto, de máximo 100 letras.';
    return '';
}

async function cargarPagina() {
    mostrarEstado(estadoResultados, 'Buscando los mejores precios, un momento por favor…');
    botonVerMas.hidden = true;
    try {
        const respuesta = await buscar(estado.termino, estado.pagina, TAMANO_PAGINA);
        mostrarAdvertencias(advertencias, respuesta.advertencias || []);
        const tarjetas = respuesta.ofertas.map((oferta) => crearTarjeta(oferta, verHistorial));
        listaResultados.append(...tarjetas);
        if (respuesta.total_resultados === 0) {
            mostrarEstado(estadoResultados,
                `No encontramos resultados para "${estado.termino}". Pruebe con otra palabra.`);
        } else {
            mostrarEstado(estadoResultados,
                `Encontramos ${respuesta.total_resultados} resultados para "${estado.termino}", del más barato al más caro.`);
        }
        botonVerMas.hidden = estado.pagina * TAMANO_PAGINA >= respuesta.total_resultados;
    } catch (error) {
        mostrarEstado(estadoResultados, error.message, cargarPagina);
    }
}

async function verHistorial(id) {
    mostrarPantalla('detalle');
    mostrarEstado(contenidoDetalle, 'Cargando el historial de precio…');
    try {
        const [oferta, historial] = await Promise.all([obtenerOferta(id), obtenerHistorial(id)]);
        mostrarDetalle(contenidoDetalle, oferta, historial);
    } catch (error) {
        mostrarEstado(contenidoDetalle, error.message, () => verHistorial(id));
    }
}

formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const termino = campo.value.trim();
    const mensaje = validarTermino(termino);
    errorBusqueda.textContent = mensaje;
    errorBusqueda.hidden = !mensaje;
    campo.setAttribute('aria-invalid', String(Boolean(mensaje)));
    if (mensaje) {
        campo.focus();
        return;
    }
    estado.termino = termino;
    estado.pagina = 1;
    listaResultados.replaceChildren();
    mostrarPantalla('resultados');
    cargarPagina();
});

botonVerMas.addEventListener('click', () => {
    estado.pagina += 1;
    cargarPagina();
});

document.querySelectorAll('[data-volver]').forEach((boton) => {
    boton.addEventListener('click', () => mostrarPantalla(boton.dataset.volver));
});

iniciarAccesibilidad();
iniciarAsistente();
