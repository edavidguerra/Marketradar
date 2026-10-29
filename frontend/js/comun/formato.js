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
};

export function pesos(valor) {
    return formatoPesos.format(Number(valor));
}

export function nombreTienda(codigo) {
    return NOMBRES_TIENDA[codigo] || codigo;
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
