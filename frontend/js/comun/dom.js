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

/** Abre un enlace de tienda en otra pestaña, después de avisar a la persona. */
/** Devuelve el dominio si la dirección es https; si no, null (no se ofrece un enlace que no sea seguro). */
export function dominioSeguro(url) {
    try {
        const direccion = new URL(url);
        return direccion.protocol === 'https:' ? direccion.hostname.replace(/^www\./, '') : null;
    } catch {
        return null;
    }
}

export function enlaceTienda(texto, url, nombreTienda, clase = 'boton boton-principal') {
    const dominio = dominioSeguro(url);
    if (!dominio) {
        return crear('p', 'ayuda', 'Esta oferta no tiene un enlace seguro. Búsquela directamente en la tienda.');
    }
    const enlace = crear('a', clase, texto);
    enlace.href = url;
    enlace.target = '_blank';
    enlace.rel = 'noopener noreferrer';
    enlace.addEventListener('click', (evento) => {
        const continuar = window.confirm(`Va a abrir ${dominio} (${nombreTienda}) en otra pestaña, con conexión `
            + 'segura. Allí podrá comprar el producto. Nunca le pediremos datos bancarios en Marketradar.');
        if (!continuar) evento.preventDefault();
    });
    return enlace;
}
