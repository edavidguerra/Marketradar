/**
 * Formato de precios, fechas y nombres de tiendas en español de Colombia.
 */

const formatoPesos = new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
});

const NOMBRES_TIENDA = {
    aliexpress: 'AliExpress',
    shopify: 'Shopify',
    mercadolibre: 'Mercado Libre',
    googleshopping: 'Google Shopping',
};

export function pesos(valor) {
    return formatoPesos.format(Number(valor));
}

export function nombreTienda(codigo) {
    return NOMBRES_TIENDA[codigo] || codigo;
}

export const SIN_VENDEDOR = 'Vendedor no identificado';

/** Separadores entre el nombre de la tienda y lo que el proveedor de datos le pegó. */
const SEPARADOR_VENDEDOR = /\s*(?:[·|•»]|\s-\s|[()[\],;])\s*/;

/** Partes del nombre que son marcadores técnicos (las mismas reglas que NombreVendedor en el servidor). */
const MARCADORES_VENDEDOR = [
    /^[a-z_]*(id|key|token|srsltid|gclid|utm_[a-z]+|ref|sku|merchant|seller)[a-z_]*\s*[=:].*/i,
    /[a-z_]+=\S*/i,
    /\{\{|\}\}|\$\{|%s|<[^>]*>/,
    /^(null|undefined|nan|none|n\/?a|\[?object object\]?|sin nombre|unknown|-+)$/i,
    /^(https?:\/\/|www\.google\.)/i,
    /^[a-z]{1,10}_[a-z0-9_]*\d[a-z0-9_]*$/i,
    /^\d{6,}$/,
    /^(?=[A-Za-z0-9_-]{16,}$)(?=.*\d)(?=.*[A-Za-z])[A-Za-z0-9_-]+$/,
];

const esMarcador = (parte) => MARCADORES_VENDEDOR.some((patron) => patron.test(parte));
const tieneLetras = (parte) => (parte.match(/\p{L}/gu) || []).length >= 2 || /^[a-z]\d{1,3}$/i.test(parte);

/**
 * Nombre comercial del vendedor, sin identificadores internos, plantillas ni enlaces que a veces manda el proveedor.
 * "Alkosto · merchant_id=12345" queda "Alkosto"; "mc_9f3a77c1e2b44d0a8" no deja nada. Nombres como "3M Colombia" o
 * "Black+Decker Store" no se tocan.
 *
 * @param {string|null|undefined} texto nombre recibido
 * @returns {string|null} nombre para mostrar, o null si no queda uno fiable
 */
export function limpiarVendedor(texto) {
    const recibido = String(texto ?? '').trim();
    if (!recibido) return null;
    const partes = recibido.split(SEPARADOR_VENDEDOR).map((parte) => parte.trim()).filter(Boolean);
    if (!esMarcador(recibido) && !partes.some(esMarcador)) return recibido;
    return partes.find((parte) => !esMarcador(parte) && tieneLetras(parte)) ?? null;
}

/**
 * Google Shopping reúne muchas tiendas: se muestra solo la tienda real, por ejemplo "Éxito". Si el nombre que llegó
 * no es fiable, se dice "Vendedor no identificado" (nunca "Google Shopping", que es de donde salen los datos, no
 * quien vende).
 */
export function tiendaDeOferta(oferta) {
    if (oferta.tienda === 'googleshopping') {
        return limpiarVendedor(oferta.vendedor) ?? SIN_VENDEDOR;
    }
    return nombreTienda(oferta.tienda);
}

export function haceCuanto(fechaIso) {
    const minutos = Math.max(0, Math.round((Date.now() - new Date(fechaIso).getTime()) / 60000));
    if (minutos < 1) return 'hace menos de un minuto';
    if (minutos === 1) return 'hace 1 minuto';
    if (minutos < 60) return `hace ${minutos} minutos`;
    const horas = Math.round(minutos / 60);
    return horas === 1 ? 'hace 1 hora' : `hace ${horas} horas`;
}

export function fechaCorta(fechaIso) {
    return new Date(fechaIso).toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' });
}

export function fechaHora(fechaIso) {
    return new Date(fechaIso).toLocaleString('es-CO', {
        day: 'numeric', month: 'long', year: 'numeric', hour: 'numeric', minute: '2-digit',
    });
}

export function estrellas(calificacion) {
    const llenas = Math.round(Number(calificacion));
    return '★'.repeat(llenas) + '☆'.repeat(5 - llenas);
}

export function decimal(valor) {
    return Number(valor).toLocaleString('es-CO', { maximumFractionDigits: 1 });
}

const ESTADOS_PQR = { RADICADA: 'Recibida', EN_PROCESO: 'En revisión', RESPONDIDA: 'Respondida' };
const TIPOS_PQR = { PETICION: 'Petición', QUEJA: 'Queja', RECLAMO: 'Reclamo', SUGERENCIA: 'Sugerencia' };

export const nombreEstadoPqr = (estado) => ESTADOS_PQR[estado] || estado;
export const nombreTipoPqr = (tipo) => TIPOS_PQR[tipo] || tipo;

/** Un valor en otra moneda (USD, EUR...), con decimales: "US$ 1,75". Si es en pesos, como siempre. */
export function dinero(valor, moneda) {
    if (!moneda || moneda === 'COP') return pesos(valor);
    try {
        return new Intl.NumberFormat('es-CO', { style: 'currency', currency: moneda, maximumFractionDigits: 2 })
            .format(Number(valor));
    } catch {
        return `${moneda} ${Number(valor).toLocaleString('es-CO', { maximumFractionDigits: 2 })}`;
    }
}
