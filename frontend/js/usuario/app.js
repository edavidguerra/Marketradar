/**
 * Punto de entrada de la interfaz de usuario: verifica la sesión, conecta cada sección y la navegación.
 */

import { iniciarAccesibilidad } from '../comun/accesibilidad.js';
import { conectarSalida, exigirSesion, saludo } from '../comun/sesion.js';
import { iniciarAsistente } from './asistente.js';
import { iniciarBusqueda } from './busqueda.js';
import { cargarHistorial, iniciarHistorial } from './historial.js';
import { cargarInicio } from './inicio.js';
import { alMostrarSeccion, iniciarNavegacion } from './navegacion.js';
import { cargarPerfil, iniciarPerfil } from './perfil.js';
import { cargarPqr, iniciarPqr } from './pqr.js';

async function iniciar() {
    iniciarAccesibilidad();
    const sesion = await exigirSesion('USUARIO');
    if (!sesion) return;

    document.getElementById('saludo').textContent = saludo(sesion.nombre);
    conectarSalida();
    iniciarBusqueda();
    iniciarHistorial();
    iniciarPerfil();
    iniciarPqr();
    iniciarAsistente();

    alMostrarSeccion('inicio', cargarInicio);
    alMostrarSeccion('historial', cargarHistorial);
    alMostrarSeccion('perfil', cargarPerfil);
    alMostrarSeccion('pqr', cargarPqr);
    iniciarNavegacion();
    document.body.classList.remove('cargando');
}

iniciar();
