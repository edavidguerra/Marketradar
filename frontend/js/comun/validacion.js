/**
 * Validaciones de los formularios en el navegador. Repiten las reglas del servidor para avisar
 * antes de enviar; el servidor vuelve a validar todo.
 */

const CORREO = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
const NOMBRE = /^[\p{L}]+([ '\-][\p{L}]+)*$/u;
const CELULAR = /^3\d{9}$/;

export const REGLAS_CLAVE = [
    { id: 'longitud', texto: 'Entre 8 y 64 caracteres', cumple: (c) => c.length >= 8 && c.length <= 64 },
    { id: 'minuscula', texto: 'Una letra minúscula (a-z)', cumple: (c) => /\p{Ll}/u.test(c) },
    { id: 'mayuscula', texto: 'Una letra mayúscula (A-Z)', cumple: (c) => /\p{Lu}/u.test(c) },
    { id: 'numero', texto: 'Un número (0-9)', cumple: (c) => /\d/.test(c) },
    { id: 'especial', texto: 'Un carácter especial, por ejemplo ! @ # $ %', cumple: (c) => /[^\p{L}\d\s]/u.test(c) },
    { id: 'espacios', texto: 'Sin espacios', cumple: (c) => c.length > 0 && !/\s/.test(c) },
];

export function claveEsSegura(clave) {
    return REGLAS_CLAVE.every((regla) => regla.cumple(clave));
}

export const validar = {
    correo(valor) {
        if (!valor) return 'Escriba su correo electrónico.';
        return CORREO.test(valor) && valor.length <= 150 ? '' : 'Escriba un correo válido, por ejemplo nombre@correo.com.';
    },
    nombre(valor, campo) {
        const limpio = valor.replace(/\s+/g, ' ');
        if (!limpio) return `Escriba ${campo}.`;
        if (limpio.length < 2 || limpio.length > 80) return `Revise ${campo}: debe tener entre 2 y 80 letras.`;
        return NOMBRE.test(limpio) ? '' : `Revise ${campo}: use solo letras, sin números ni símbolos.`;
    },
    nombreOpcional(valor, campo) {
        return valor ? validar.nombre(valor, campo) : '';
    },
    celular(valor) {
        if (!valor) return '';
        const limpio = valor.replace(/[\s-]/g, '').replace(/^\+57/, '');
        return CELULAR.test(limpio) ? '' : 'El celular debe tener 10 números y empezar por 3.';
    },
    documento(tipo, numero) {
        if (!tipo && !numero) return '';
        if (!tipo) return 'Elija el tipo de documento.';
        const limpio = numero.replace(/[.\s]/g, '').toUpperCase();
        if (!limpio) return 'Escriba el número de documento.';
        if (tipo === 'PA') {
            return /^[A-Z0-9]{6,12}$/.test(limpio) ? '' : 'El pasaporte debe tener entre 6 y 12 letras o números.';
        }
        return /^\d{6,10}$/.test(limpio) ? '' : 'El número de cédula debe tener entre 6 y 10 números, sin puntos.';
    },
    fechaNacimiento(valor) {
        if (!valor) return '';
        const fecha = new Date(`${valor}T00:00:00`);
        const hoy = new Date();
        if (fecha > hoy) return 'La fecha de nacimiento no puede ser posterior a hoy.';
        let edad = hoy.getFullYear() - fecha.getFullYear();
        const cumplio = hoy.getMonth() > fecha.getMonth()
            || (hoy.getMonth() === fecha.getMonth() && hoy.getDate() >= fecha.getDate());
        if (!cumplio) edad -= 1;
        if (edad < 18) return 'Debe ser mayor de edad para usar Marketradar.';
        return edad > 120 ? 'Revise el año de la fecha de nacimiento.' : '';
    },
    texto(valor, campo, minimo, maximo) {
        if (!valor) return `Escriba ${campo}.`;
        return valor.length < minimo || valor.length > maximo
            ? `Revise ${campo}: debe tener entre ${minimo} y ${maximo} caracteres.` : '';
    },
};

/**
 * Muestra u oculta el error de un campo y lo marca para lectores de pantalla.
 *
 * @returns {boolean} true si el campo es válido
 */
export function marcarCampo(campo, mensaje) {
    const error = document.getElementById(`error-${campo.id}`);
    campo.setAttribute('aria-invalid', String(Boolean(mensaje)));
    if (error) {
        error.textContent = mensaje;
        error.hidden = !mensaje;
    }
    return !mensaje;
}

/** Lleva el foco al primer campo con error, para que la persona sepa qué corregir. */
export function enfocarPrimerError(formulario) {
    const conError = formulario.querySelector('[aria-invalid="true"]');
    if (conError) conError.focus();
}

/** Conecta una lista de reglas que se marcan con ✓ mientras la persona escribe la contraseña. */
export function conectarReglasClave(campoClave, lista) {
    const elementos = REGLAS_CLAVE.map((regla) => {
        const elemento = document.createElement('li');
        elemento.dataset.regla = regla.id;
        lista.append(elemento);
        return { regla, elemento };
    });
    const actualizar = () => {
        for (const { regla, elemento } of elementos) {
            const cumple = regla.cumple(campoClave.value);
            elemento.className = cumple ? 'cumple' : 'pendiente';
            elemento.textContent = `${cumple ? '✓' : '✗'} ${regla.texto}`;
        }
    };
    campoClave.addEventListener('input', actualizar);
    actualizar();
}

/** Botón para mostrar u ocultar la contraseña escrita. */
export function conectarVerClave(boton, ...campos) {
    boton.addEventListener('click', () => {
        const mostrar = campos[0].type === 'password';
        for (const campo of campos) campo.type = mostrar ? 'text' : 'password';
        boton.setAttribute('aria-pressed', String(mostrar));
        boton.textContent = mostrar ? 'Ocultar contraseña' : 'Mostrar contraseña';
    });
}
