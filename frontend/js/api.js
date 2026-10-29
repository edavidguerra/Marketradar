/**
 * Cliente de la API REST de Marketradar.
 */

const BASE = '/api/v1';

async function pedir(ruta, opciones = {}) {
    let respuesta;
    try {
        respuesta = await fetch(BASE + ruta, {
            headers: { 'Content-Type': 'application/json' },
            ...opciones,
        });
    } catch {
        throw new Error('No hay conexión. Revise su internet e intente de nuevo.');
    }
    const datos = await respuesta.json().catch(() => ({}));
    if (!respuesta.ok) {
        throw new Error(datos.mensaje_usuario || 'Algo salió mal. Intente de nuevo.');
    }
    return datos;
}

export function buscar(termino, pagina = 1, tamano = 10) {
    const parametros = new URLSearchParams({ q: termino, pagina, tamano });
    return pedir(`/busqueda?${parametros}`);
}

export function obtenerOferta(id) {
    return pedir(`/ofertas/${encodeURIComponent(id)}`);
}

export function obtenerHistorial(id) {
    return pedir(`/ofertas/${encodeURIComponent(id)}/historial`);
}

export function preguntarAsistente(mensaje, historial) {
    return pedir('/asistente/mensajes', {
        method: 'POST',
        body: JSON.stringify({ mensaje, historial }),
    });
}
