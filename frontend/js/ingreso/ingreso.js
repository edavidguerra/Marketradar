/**
 * Pantalla de ingreso y registro. Al entrar, cada rol va a su propia interfaz.
 */

import { iniciarAccesibilidad } from '../comun/accesibilidad.js';
import { ingresar, obtenerSesion, registrar } from '../comun/api.js';
import { avisar } from '../comun/dom.js';
import {
    claveEsSegura, conectarReglasClave, conectarVerClave, enfocarPrimerError, marcarCampo, validar,
} from '../comun/validacion.js';

const MENSAJES_SESION = {
    terminada: 'Su sesión terminó. Ingrese de nuevo para continuar.',
    cerrada: 'Cerró su sesión correctamente. ¡Hasta pronto!',
};

const campo = (id) => document.getElementById(id);

function irAInterfaz(sesion) {
    window.location.replace(sesion.interfaz);
}

function conectarPestanas() {
    const pestanas = [campo('pestana-ingreso'), campo('pestana-registro')];
    const activar = (elegida) => {
        for (const pestana of pestanas) {
            const activa = pestana === elegida;
            pestana.setAttribute('aria-selected', String(activa));
            pestana.tabIndex = activa ? 0 : -1;
            campo(pestana.getAttribute('aria-controls')).hidden = !activa;
        }
        elegida.focus();
    };
    pestanas.forEach((pestana, indice) => {
        pestana.addEventListener('click', () => activar(pestana));
        pestana.addEventListener('keydown', (evento) => {
            if (evento.key === 'ArrowRight' || evento.key === 'ArrowLeft') {
                activar(pestanas[(indice + 1) % pestanas.length]);
            }
        });
    });
}

function conectarIngreso() {
    const formulario = campo('formulario-ingreso');
    const correo = campo('ingreso-correo');
    const clave = campo('ingreso-clave');
    const resultado = campo('resultado-ingreso');
    conectarVerClave(campo('ver-clave-ingreso'), clave);

    formulario.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        avisar(resultado, '');
        const valido = [
            marcarCampo(correo, validar.correo(correo.value.trim())),
            marcarCampo(clave, clave.value ? '' : 'Escriba su contraseña.'),
        ].every(Boolean);
        if (!valido) {
            enfocarPrimerError(formulario);
            return;
        }
        const enviar = formulario.querySelector('[type="submit"]');
        enviar.disabled = true;
        enviar.textContent = 'Ingresando…';
        try {
            irAInterfaz(await ingresar(correo.value.trim(), clave.value));
        } catch (error) {
            avisar(resultado, error.message, 'error');
            clave.value = '';
            clave.focus();
        } finally {
            enviar.disabled = false;
            enviar.textContent = 'Ingresar';
        }
    });
}

function conectarRegistro() {
    const formulario = campo('formulario-registro');
    const nombres = campo('registro-nombres');
    const apellidos = campo('registro-apellidos');
    const correo = campo('registro-correo');
    const clave = campo('registro-clave');
    const confirmacion = campo('registro-confirmacion');
    const resultado = campo('resultado-registro');
    conectarReglasClave(clave, campo('reglas-clave'));
    conectarVerClave(campo('ver-clave-registro'), clave, confirmacion);

    formulario.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        avisar(resultado, '');
        const valido = [
            marcarCampo(nombres, validar.nombre(nombres.value.trim(), 'los nombres')),
            marcarCampo(apellidos, validar.nombre(apellidos.value.trim(), 'los apellidos')),
            marcarCampo(correo, validar.correo(correo.value.trim())),
            marcarCampo(clave, claveEsSegura(clave.value) ? '' : 'La contraseña aún no cumple todas las reglas.'),
            marcarCampo(confirmacion, clave.value === confirmacion.value ? ''
                : 'Las dos contraseñas no son iguales.'),
        ].every(Boolean);
        if (!valido) {
            enfocarPrimerError(formulario);
            return;
        }
        const enviar = formulario.querySelector('[type="submit"]');
        enviar.disabled = true;
        enviar.textContent = 'Creando su cuenta…';
        try {
            irAInterfaz(await registrar({
                nombres: nombres.value.trim(),
                apellidos: apellidos.value.trim(),
                correo: correo.value.trim(),
                clave: clave.value,
                confirmacion_clave: confirmacion.value,
            }));
        } catch (error) {
            avisar(resultado, error.message, 'error');
        } finally {
            enviar.disabled = false;
            enviar.textContent = 'Crear mi cuenta';
        }
    });
}

async function iniciar() {
    iniciarAccesibilidad();
    const motivo = new URLSearchParams(window.location.search).get('sesion');
    if (MENSAJES_SESION[motivo]) {
        avisar(campo('aviso-sesion'), MENSAJES_SESION[motivo], 'info');
    }
    try {
        const sesion = await obtenerSesion();
        if (sesion) {
            irAInterfaz(sesion);
            return;
        }
    } catch {
        /* Sin conexión con el servidor: se muestra el formulario igual. */
    }
    conectarPestanas();
    conectarIngreso();
    conectarRegistro();
}

iniciar();
