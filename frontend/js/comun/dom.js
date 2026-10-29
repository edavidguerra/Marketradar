/**
 * Ayudas para construir la página. Todo el texto se inserta con textContent para evitar inyección de HTML.
 */

export function crear(etiqueta, clase, texto) {
    const elemento = document.createElement(etiqueta);
    if (clase) elemento.className = clase;
    if (texto !== undefined && texto !== null) elemento.textContent = texto;
    return elemento;
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
export function enlaceTienda(texto, url, nombreTienda, clase = 'boton boton-principal') {
    const enlace = crear('a', clase, texto);
    enlace.href = url;
    enlace.target = '_blank';
    enlace.rel = 'noopener noreferrer';
    enlace.addEventListener('click', (evento) => {
        const continuar = window.confirm(`Va a abrir la página de ${nombreTienda} en otra pestaña. `
            + 'Allí podrá comprar el producto.');
        if (!continuar) evento.preventDefault();
    });
    return enlace;
}
