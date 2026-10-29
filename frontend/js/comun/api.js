/**
 * Cliente de la API REST de Marketradar. Envía el token CSRF en cada cambio y, si la sesión
 * terminó, lleva a la persona a la pantalla de ingreso.
 */

const BASE = '/api/v1';
const COOKIE_CSRF = 'XSRF-TOKEN';

function leerCookie(nombre) {
    const par = document.cookie.split('; ').find((cookie) => cookie.startsWith(`${nombre}=`));
    return par ? decodeURIComponent(par.slice(nombre.length + 1)) : null;
}

/**
 * Llama a la API y devuelve el JSON de la respuesta, o null si no hay contenido.
 *
 * @param {string} ruta ruta desde /api/v1
 * @param {{metodo?: string, cuerpo?: object, redirigirSinSesion?: boolean}} opciones
 */
export async function pedir(ruta, { metodo = 'GET', cuerpo, redirigirSinSesion = true } = {}) {
    const cabeceras = { Accept: 'application/json' };
    if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';
    if (metodo !== 'GET') {
        const token = leerCookie(COOKIE_CSRF);
        if (token) cabeceras['X-XSRF-TOKEN'] = token;
    }
    let respuesta;
    try {
        respuesta = await fetch(BASE + ruta, {
            method: metodo,
            headers: cabeceras,
            body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
            credentials: 'same-origin',
        });
    } catch {
        throw new Error('No hay conexión. Revise su internet e intente de nuevo.');
    }
    if (respuesta.status === 401 && redirigirSinSesion) {
        window.location.replace('/index.html?sesion=terminada');
        throw new Error('Su sesión terminó.');
    }
    if (respuesta.status === 204) return null;
    const datos = await respuesta.json().catch(() => ({}));
    if (!respuesta.ok) {
        throw new Error(datos.mensaje_usuario || 'Algo salió mal. Intente de nuevo.');
    }
    return datos;
}

/* Sesión */
export const obtenerSesion = () => pedir('/auth/sesion', { redirigirSinSesion: false });
export const ingresar = (correo, clave) =>
    pedir('/auth/ingreso', { metodo: 'POST', cuerpo: { correo, clave }, redirigirSinSesion: false });
export const registrar = (datos) => pedir('/auth/registro', { metodo: 'POST', cuerpo: datos, redirigirSinSesion: false });
export const salir = () => pedir('/auth/salida', { metodo: 'POST', redirigirSinSesion: false });

/* Búsqueda y ofertas */
export function buscar(termino, pagina = 1, tamano = 10) {
    const parametros = new URLSearchParams({ q: termino, pagina, tamano });
    return pedir(`/busqueda?${parametros}`);
}
export const obtenerOferta = (id) => pedir(`/ofertas/${encodeURIComponent(id)}`);
export const obtenerHistorialPrecio = (id) => pedir(`/ofertas/${encodeURIComponent(id)}/historial`);
export const obtenerTasas = () => pedir('/tasas');

/* Interfaz de usuario */
export const obtenerAvisos = () => pedir('/usuario/avisos');
export const obtenerPerfil = () => pedir('/usuario/perfil');
export const guardarPerfil = (perfil) => pedir('/usuario/perfil', { metodo: 'PUT', cuerpo: perfil });
export const cambiarClave = (datos) => pedir('/usuario/perfil/clave', { metodo: 'PUT', cuerpo: datos });
export const obtenerHistorialBusquedas = () => pedir('/usuario/historial');
export const borrarHistorialBusquedas = () => pedir('/usuario/historial', { metodo: 'DELETE' });
export const obtenerMisPqr = () => pedir('/usuario/pqr');
export const radicarPqr = (pqr) => pedir('/usuario/pqr', { metodo: 'POST', cuerpo: pqr });
export const preguntarAsistente = (mensaje, historial) =>
    pedir('/asistente/mensajes', { metodo: 'POST', cuerpo: { mensaje, historial } });

/* Interfaz de administrador */
export const obtenerResumen = () => pedir('/admin/resumen');
export function listarPqrAdmin(estado) {
    return pedir(estado ? `/admin/pqr?${new URLSearchParams({ estado })}` : '/admin/pqr');
}
export const abrirPqrAdmin = (id) => pedir(`/admin/pqr/${encodeURIComponent(id)}`);
export const responderPqrAdmin = (id, respuesta) =>
    pedir(`/admin/pqr/${encodeURIComponent(id)}/respuesta`, { metodo: 'POST', cuerpo: { respuesta } });
