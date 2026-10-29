/**
 * Sección Inicio: saludo, aviso dinámico que invita a comprar y accesos a cada opción.
 */

import { obtenerAvisos } from '../comun/api.js';
import { boton, crear, enlaceTienda, mostrarEstado } from '../comun/dom.js';
import { buscarTermino } from './busqueda.js';
import { irA } from './navegacion.js';

let avisos = [];
let actual = 0;

function mostrarAviso() {
    const contenedor = document.getElementById('aviso-inicio');
    const aviso = avisos[actual];
    const acciones = crear('div', 'acciones');
    if (aviso.enlace) {
        acciones.append(enlaceTienda(aviso.texto_accion, aviso.enlace, 'la tienda'));
    } else if (aviso.termino_busqueda) {
        acciones.append(boton(aviso.texto_accion, 'boton-principal', () => buscarTermino(aviso.termino_busqueda)));
    } else {
        acciones.append(boton(aviso.texto_accion, 'boton-principal', () => irA('buscar')));
    }
    if (aviso.enlace && aviso.termino_busqueda) {
        acciones.append(boton('Comparar otra vez', 'boton-secundario', () => buscarTermino(aviso.termino_busqueda)));
    }
    if (avisos.length > 1) {
        acciones.append(boton(`Ver otro aviso (${actual + 1} de ${avisos.length})`, 'boton-secundario', () => {
            actual = (actual + 1) % avisos.length;
            mostrarAviso();
        }));
    }
    contenedor.replaceChildren(
        crear('p', 'titulo-aviso', aviso.titulo),
        crear('p', null, aviso.mensaje),
        acciones,
    );
}

export async function cargarInicio() {
    const contenedor = document.getElementById('aviso-inicio');
    mostrarEstado(contenedor, 'Buscando ofertas para usted…');
    try {
        avisos = await obtenerAvisos();
        actual = 0;
        mostrarAviso();
    } catch (error) {
        mostrarEstado(contenedor, error.message, cargarInicio);
    }
}
