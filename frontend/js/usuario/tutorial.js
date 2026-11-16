/**
 * Tutorial interactivo. En vez de un texto largo, la persona elige un tema corto ("Buscar un producto", "Seguir un
 * producto"...) y el recorrido le señala en la pantalla de verdad dónde tocar, un paso a la vez:
 * <ul>
 *   <li>Un recuadro grande con el paso ("Paso 2 de 5"), una explicación corta y los botones Atrás, Siguiente y
 *       Salir. Nunca avanza solo: cada persona va a su ritmo.</li>
 *   <li>Lo demás se oscurece y lo que se explica queda iluminado y se puede tocar. Si todavía no existe (por ejemplo,
 *       los resultados antes de buscar), el recuadro muestra cómo se ve.</li>
 *   <li>Algunos pasos tienen "Probar ahora" para practicar sin miedo: nada se compra ni se envía.</li>
 *   <li>Teclado: Tab recorre los botones del recuadro, Esc sale. Los lectores de pantalla oyen cada paso.</li>
 * </ul>
 * La primera vez que la persona entra se le ofrece el tutorial en el inicio; puede decir "Ahora no".
 */

import { crear, crearIcono } from '../comun/dom.js';
import { irA } from './navegacion.js';

const CLAVE_VISTOS = 'marketradar.tutorial.vistos';
const CLAVE_INVITACION = 'marketradar.tutorial.invitacion';
const MARGEN = 8;

/** Lo que se ve en el recuadro cuando lo explicado todavía no aparece en la pantalla. */
function ejemploBotones(...textos) {
    const ejemplo = crear('div', 'recorrido-ejemplo');
    ejemplo.setAttribute('role', 'img');
    ejemplo.setAttribute('aria-label', `Así se ve: ${textos.join(', ')}`);
    ejemplo.append(crear('span', 'recorrido-ejemplo-titulo', 'Así se ve:'));
    for (const texto of textos) ejemplo.append(crear('span', 'boton-falso', texto));
    return ejemplo;
}

/**
 * Qué hace el botón de una oferta según su destino real (el tipo de enlace que manda el servidor). Sin una oferta
 * cargada se explican los dos casos.
 *
 * @param destino 'directo', 'redireccion', 'pagina_google', 'busqueda_google' o nada
 */
export function textoIrALaTienda(destino) {
    const cierre = ' La compra se hace allá, no en Marketradar. Revise que el precio sea el mismo.';
    switch (destino) {
        case 'directo':
        case 'redireccion':
            return `Este botón abre la publicación de esta oferta en la tienda.${cierre}`;
        case 'pagina_google':
            return 'Este botón abre la ficha del producto en Google Shopping, donde aparecen las tiendas que lo venden. '
                + `Elija allí la tienda.${cierre}`;
        case 'busqueda_google':
            return 'Este botón no abre la tienda directamente: busca la oferta en Google, porque la tienda no nos dio '
                + `un enlace directo. Busque en los resultados la misma tienda y el mismo producto.${cierre}`;
        default:
            return 'Algunos botones abren directamente la publicación en la tienda («Ver la oferta en…») y otros buscan '
                + `la oferta en Google («Buscar esta oferta en Google»). El texto del botón le dice a dónde lleva.${cierre}`;
    }
}

/** El texto del paso; si depende de lo que se señala, se calcula con ese elemento (o null si no hay). */
const textoDelPaso = (paso, objetivo = visible(paso.objetivo)) => (typeof paso.texto === 'function'
    ? paso.texto(objetivo) : paso.texto);

function escribirEjemplo() {
    const campo = document.getElementById('campo-busqueda');
    campo.value = 'tensiómetro';
    campo.dispatchEvent(new Event('input', { bubbles: true }));
}

function pulsar(id) {
    return () => document.getElementById(id)?.click();
}

/** Temas del tutorial. Cada paso puede llevar a una sección y señalar un elemento. */
export const TEMAS = [
    {
        id: 'primeros-pasos',
        icono: 'casa',
        titulo: 'Primeros pasos',
        descripcion: 'Dónde está cada cosa en la pantalla.',
        recomendado: true,
        pasos: [
            {
                titulo: '¡Bienvenido!',
                texto: 'Le vamos a mostrar, en la pantalla de verdad, dónde está cada cosa. Lea este recuadro y pulse '
                    + '«Siguiente». Puede volver atrás o salir cuando quiera. Mientras aprende no se compra ni se envía '
                    + 'nada.',
            },
            {
                seccion: 'inicio',
                objetivo: '.menu',
                titulo: 'Este es el menú',
                texto: 'Desde aquí va a cada parte de Marketradar. La opción en la que está se ve pintada de color.',
            },
            {
                objetivo: '.menu a[href="#buscar"]',
                titulo: 'Buscar',
                texto: 'Aquí escribe el producto que quiere comprar y le decimos en qué tienda sale más barato.',
            },
            {
                objetivo: '.menu a[href="#seguimientos"]',
                titulo: 'Seguimientos',
                texto: 'Aquí están los productos que usted sigue. Le avisamos por correo cuando bajan de precio.',
            },
            {
                objetivo: '.menu a[href="#mayoristas"]',
                titulo: 'Emprender',
                texto: 'Si tiene o quiere montar un negocio, aquí busca proveedores que venden muchas unidades a la '
                    + 'vez.',
            },
            {
                objetivo: '#abrir-asistente',
                titulo: '¿Necesita ayuda?',
                texto: 'Este botón abre el asistente. Escríbale su pregunta con sus palabras, como si le hablara a una '
                    + 'persona.',
            },
            {
                objetivo: '#salir',
                titulo: 'Salir',
                texto: 'Cuando termine, pulse «Salir». Marketradar nunca le pide datos del banco: la compra se hace en '
                    + 'la página de la tienda.',
            },
        ],
    },
    {
        id: 'buscar',
        icono: 'buscar',
        titulo: 'Buscar un producto',
        descripcion: 'Escribir lo que quiere y ver dónde sale más barato.',
        pasos: [
            {
                seccion: 'buscar',
                objetivo: '#campo-busqueda',
                titulo: 'Escriba qué quiere comprar',
                texto: 'Toque este espacio y escriba el producto, por ejemplo «tensiómetro». Si sabe la marca o el '
                    + 'tamaño, escríbalos también.',
                practica: { texto: 'Escribir «tensiómetro» por mí', hacer: escribirEjemplo },
            },
            {
                objetivo: '#formulario-busqueda button[type="submit"]',
                titulo: 'Pulse «Buscar»',
                texto: 'También puede pulsar la tecla Enter. La búsqueda tarda unos segundos.',
            },
            {
                objetivo: '#producto-mvp',
                titulo: 'La mejor opción',
                texto: 'Arriba aparece la oferta que le recomendamos y por qué: precio, envío y opiniones. Si ninguna '
                    + 'tienda tiene lo que pidió, se lo decimos.',
                ejemplo: () => ejemploBotones('★ Mejor opción de compra'),
            },
            {
                objetivo: '#lista-resultados .tarjeta .acciones a',
                titulo: 'Ir a la tienda',
                // El texto depende de a dónde lleva el botón de la oferta que se señala (sus datos de enlace).
                texto: (boton) => textoIrALaTienda(boton?.dataset?.destino),
                ejemplo: () => ejemploBotones('Ver la oferta en la tienda', 'Buscar esta oferta en Google'),
            },
            {
                objetivo: '#filtro-precio',
                titulo: 'Filtrar por precio',
                texto: 'Después de buscar, escriba cuánto quiere pagar como mínimo y como máximo, y pulse «Aplicar».',
                ejemplo: () => ejemploBotones('Desde $', 'Hasta $', 'Aplicar'),
            },
        ],
    },
    {
        id: 'comparar',
        icono: 'balanza',
        titulo: 'Comparar productos',
        descripcion: 'Ver de 2 a 4 productos lado a lado.',
        pasos: [
            {
                seccion: 'buscar',
                objetivo: '#lista-resultados [data-comparar]',
                titulo: 'Elija los productos',
                texto: 'En cada resultado hay un botón «Comparar». Púlselo en 2, 3 o 4 productos.',
                ejemplo: () => ejemploBotones('Comparar'),
            },
            {
                objetivo: '#barra-comparacion',
                titulo: 'Sus productos elegidos',
                texto: 'Abajo aparece una barra con las fotos de los que eligió. Si se equivocó, quite uno con su '
                    + 'botón «Quitar».',
                ejemplo: () => ejemploBotones('2 de 4 productos para comparar', 'Ver comparación'),
            },
            {
                objetivo: '#abrir-comparacion',
                titulo: 'Vea la comparación',
                texto: 'Pulse «Ver comparación». Verá una tabla con un producto en cada columna. Lo que la tienda no '
                    + 'informa dice «No disponible».',
            },
            {
                titulo: 'Solo lo que cambia',
                texto: 'En la tabla, marque «Mostrar solo las diferencias» para ver únicamente lo que cambia de un '
                    + 'producto a otro.',
                ejemplo: () => ejemploBotones('☐ Mostrar solo las diferencias'),
            },
        ],
    },
    {
        id: 'seguir',
        icono: 'campana',
        titulo: 'Seguir un producto',
        descripcion: 'Recibir un correo cuando baje de precio.',
        pasos: [
            {
                seccion: 'buscar',
                objetivo: '#lista-resultados .boton-seguir',
                titulo: 'Pulse «Seguir producto»',
                texto: 'Está en cada resultado. Elija si quiere el aviso cuando baje el precio o cuando llegue al '
                    + 'precio que usted espera.',
                ejemplo: () => ejemploBotones('Seguir producto'),
            },
            {
                titulo: 'Revise su correo',
                texto: 'Le mandamos un correo para confirmar. La primera vez también le pedimos verificar su correo: '
                    + 'abra el mensaje y pulse el botón que trae.',
            },
            {
                seccion: 'seguimientos',
                objetivo: '.menu a[href="#seguimientos"]',
                titulo: 'Sus seguimientos',
                texto: 'Aquí ve todos los productos que sigue y el último precio que encontramos.',
            },
            {
                objetivo: '#lista-seguimientos .acciones',
                titulo: 'Pausar o dejar de seguir',
                texto: 'En cada producto puede pausar los avisos o dejar de seguirlo. Cada correo trae también un '
                    + 'enlace para dejar de seguirlo.',
                ejemplo: () => ejemploBotones('Pausar avisos', 'Dejar de seguir'),
            },
        ],
    },
    {
        id: 'emprender',
        icono: 'caja',
        titulo: 'Emprender',
        descripcion: 'Comprar muchas unidades para un negocio.',
        pasos: [
            {
                seccion: 'mayoristas',
                objetivo: '#termino-mayorista',
                titulo: '¿Qué producto busca?',
                texto: 'Escríbalo aquí. Los proveedores responden mejor en inglés: por ejemplo, «phone case» para '
                    + 'forros de celular.',
            },
            {
                objetivo: '#cantidad-mayorista',
                titulo: '¿Cuántas unidades?',
                texto: 'Escriba solo el número de unidades que necesita, por ejemplo 500.',
            },
            {
                objetivo: '#unidad-mayorista',
                titulo: '¿En qué unidad?',
                texto: 'Elija piezas, pares o paquetes. No son lo mismo: un paquete puede traer varias piezas.',
            },
            {
                objetivo: '#lista-mayoristas .tarjeta',
                titulo: 'Lea el subtotal',
                texto: 'Le mostramos el pedido mínimo, el precio según la cantidad y el subtotal. El envío y los '
                    + 'impuestos no están incluidos. La compra se hace en Alibaba.',
                ejemplo: () => ejemploBotones('Pedido mínimo: 100 piezas', 'Subtotal de mercancía'),
            },
        ],
    },
    {
        id: 'letra',
        icono: 'letra',
        titulo: 'Letra y colores',
        descripcion: 'Agrandar la letra y ver mejor la pantalla.',
        pasos: [
            {
                objetivo: '#letra-mas',
                titulo: 'Letra más grande',
                texto: 'Pulse «A+» para agrandar la letra. Puede pulsarlo varias veces.',
                practica: { texto: 'Probar ahora', hacer: pulsar('letra-mas') },
            },
            {
                objetivo: '#letra-menos',
                titulo: 'Letra más pequeña',
                texto: '«A−» la vuelve a achicar.',
                practica: { texto: 'Probar ahora', hacer: pulsar('letra-menos') },
            },
            {
                objetivo: '#contraste',
                titulo: 'Alto contraste',
                texto: 'Pone la pantalla en negro y amarillo para leer mejor. Púlselo otra vez para volver. '
                    + 'Marketradar recuerda lo que usted eligió.',
            },
        ],
    },
    {
        id: 'cuenta',
        icono: 'persona',
        titulo: 'Su cuenta y sus dudas',
        descripcion: 'Historial, perfil y cómo escribirnos.',
        pasos: [
            {
                objetivo: '.menu a[href="#historial"]',
                titulo: 'Historial',
                texto: 'Aquí quedan sus búsquedas. Pulse «Buscar otra vez» para ver los precios de hoy sin volver a '
                    + 'escribir.',
            },
            {
                objetivo: '.menu a[href="#perfil"]',
                titulo: 'Mi perfil',
                texto: 'Aquí revisa y corrige sus datos y cambia su contraseña.',
            },
            {
                objetivo: '.menu a[href="#pqr"]',
                titulo: 'PQR',
                texto: 'Si tiene una pregunta, una queja o una sugerencia, escríbala aquí. La respuesta aparece en la '
                    + 'misma sección.',
            },
        ],
    },
    {
        id: 'asistente',
        icono: 'chat',
        titulo: 'Pedir ayuda',
        descripcion: 'Preguntarle al asistente con sus palabras.',
        pasos: [
            {
                objetivo: '#abrir-asistente',
                titulo: 'El botón de ayuda',
                texto: 'Está siempre abajo a la derecha. Púlselo para abrir el asistente.',
            },
            {
                titulo: 'Pregunte como le hablaría a una persona',
                texto: 'Por ejemplo: «¿dónde está más barato un tensiómetro?». El asistente le responde y le da '
                    + 'enlaces a las tiendas.',
            },
        ],
    },
];

function leerVistos() {
    try {
        const valor = JSON.parse(localStorage.getItem(CLAVE_VISTOS) || '[]');
        return new Set(Array.isArray(valor) ? valor : []);
    } catch {
        return new Set();
    }
}

function guardarVisto(id) {
    try {
        const vistos = leerVistos();
        vistos.add(id);
        localStorage.setItem(CLAVE_VISTOS, JSON.stringify([...vistos]));
    } catch {
        // Sin almacenamiento el tutorial funciona igual; solo no se marca como visto.
    }
}

function movimientoReducido() {
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/** Elemento que se ve en pantalla, o null si no existe o está oculto. */
function visible(selector) {
    if (!selector) return null;
    for (const elemento of document.querySelectorAll(selector)) {
        if (elemento.closest('[hidden]')) continue;
        const caja = elemento.getBoundingClientRect();
        if (caja.width > 0 && caja.height > 0) return elemento;
    }
    return null;
}

/** Recorrido guiado de un tema. Solo hay uno abierto a la vez. */
class Recorrido {

    constructor(tema, alTerminar) {
        this.tema = tema;
        this.pasos = [...tema.pasos, {
            titulo: '¡Muy bien, terminó!',
            texto: `Ya sabe: ${tema.titulo.toLowerCase()}. Puede repetir este tema cuando quiera o aprender otro.`,
            final: true,
        }];
        this.alTerminar = alTerminar;
        this.indice = 0;
        this.objetivo = null;
        this.cuadro = 0;

        this.capa = crear('div', 'recorrido');
        this.velos = ['arriba', 'abajo', 'izquierda', 'derecha'].map((lado) => {
            const velo = crear('div', `recorrido-velo velo-${lado}`);
            velo.addEventListener('click', () => this.globo.querySelector('h2').focus());
            return velo;
        });
        this.anillo = crear('div', 'recorrido-anillo');
        this.anillo.setAttribute('aria-hidden', 'true');

        this.globo = crear('div', 'recorrido-globo');
        this.globo.setAttribute('role', 'dialog');
        this.globo.setAttribute('aria-modal', 'true');
        this.globo.setAttribute('aria-labelledby', 'recorrido-titulo');
        this.globo.setAttribute('aria-describedby', 'recorrido-texto');

        this.capa.append(...this.velos, this.anillo, this.globo);
        document.body.append(this.capa);
        document.body.classList.add('con-recorrido');

        this.alTeclear = (evento) => this.teclear(evento);
        this.alMover = () => this.programarUbicacion();
        document.addEventListener('keydown', this.alTeclear, true);
        window.addEventListener('resize', this.alMover);
        window.addEventListener('scroll', this.alMover, true);
        this.observador = new ResizeObserver(this.alMover);
        this.observador.observe(document.body);

        this.ir(0);
    }

    ir(indice) {
        this.indice = indice;
        const paso = this.pasos[indice];
        if (paso.seccion && window.location.hash !== `#${paso.seccion}`) irA(paso.seccion);
        this.pintarGlobo(paso);
        // Espera a que la sección se pinte antes de buscar lo que hay que señalar.
        requestAnimationFrame(() => requestAnimationFrame(() => {
            this.objetivo = visible(paso.objetivo);
            if (typeof paso.texto === 'function') {
                const texto = this.globo.querySelector('#recorrido-texto');
                if (texto) texto.textContent = textoDelPaso(paso, this.objetivo);
            }
            this.globo.querySelector('.recorrido-ejemplo')?.toggleAttribute('hidden', Boolean(this.objetivo));
            if (this.objetivo) this.mostrarObjetivo();
            this.ubicar();
            this.globo.querySelector('h2').focus();
        }));
    }

    /**
     * Lleva a la vista lo que se explica. En el celular lo deja arriba, porque el recuadro ocupa la parte de abajo
     * de la pantalla; en una pantalla grande lo deja en el centro.
     */
    mostrarObjetivo() {
        const comportamiento = movimientoReducido() ? 'auto' : 'smooth';
        if (window.innerWidth >= 640 || getComputedStyle(this.objetivo).position === 'fixed') {
            this.objetivo.scrollIntoView({ block: 'center', inline: 'center', behavior: comportamiento });
            return;
        }
        const arriba = this.objetivo.getBoundingClientRect().top + window.scrollY - 16;
        window.scrollTo({ top: Math.max(0, arriba), behavior: comportamiento });
    }

    pintarGlobo(paso) {
        const total = this.pasos.length - 1;
        const cabecera = crear('div', 'recorrido-cabecera');
        const numero = crear('p', 'recorrido-numero', paso.final ? this.tema.titulo
            : `Paso ${this.indice + 1} de ${total}`);
        const barra = crear('div', 'recorrido-progreso');
        barra.setAttribute('aria-hidden', 'true');
        const relleno = crear('span');
        relleno.style.width = `${Math.round(((paso.final ? total : this.indice + 1) / total) * 100)}%`;
        barra.append(relleno);
        const salir = crear('button', 'recorrido-salir');
        salir.type = 'button';
        salir.setAttribute('aria-label', 'Salir del tutorial');
        salir.append(crearIcono('cerrar'), document.createTextNode('Salir'));
        salir.addEventListener('click', () => this.cerrar(false));
        cabecera.append(numero, salir);

        const titulo = crear('h2', null, paso.titulo);
        titulo.id = 'recorrido-titulo';
        titulo.tabIndex = -1;
        const texto = crear('p', 'recorrido-texto', textoDelPaso(paso));
        texto.id = 'recorrido-texto';
        const contenido = [cabecera, barra, titulo, texto];
        if (paso.ejemplo) contenido.push(paso.ejemplo());
        if (paso.practica) {
            const practicar = crear('button', 'boton boton-secundario recorrido-practica');
            practicar.type = 'button';
            practicar.append(crearIcono('estrella'), document.createTextNode(paso.practica.texto));
            practicar.addEventListener('click', () => {
                paso.practica.hacer();
                this.programarUbicacion();
            });
            contenido.push(practicar);
        }

        const acciones = crear('div', 'recorrido-acciones');
        if (paso.final) {
            const otro = crear('button', 'boton boton-principal', 'Elegir otro tema');
            otro.type = 'button';
            otro.addEventListener('click', () => this.cerrar(true));
            const repetir = crear('button', 'boton boton-secundario', 'Repetir este tema');
            repetir.type = 'button';
            repetir.addEventListener('click', () => this.ir(0));
            acciones.append(repetir, otro);
        } else {
            const atras = crear('button', 'boton boton-secundario');
            atras.type = 'button';
            atras.append(crearIcono('anterior'), document.createTextNode('Atrás'));
            atras.disabled = this.indice === 0;
            atras.addEventListener('click', () => this.ir(this.indice - 1));
            const siguiente = crear('button', 'boton boton-principal');
            siguiente.type = 'button';
            siguiente.append(document.createTextNode(this.indice === total - 1 ? 'Terminar' : 'Siguiente'),
                crearIcono('siguiente'));
            siguiente.addEventListener('click', () => this.ir(this.indice + 1));
            acciones.append(atras, siguiente);
        }
        contenido.push(acciones);
        this.globo.replaceChildren(...contenido);
        this.globo.classList.toggle('final', Boolean(paso.final));
    }

    programarUbicacion() {
        if (this.cuadro) return;
        this.cuadro = requestAnimationFrame(() => {
            this.cuadro = 0;
            this.ubicar();
        });
    }

    /** Oscurece todo menos lo que se explica y pone el recuadro donde no lo tape. */
    ubicar() {
        if (this.objetivo && !this.objetivo.isConnected) this.objetivo = visible(this.pasos[this.indice].objetivo);
        const ancho = window.innerWidth;
        const alto = window.innerHeight;
        const [arriba, abajo, izquierda, derecha] = this.velos;
        const angosto = ancho < 640;
        this.globo.classList.remove('centrado', 'hoja-abajo', 'hoja-arriba');
        this.globo.style.removeProperty('top');
        this.globo.style.removeProperty('left');

        if (!this.objetivo) {
            Object.assign(arriba.style, { top: '0', left: '0', width: '100%', height: '100%' });
            for (const velo of [abajo, izquierda, derecha]) velo.style.height = '0';
            this.anillo.hidden = true;
            this.globo.classList.add(angosto ? 'hoja-abajo' : 'centrado');
            return;
        }

        const caja = this.objetivo.getBoundingClientRect();
        const t = Math.max(0, caja.top - MARGEN);
        const l = Math.max(0, caja.left - MARGEN);
        const b = Math.min(alto, caja.bottom + MARGEN);
        const r = Math.min(ancho, caja.right + MARGEN);
        Object.assign(arriba.style, { top: '0', left: '0', width: '100%', height: `${t}px` });
        Object.assign(abajo.style, { top: `${b}px`, left: '0', width: '100%', height: `${Math.max(0, alto - b)}px` });
        Object.assign(izquierda.style, { top: `${t}px`, left: '0', width: `${l}px`, height: `${Math.max(0, b - t)}px` });
        Object.assign(derecha.style, { top: `${t}px`, left: `${r}px`, width: `${Math.max(0, ancho - r)}px`,
            height: `${Math.max(0, b - t)}px` });
        this.anillo.hidden = false;
        Object.assign(this.anillo.style, { top: `${t}px`, left: `${l}px`, width: `${r - l}px`, height: `${b - t}px` });

        if (angosto) {
            // En el celular el recuadro va abajo, o arriba si lo señalado está en la mitad de abajo.
            this.globo.classList.add((caja.top + caja.bottom) / 2 > alto / 2 ? 'hoja-arriba' : 'hoja-abajo');
            return;
        }
        const globo = this.globo.getBoundingClientRect();
        let top = b + 12;
        if (top + globo.height > alto - 12) top = t - globo.height - 12;
        if (top < 12) top = Math.max(12, alto - globo.height - 12);
        const left = Math.min(Math.max(12, l), ancho - globo.width - 12);
        this.globo.style.top = `${top}px`;
        this.globo.style.left = `${left}px`;
    }

    teclear(evento) {
        if (evento.key === 'Escape') {
            evento.preventDefault();
            evento.stopPropagation();
            this.cerrar(false);
            return;
        }
        if (evento.key !== 'Tab') return;
        // El foco se queda en el recuadro y en lo que se está explicando.
        const enfocables = [...this.globo.querySelectorAll('button:not([disabled])')];
        if (this.objetivo?.matches('a, button, input, select, textarea')) enfocables.push(this.objetivo);
        if (enfocables.length === 0) return;
        const actual = enfocables.indexOf(document.activeElement);
        evento.preventDefault();
        const siguiente = actual === -1 ? 0
            : (actual + (evento.shiftKey ? -1 : 1) + enfocables.length) % enfocables.length;
        enfocables[siguiente].focus();
    }

    cerrar(completo) {
        if (completo || this.pasos[this.indice].final) guardarVisto(this.tema.id);
        cancelAnimationFrame(this.cuadro);
        document.removeEventListener('keydown', this.alTeclear, true);
        window.removeEventListener('resize', this.alMover);
        window.removeEventListener('scroll', this.alMover, true);
        this.observador.disconnect();
        this.capa.remove();
        document.body.classList.remove('con-recorrido');
        this.alTerminar(this.tema, completo);
    }
}

let abierto = null;

function pintarTemas() {
    const lista = document.getElementById('lista-temas');
    if (!lista) return;
    const vistos = leerVistos();
    lista.replaceChildren(...TEMAS.map((tema) => {
        const elemento = crear('li');
        const boton = crear('button', `tema${tema.recomendado ? ' recomendado' : ''}${vistos.has(tema.id) ? ' visto' : ''}`);
        boton.type = 'button';
        boton.dataset.tema = tema.id;
        const icono = crear('span', 'tema-icono');
        icono.append(crearIcono(tema.icono));
        const textos = crear('span', 'tema-textos');
        textos.append(crear('strong', null, tema.titulo), crear('span', 'tema-descripcion', tema.descripcion));
        const pie = crear('span', 'tema-pie');
        pie.append(crear('span', null, `${tema.pasos.length} pasos`));
        if (vistos.has(tema.id)) {
            const visto = crear('span', 'tema-visto');
            visto.append(crearIcono('check'), document.createTextNode('Ya lo vio'));
            pie.append(visto);
        } else if (tema.recomendado) {
            pie.append(crear('span', 'tema-empiece', 'Empiece aquí'));
        }
        textos.append(pie);
        boton.append(icono, textos);
        boton.addEventListener('click', () => empezarTema(tema.id));
        elemento.append(boton);
        return elemento;
    }));
}

/** Abre el recorrido de un tema. */
export function empezarTema(id) {
    const tema = TEMAS.find((candidato) => candidato.id === id);
    if (!tema || abierto) return;
    ocultarInvitacion(true);
    abierto = new Recorrido(tema, (terminado, elegirOtro) => {
        abierto = null;
        pintarTemas();
        if (elegirOtro) {
            irA('tutorial');
            requestAnimationFrame(() => document.querySelector(`[data-tema="${terminado.id}"]`)?.focus());
        } else if (window.location.hash === '#tutorial') {
            document.querySelector(`[data-tema="${terminado.id}"]`)?.focus();
        }
    });
}

function ocultarInvitacion(guardar) {
    const invitacion = document.getElementById('invitacion-tutorial');
    if (invitacion) invitacion.hidden = true;
    if (!guardar) return;
    try {
        localStorage.setItem(CLAVE_INVITACION, 'no');
    } catch {
        // Sin almacenamiento se vuelve a ofrecer la próxima vez; no pasa nada.
    }
}

/** En el inicio, ofrece el tutorial a quien nunca lo ha visto. */
export function ofrecerTutorial() {
    const invitacion = document.getElementById('invitacion-tutorial');
    if (!invitacion) return;
    let rechazada = false;
    try {
        rechazada = localStorage.getItem(CLAVE_INVITACION) === 'no';
    } catch {
        rechazada = false;
    }
    invitacion.hidden = rechazada || leerVistos().size > 0;
}

export function iniciarTutorial() {
    pintarTemas();
    document.getElementById('empezar-tutorial')?.addEventListener('click', () => empezarTema('primeros-pasos'));
    document.getElementById('rechazar-tutorial')?.addEventListener('click', () => {
        ocultarInvitacion(true);
        document.getElementById('saludo').focus();
    });
}
