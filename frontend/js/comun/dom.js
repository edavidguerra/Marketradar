/**
 * Ayudas para construir la página. Todo el texto se inserta con textContent para evitar inyección de HTML.
 */

export function crear(etiqueta, clase, texto) {
    const elemento = document.createElement(etiqueta);
    if (clase) elemento.className = clase;
    if (texto !== undefined && texto !== null) elemento.textContent = texto;
    return elemento;
}

/** Ícono decorativo del archivo img/iconos.svg; el lector de pantalla lo ignora. */
export function crearIcono(nombre) {
    const svgNs = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNs, 'svg');
    svg.setAttribute('class', 'icono');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    const uso = document.createElementNS(svgNs, 'use');
    uso.setAttribute('href', `img/iconos.svg#${nombre}`);
    svg.append(uso);
    return svg;
}

export function boton(texto, clase, alPulsar) {
    const elemento = crear('button', `boton ${clase}`, texto);
    elemento.type = 'button';
    elemento.addEventListener('click', alPulsar);
    return elemento;
}

/** Muestra un mensaje de estado y, si se indica, un botón para reintentar. */
export function mostrarEstado(contenedor, mensaje, reintentar) {
    contenedor.replaceChildren(crear('p', null, mensaje));
    if (reintentar) {
        contenedor.append(boton('Intentar de nuevo', 'boton-secundario', reintentar));
    }
}

/** Muestra un aviso de éxito o error en una zona con role="status" o role="alert". */
export function avisar(zona, mensaje, tipo = 'exito') {
    zona.textContent = mensaje;
    zona.className = `aviso-formulario ${tipo}`;
    zona.hidden = !mensaje;
}

/**
 * Devuelve el dominio si la dirección se puede abrir sin riesgo: https, con un servidor con nombre (no una IP ni
 * localhost) y sin usuario o clave antes del servidor ("https://banco.com@otro.com"). Si no, null: no se ofrece.
 * Es la misma regla que aplica el servidor.
 */
export function dominioSeguro(url) {
    try {
        if (typeof url !== 'string' || url.length > 2048) return null;
        const direccion = new URL(url);
        const servidor = direccion.hostname;
        const valido = direccion.protocol === 'https:' && !direccion.username && !direccion.password
            && servidor.includes('.') && !servidor.endsWith('.') && !/^[0-9.]+$|^\[|:/.test(servidor);
        return valido ? servidor.replace(/^www\./, '') : null;
    } catch {
        return null;
    }
}

/** Si el enlace es de Google (una búsqueda o la ficha de Google Shopping) y no de la tienda. */
function esDeGoogle(url) {
    try {
        return /(^|\.)google\.[a-z]{2,3}(\.[a-z]{2})?$/.test(new URL(url).hostname);
    } catch {
        return false;
    }
}

/**
 * Lo que se le dice a la persona antes de abrir el enlace, según a dónde lleva de verdad.
 * nombreTienda puede ser null (el asistente no siempre sabe de qué tienda es el enlace).
 */
export function avisoAntesDeAbrir(dominio, nombreTienda, enlace, url) {
    const tipo = enlace?.tipo ?? (esDeGoogle(url) ? 'busqueda_google' : 'directo');
    const seguridad = ' Nunca le pediremos datos bancarios en Marketradar.';
    if (tipo === 'busqueda_google') {
        return `Va a abrir una búsqueda en Google (${dominio}) con el nombre de esta oferta, en otra pestaña. `
            + `Google no nos dio el enlace de la tienda: busque allí ${nombreTienda || 'la tienda'} y revise que el precio y el `
            + `producto sean los mismos antes de comprar.${seguridad}`;
    }
    if (tipo === 'pagina_google') {
        return `Va a abrir la ficha del producto en Google Shopping (${dominio}), en otra pestaña. Allí están las `
            + `tiendas que lo venden; elija ${nombreTienda || 'una tienda'} y revise el precio antes de comprar.${seguridad}`;
    }
    const redireccion = tipo === 'redireccion' && enlace?.dominio;
    return `Va a abrir ${redireccion ? enlace.dominio : dominio}${nombreTienda ? ` (${nombreTienda})` : ''} en otra pestaña, con conexión `
        + `segura.${redireccion ? ' El enlace pasa primero por Google.' : ''} Allí podrá comprar el producto.`
        + seguridad;
}

/**
 * Botón que abre la oferta en otra pestaña después de avisar a dónde lleva.
 *
 * @param texto texto del botón; debe decir a dónde lleva (por ejemplo "Buscar esta oferta en Google")
 * @param url dirección; si no es https no se ofrece
 * @param nombreTienda tienda de la oferta
 * @param clase clases del botón
 * @param enlace lo que el servidor sabe del enlace ({@code tipo} y {@code dominio}), si lo mandó
 */
export function enlaceTienda(texto, url, nombreTienda, clase = 'boton boton-principal', enlace = null) {
    const dominio = dominioSeguro(url);
    if (!dominio) {
        return crear('p', 'ayuda', 'Esta oferta no tiene un enlace seguro. Búsquela directamente en la tienda.');
    }
    const elemento = crear('a', clase, texto);
    elemento.href = url;
    elemento.target = '_blank';
    elemento.rel = 'noopener noreferrer';
    elemento.addEventListener('click', (evento) => {
        if (!window.confirm(avisoAntesDeAbrir(dominio, nombreTienda, enlace, url))) evento.preventDefault();
    });
    return elemento;
}
