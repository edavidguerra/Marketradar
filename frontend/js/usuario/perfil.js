/**
 * Sección Mi perfil: formulario de datos personales y cambio de contraseña.
 */

import { cambiarClave, guardarPerfil, obtenerPerfil } from '../comun/api.js';
import { avisar } from '../comun/dom.js';
import {
    claveEsSegura, conectarReglasClave, conectarVerClave, enfocarPrimerError, marcarCampo, validar,
} from '../comun/validacion.js';

const $ = (id) => document.getElementById(id);

function llenar(perfil) {
    $('perfil-nombres').value = perfil.nombres ?? '';
    $('perfil-apellidos').value = perfil.apellidos ?? '';
    $('perfil-correo').value = perfil.correo ?? '';
    $('perfil-tipo-documento').value = perfil.tipo_documento ?? '';
    $('perfil-numero-documento').value = perfil.numero_documento ?? '';
    $('perfil-fecha-nacimiento').value = perfil.fecha_nacimiento ?? '';
    $('perfil-celular').value = perfil.celular ?? '';
    $('perfil-ciudad').value = perfil.ciudad ?? '';
}

export async function cargarPerfil() {
    try {
        llenar(await obtenerPerfil());
    } catch (error) {
        avisar($('resultado-perfil'), error.message, 'error');
    }
}

function validarPerfil() {
    const tipo = $('perfil-tipo-documento');
    const numero = $('perfil-numero-documento');
    return [
        marcarCampo($('perfil-nombres'), validar.nombre($('perfil-nombres').value.trim(), 'los nombres')),
        marcarCampo($('perfil-apellidos'), validar.nombre($('perfil-apellidos').value.trim(), 'los apellidos')),
        marcarCampo(numero, validar.documento(tipo.value, numero.value.trim())),
        marcarCampo($('perfil-fecha-nacimiento'), validar.fechaNacimiento($('perfil-fecha-nacimiento').value)),
        marcarCampo($('perfil-celular'), validar.celular($('perfil-celular').value.trim())),
        marcarCampo($('perfil-ciudad'), validar.nombreOpcional($('perfil-ciudad').value.trim(), 'la ciudad')),
    ].every(Boolean);
}

function conectarFormularioPerfil() {
    const formulario = $('formulario-perfil');
    formulario.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        avisar($('resultado-perfil'), '');
        if (!validarPerfil()) {
            enfocarPrimerError(formulario);
            return;
        }
        try {
            llenar(await guardarPerfil({
                nombres: $('perfil-nombres').value.trim(),
                apellidos: $('perfil-apellidos').value.trim(),
                tipo_documento: $('perfil-tipo-documento').value || null,
                numero_documento: $('perfil-numero-documento').value.trim() || null,
                fecha_nacimiento: $('perfil-fecha-nacimiento').value || null,
                celular: $('perfil-celular').value.trim() || null,
                ciudad: $('perfil-ciudad').value.trim() || null,
            }));
            avisar($('resultado-perfil'), 'Sus datos quedaron guardados.');
        } catch (error) {
            avisar($('resultado-perfil'), error.message, 'error');
        }
    });
}

function conectarFormularioClave() {
    const formulario = $('formulario-clave');
    const actual = $('clave-actual');
    const nueva = $('clave-nueva');
    const confirmacion = $('clave-confirmacion');
    conectarReglasClave(nueva, $('reglas-clave-nueva'));
    conectarVerClave($('ver-clave-perfil'), actual, nueva, confirmacion);

    formulario.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        avisar($('resultado-clave'), '');
        const valido = [
            marcarCampo(actual, actual.value ? '' : 'Escriba su contraseña actual.'),
            marcarCampo(nueva, claveEsSegura(nueva.value) ? '' : 'La contraseña nueva aún no cumple todas las reglas.'),
            marcarCampo(confirmacion, nueva.value === confirmacion.value ? '' : 'Las dos contraseñas no son iguales.'),
        ].every(Boolean);
        if (!valido) {
            enfocarPrimerError(formulario);
            return;
        }
        try {
            await cambiarClave({
                clave_actual: actual.value, clave_nueva: nueva.value, confirmacion_clave: confirmacion.value,
            });
            formulario.reset();
            nueva.dispatchEvent(new Event('input'));
            avisar($('resultado-clave'), 'Su contraseña cambió. Úsela la próxima vez que ingrese.');
        } catch (error) {
            avisar($('resultado-clave'), error.message, 'error');
        }
    });
}

export function iniciarPerfil() {
    conectarFormularioPerfil();
    conectarFormularioClave();
}
