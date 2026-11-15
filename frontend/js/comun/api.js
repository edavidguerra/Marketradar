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
        const error = new Error(datos.mensaje_usuario || 'Algo salió mal. Intente de nuevo.');
        error.codigo = datos.codigo;
        error.estado = respuesta.status;
        throw error;
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
/**
 * Con exacto = true se busca el texto tal cual, sin corregir errores de escritura. precioMinimo y precioMaximo
 * (en pesos, opcionales) dejan solo las ofertas cuyo precio, más el envío si la tienda lo informa, está entre esos
 * valores. original es lo que escribió la persona cuando se buscó una corrección: se manda al pasar de página o
 * filtrar, para que "128 GB" o "nuevo" se sigan exigiendo.
 */
export function buscar(termino, pagina = 1, tamano = 10, exacto = false,
    { precioMinimo, precioMaximo, original } = {}) {
    const parametros = new URLSearchParams({ q: termino, pagina, tamano });
    if (exacto) parametros.set('exacto', 'true');
    if (precioMinimo != null) parametros.set('precio_minimo', precioMinimo);
    if (precioMaximo != null) parametros.set('precio_maximo', precioMaximo);
    if (original && original !== termino) parametros.set('original', original);
    return pedir(`/busqueda?${parametros}`);
}
/** Cómo se elige la recomendación: las reglas y los pesos que usa el servidor. */
export const obtenerComoRecomendamos = () => pedir('/busqueda/como-recomendamos');
export function obtenerSugerencias(texto) {
    return pedir(`/busqueda/sugerencias?${new URLSearchParams({ q: texto })}`);
}
export const obtenerOferta = (id) => pedir(`/ofertas/${encodeURIComponent(id)}`);
export const obtenerHistorialPrecio = (id) => pedir(`/ofertas/${encodeURIComponent(id)}/historial`);
export const obtenerResenas = (id) => pedir(`/ofertas/${encodeURIComponent(id)}/resenas`);
export const traducirResena = (id, indice) => pedir(
    `/ofertas/${encodeURIComponent(id)}/resenas/${encodeURIComponent(indice)}/traduccion`, { metodo: 'POST' });
export const obtenerTasas = () => pedir('/tasas');
/** Compara de 2 a 4 ofertas de la misma búsqueda. */
export const compararOfertas = (ids) => pedir(`/ofertas/comparacion?${new URLSearchParams({ ids: ids.join(',') })}`);

/* Seguimientos y correo */
export const obtenerEstadoCorreo = () => pedir('/usuario/correo');
export const pedirVerificacionCorreo = () => pedir('/usuario/correo/verificacion', { metodo: 'POST' });
export const obtenerSeguimientos = () => pedir('/usuario/seguimientos');
/** tipoAviso: BAJA_PRECIO o PRECIO_OBJETIVO; precioObjetivo en pesos (solo para PRECIO_OBJETIVO). */
export const seguirOferta = (ofertaId, tipoAviso, precioObjetivo, termino) => pedir('/usuario/seguimientos',
    { metodo: 'POST', cuerpo: { oferta_id: ofertaId, tipo_aviso: tipoAviso, precio_objetivo: precioObjetivo, termino } });
/** cambios: { tipo_aviso, precio_objetivo, estado } (estado ACTIVO o PAUSADO). */
export const cambiarSeguimiento = (ofertaId, cambios) =>
    pedir(`/usuario/seguimientos/${encodeURIComponent(ofertaId)}`, { metodo: 'PUT', cuerpo: cambios });
export const dejarDeSeguir = (ofertaId) =>
    pedir(`/usuario/seguimientos/${encodeURIComponent(ofertaId)}`, { metodo: 'DELETE' });
/** Enlaces de los correos: el token llega en el ancla (#token=...) y se manda en el cuerpo, nunca en la dirección. */
export const verificarCorreo = (token) =>
    pedir('/enlaces/verificacion-correo', { metodo: 'POST', cuerpo: { token }, redirigirSinSesion: false });
export const cancelarPorEnlace = (token) =>
    pedir('/enlaces/cancelacion-seguimiento', { metodo: 'POST', cuerpo: { token }, redirigirSinSesion: false });

/* Al por mayor (Alibaba vía API.SH; la clave queda en el servidor) */
export const obtenerEstadoMayoristas = () => pedir('/mayoristas/estado');
/** pagina: hasta qué página cargar (el servidor devuelve todas las cargadas, juntas y ordenadas). */
export function buscarMayoristas(termino, cantidad, unidad, pagina = 1, variantes = '') {
    const parametros = new URLSearchParams({ q: termino, cantidad, unidad, pagina });
    if (variantes) parametros.set('variantes', variantes);
    return pedir(`/mayoristas/busqueda?${parametros}`);
}
export function obtenerDetalleMayorista(referencia, cantidad, unidad, variante) {
    const parametros = new URLSearchParams({ ref: referencia, cantidad, unidad });
    if (variante) parametros.set('variante', variante);
    return pedir(`/mayoristas/producto?${parametros}`);
}

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
