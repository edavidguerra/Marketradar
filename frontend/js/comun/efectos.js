/**
 * Efectos visuales: las tarjetas aparecen al bajar por la página, los botones hacen una onda al presionarlos,
 * una luz suave sigue al ratón sobre las tarjetas, el encabezado muestra cuánto se ha bajado y la
 * recomendación lanza unas chispas al aparecer.
 *
 * Todo es decorativo y se añade encima de una página que ya se ve completa:
 *  - Si la persona activó "reducir movimiento" en su equipo, no se activa ningún efecto.
 *  - Sin JavaScript o sin IntersectionObserver, el contenido se muestra igual, solo que sin efectos.
 *  - Ningún efecto cambia textos ni lo que anuncia el lector de pantalla (todo lo creado es aria-hidden).
 */

const movimientoReducido = window.matchMedia('(prefers-reduced-motion: reduce)');
const conMovimiento = () => !movimientoReducido.matches;

/** Elementos que entran con un movimiento cuando llegan a la pantalla. */
const REVELAR = '.accesos > li, .resultados > li:not(.esqueleto), .lista-simple > li, .pasos > li, .cifras > li';
/** Elementos que hacen una onda al presionarlos. */
const CON_ONDA = '.boton:not(.boton-enlace), .menu a, .enlace-chat';
/** Tarjetas con una luz que sigue al ratón. */
const CON_LUZ = '.tarjeta:not(.esqueleto), .accesos a, .ingreso-tarjeta';
const COLORES_CHISPAS = ['#f59e0b', '#10b981', '#0ea5e9', '#8b5cf6', '#ef4444'];

/**
 * Las animaciones de entrada ya existen en el CSS; aquí solo se pausan hasta que el elemento se ve,
 * así lo que está más abajo no aparece "ya animado" cuando la persona llega a él.
 */
function iniciarRevelado() {
    if (!('IntersectionObserver' in window) || !('MutationObserver' in window)) return;
    const observador = new IntersectionObserver((entradas) => {
        entradas.forEach((entrada) => {
            if (!entrada.isIntersecting) return;
            entrada.target.classList.add('visible');
            observador.unobserve(entrada.target);
        });
    }, { rootMargin: '0px 0px -6% 0px', threshold: 0.05 });

    const preparar = (elemento) => {
        if (elemento.classList.contains('revelar')) return;
        elemento.classList.add('revelar');
        observador.observe(elemento);
    };
    const revisar = (nodo) => {
        if (nodo.nodeType !== Node.ELEMENT_NODE) return;
        if (nodo.matches(REVELAR)) preparar(nodo);
        nodo.querySelectorAll(REVELAR).forEach(preparar);
        if (nodo.matches('.mvp .sello')) setTimeout(() => lanzarChispas(nodo), 350);
    };

    document.documentElement.classList.add('efectos');
    revisar(document.body);
    new MutationObserver((cambios) => cambios.forEach((cambio) => cambio.addedNodes.forEach(revisar)))
        .observe(document.body, { childList: true, subtree: true });
}

/** Onda que sale desde donde se tocó el botón. */
function iniciarOndas() {
    document.addEventListener('pointerdown', (evento) => {
        if (!conMovimiento() || evento.button !== 0) return;
        const boton = evento.target.closest(CON_ONDA);
        if (!boton || boton.disabled) return;
        const caja = boton.getBoundingClientRect();
        const lado = Math.max(caja.width, caja.height) * 2;
        const onda = document.createElement('span');
        onda.className = 'onda';
        onda.setAttribute('aria-hidden', 'true');
        onda.style.width = `${lado}px`;
        onda.style.height = `${lado}px`;
        onda.style.left = `${evento.clientX - caja.left - lado / 2}px`;
        onda.style.top = `${evento.clientY - caja.top - lado / 2}px`;
        boton.append(onda);
        setTimeout(() => onda.remove(), 700);
    }, { passive: true });
}

/** Luz suave que sigue al ratón dentro de las tarjetas (solo con ratón, no en pantallas táctiles). */
function iniciarLuz() {
    document.addEventListener('pointermove', (evento) => {
        if (evento.pointerType !== 'mouse') return;
        const tarjeta = evento.target.closest?.(CON_LUZ);
        if (!tarjeta) return;
        const caja = tarjeta.getBoundingClientRect();
        tarjeta.style.setProperty('--luz-x', `${Math.round(evento.clientX - caja.left)}px`);
        tarjeta.style.setProperty('--luz-y', `${Math.round(evento.clientY - caja.top)}px`);
    }, { passive: true });
}

/** El encabezado toma sombra al bajar y una barra de colores muestra cuánto falta de la página. */
function iniciarEncabezado() {
    const encabezado = document.querySelector('.encabezado');
    if (!encabezado) return;
    let pendiente = false;
    const actualizar = () => {
        pendiente = false;
        const maximo = document.documentElement.scrollHeight - window.innerHeight;
        encabezado.classList.toggle('desplazado', window.scrollY > 8);
        encabezado.style.setProperty('--avance', maximo > 0 ? (window.scrollY / maximo).toFixed(3) : '0');
    };
    window.addEventListener('scroll', () => {
        if (!pendiente) {
            pendiente = true;
            requestAnimationFrame(actualizar);
        }
    }, { passive: true });
    actualizar();
}

/** Chispas de colores que salen del sello de la recomendación, una sola vez y en poco más de un segundo. */
function lanzarChispas(sello) {
    const tarjeta = sello.closest('.mvp');
    if (!conMovimiento() || !tarjeta || !sello.isConnected) return;
    const chispas = document.createElement('span');
    chispas.className = 'chispas';
    chispas.setAttribute('aria-hidden', 'true');
    chispas.style.left = `${sello.offsetLeft + 22}px`;
    chispas.style.top = `${sello.offsetTop + sello.offsetHeight / 2}px`;
    for (let i = 0; i < 14; i += 1) {
        const chispa = document.createElement('i');
        chispa.style.setProperty('--angulo', `${Math.round((360 / 14) * i + Math.random() * 12)}deg`);
        chispa.style.setProperty('--distancia', `${60 + Math.round(Math.random() * 60)}px`);
        chispa.style.setProperty('--color', COLORES_CHISPAS[i % COLORES_CHISPAS.length]);
        chispas.append(chispa);
    }
    tarjeta.append(chispas);
    setTimeout(() => chispas.remove(), 1500);
}

export function iniciarEfectos() {
    if (!conMovimiento()) return;
    iniciarRevelado();
    iniciarOndas();
    iniciarLuz();
    iniciarEncabezado();
}
