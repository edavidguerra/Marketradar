/**
 * Punto de entrada de la interfaz de administrador. Solo la pueden abrir cuentas con rol ADMINISTRADOR.
 */

import { iniciarAccesibilidad } from '../comun/accesibilidad.js';
import { conectarSalida, exigirSesion, saludo } from '../comun/sesion.js';
import { iniciarPqrAdmin } from './pqr.js';

async function iniciar() {
    iniciarAccesibilidad();
    const sesion = await exigirSesion('ADMINISTRADOR');
    if (!sesion) return;
    document.getElementById('saludo-admin').textContent = saludo(sesion.nombre);
    conectarSalida();
    iniciarPqrAdmin();
}

iniciar();
