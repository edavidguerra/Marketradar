/**
 * Cómo se lee un seguimiento que llega de la API. El servidor manda {@code estado} y {@code tipo_aviso} en minúsculas
 * ("activo", "pausado", "precio_objetivo"); aquí se pasan a una sola forma (mayúsculas) y todo lo demás de la pantalla
 * usa esa forma. No toca la página, así se prueba con Node.
 */

import { dinero } from '../comun/formato.js';

export const ESTADOS = Object.freeze({
    ACTIVO: { etiqueta: 'Activo', accion: 'Pausar avisos', siguiente: 'PAUSADO', clase: 'estado-activo' },
    PAUSADO: { etiqueta: 'En pausa', accion: 'Reanudar avisos', siguiente: 'ACTIVO', clase: 'estado-pausado' },
});

const TIPOS = new Set(['BAJA_PRECIO', 'PRECIO_OBJETIVO']);

function mayusculas(valor) {
    return typeof valor === 'string' ? valor.trim().toUpperCase() : null;
}

/**
 * Estado del seguimiento en la forma única. Si llega un valor que no se conoce, devuelve {@code null}: no se adivina
 * si está activo o en pausa.
 *
 * @param valor estado tal como lo manda la API
 * @return 'ACTIVO', 'PAUSADO' o null
 */
export function normalizarEstado(valor) {
    const estado = mayusculas(valor);
    return estado && Object.hasOwn(ESTADOS, estado) ? estado : null;
}

/**
 * @param valor tipo de aviso tal como lo manda la API
 * @return 'BAJA_PRECIO', 'PRECIO_OBJETIVO' o null
 */
export function normalizarTipo(valor) {
    const tipo = mayusculas(valor);
    return tipo && TIPOS.has(tipo) ? tipo : null;
}

/**
 * Lo que la pantalla necesita de un seguimiento, ya normalizado.
 *
 * @param seguimiento DTO de la API
 * @return { estado, tipo, etiqueta, accion, siguiente, clase, textoAviso }
 */
export function leerSeguimiento(seguimiento) {
    const estado = normalizarEstado(seguimiento.estado);
    const tipo = normalizarTipo(seguimiento.tipo_aviso);
    const datos = estado ? ESTADOS[estado] : null;
    return {
        estado,
        tipo,
        etiqueta: datos ? datos.etiqueta : 'Estado sin confirmar',
        // Con un estado desconocido no se ofrece ni pausar ni reanudar.
        accion: datos ? datos.accion : null,
        siguiente: datos ? datos.siguiente : null,
        clase: datos ? datos.clase : 'estado-desconocido',
        textoAviso: textoAviso(tipo, seguimiento),
    };
}

/**
 * Frase de qué se le avisa a la persona.
 *
 * @param tipo tipo normalizado
 * @param seguimiento DTO (precio_objetivo y moneda)
 */
export function textoAviso(tipo, seguimiento) {
    if (tipo === 'PRECIO_OBJETIVO') {
        if (seguimiento.precio_objetivo == null) return 'Le avisamos cuando llegue al precio que eligió.';
        return `Le avisamos cuando cueste ${dinero(seguimiento.precio_objetivo, seguimiento.moneda)} o menos.`;
    }
    return 'Le avisamos cuando baje de precio.';
}

/**
 * Cambia el estado de un seguimiento: deja el botón deshabilitado mientras espera, y solo da por hecho el cambio si
 * la API lo confirma (con el seguimiento que devuelve). Si falla, el estado que se ve no cambia.
 *
 * @param seguimiento DTO actual
 * @param cambiar función de la API (ofertaId, cambios) → seguimiento actualizado
 * @param boton botón pulsado (o algo con {@code disabled})
 * @return { ok: true, seguimiento } o { ok: false, error }
 */
export async function alternarEstado(seguimiento, cambiar, boton) {
    const { siguiente } = leerSeguimiento(seguimiento);
    if (!siguiente) return { ok: false, error: new Error('No sabemos si este seguimiento está activo o en pausa.') };
    if (boton) boton.disabled = true;
    try {
        // La API acepta el estado en mayúsculas o minúsculas; se manda como siempre: ACTIVO o PAUSADO.
        const actualizado = await cambiar(seguimiento.oferta_id, { estado: siguiente });
        if (!actualizado || normalizarEstado(actualizado.estado) !== siguiente) {
            return { ok: false, error: new Error('No pudimos confirmar el cambio. Recargue la página e intente de nuevo.') };
        }
        return { ok: true, seguimiento: actualizado };
    } catch (error) {
        return { ok: false, error };
    } finally {
        if (boton) boton.disabled = false;
    }
}
