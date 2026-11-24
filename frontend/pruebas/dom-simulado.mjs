// DOM mínimo para probar módulos de la interfaz con Node, sin navegador ni dependencias.
// Solo implementa lo que usan las pantallas probadas: crear elementos, texto, hijos, eventos, formularios y listas.

class Texto {
    constructor(texto) {
        this.data = String(texto);
    }

    get textContent() {
        return this.data;
    }
}

export class Elemento {
    constructor(etiqueta) {
        this.tagName = String(etiqueta).toUpperCase();
        this.hijos = [];
        this.atributos = {};
        this.dataset = {};
        this.oyentes = {};
        this.hidden = false;
        this.disabled = false;
        this.className = '';
        this.style = { setProperty(nombre, valor) { this[nombre] = valor; } };
        this.padre = null;
        this.id = '';
        this.value = '';
        this.type = '';
    }

    get children() {
        return this.hijos.filter((hijo) => hijo instanceof Elemento);
    }

    static comoNodo(nodo) {
        return nodo instanceof Elemento || nodo instanceof Texto ? nodo : new Texto(nodo);
    }

    adoptar(nodo) {
        if (nodo instanceof Elemento) {
            nodo.padre?.quitar(nodo);
            nodo.padre = this;
        }
        return nodo;
    }

    quitar(nodo) {
        this.hijos = this.hijos.filter((hijo) => hijo !== nodo);
    }

    append(...nodos) {
        for (const nodo of nodos) this.hijos.push(this.adoptar(Elemento.comoNodo(nodo)));
    }

    prepend(...nodos) {
        this.hijos.unshift(...nodos.map((nodo) => this.adoptar(Elemento.comoNodo(nodo))));
    }

    replaceChildren(...nodos) {
        this.hijos.forEach((hijo) => { if (hijo instanceof Elemento) hijo.padre = null; });
        this.hijos = [];
        this.append(...nodos);
    }

    remove() {
        this.padre?.quitar(this);
        this.padre = null;
    }

    /** Clases como lista, sobre className. */
    get classList() {
        const elemento = this;
        const clases = () => elemento.className.split(/\s+/).filter(Boolean);
        return {
            add: (...nuevas) => { elemento.className = [...new Set([...clases(), ...nuevas])].join(' '); },
            remove: (...quitadas) => { elemento.className = clases().filter((c) => !quitadas.includes(c)).join(' '); },
            contains: (clase) => clases().includes(clase),
            toggle: (clase, forzar) => {
                const poner = forzar ?? !clases().includes(clase);
                if (poner) elemento.classList.add(clase); else elemento.classList.remove(clase);
                return poner;
            },
        };
    }

    get textContent() {
        return this.hijos.map((hijo) => hijo.textContent).join('');
    }

    set textContent(texto) {
        this.hijos = texto === '' || texto == null ? [] : [new Texto(texto)];
    }

    setAttribute(nombre, valor) {
        this.atributos[nombre] = String(valor);
    }

    getAttribute(nombre) {
        return this.atributos[nombre] ?? null;
    }

    removeAttribute(nombre) {
        delete this.atributos[nombre];
    }

    addEventListener(tipo, funcion) {
        (this.oyentes[tipo] ??= []).push(funcion);
    }

    dispatchEvent(evento) {
        if (!evento.target) evento.target = this;
        for (const funcion of this.oyentes[evento.type] ?? []) funcion(evento);
        return !evento.defaultPrevented;
    }

    click() {
        if (this.disabled) return;
        this.dispatchEvent(crearEvento('click'));
    }

    focus() {
        documento.activeElement = this;
    }

    /** Todos los descendientes, en orden. */
    descendientes() {
        return this.children.flatMap((hijo) => [hijo, ...hijo.descendientes()]);
    }

    querySelectorAll(selector) {
        const coincide = (elemento) => {
            const atributo = /^\[(\w+)="([^"]*)"\]$/.exec(selector);
            if (atributo) return String(elemento[atributo[1]] ?? elemento.getAttribute(atributo[1])) === atributo[2];
            if (selector.startsWith('.')) return elemento.className.split(/\s+/).includes(selector.slice(1));
            return elemento.tagName === selector.toUpperCase();
        };
        return this.descendientes().filter(coincide);
    }

    querySelector(selector) {
        return this.querySelectorAll(selector)[0] ?? null;
    }
}

/** Lista desplegable: el valor es el de la opción elegida. */
class Seleccion extends Elemento {
    constructor() {
        super('select');
        this.elegido = '';
    }

    get options() {
        return this.children.filter((hijo) => hijo.tagName === 'OPTION');
    }

    get value() {
        return this.elegido;
    }

    set value(valor) {
        this.elegido = String(valor ?? '');
    }

    get selectedOptions() {
        return this.options.filter((opcion) => opcion.value === this.elegido);
    }

    append(...nodos) {
        super.append(...nodos);
        if (!this.elegido && this.options.length) this.elegido = this.options[0].value;
    }
}

export function crearEvento(tipo) {
    return {
        type: tipo,
        target: null,
        defaultPrevented: false,
        preventDefault() {
            this.defaultPrevented = true;
        },
    };
}

const porId = new Map();
const porSelector = new Map();

/** Registra el elemento que devuelve document.querySelector para un selector complejo ("#formulario [type=x]"). */
export function registrarSelector(selector, elemento) {
    porSelector.set(selector, elemento);
}

const documento = {
    activeElement: null,
    cookie: '',
    createElement(etiqueta) {
        return etiqueta === 'select' ? new Seleccion() : new Elemento(etiqueta);
    },
    createElementNS(_espacio, etiqueta) {
        return new Elemento(etiqueta);
    },
    getElementById(id) {
        return porId.get(id) ?? null;
    },
    querySelector(selector) {
        if (porSelector.has(selector)) return porSelector.get(selector);
        return selector.startsWith('#') && porId.has(selector.slice(1)) ? porId.get(selector.slice(1)) : null;
    },
    querySelectorAll(selector) {
        const elemento = documento.querySelector(selector);
        return elemento ? [elemento] : [];
    },
};

/**
 * Instala document, window y Option en el ámbito global y registra los elementos con id que pide la pantalla.
 *
 * @param {Record<string, string>} elementos id → etiqueta
 * @returns {Map<string, Elemento>} elementos registrados
 */
export function instalarDom(elementos) {
    porId.clear();
    porSelector.clear();
    documento.body = new Elemento('body');
    for (const [id, etiqueta] of Object.entries(elementos)) {
        const elemento = documento.createElement(etiqueta);
        elemento.id = id;
        porId.set(id, elemento);
    }
    globalThis.document = documento;
    globalThis.window = {
        confirm: () => true,
        location: { hash: '', replace() {} },
        addEventListener() {},
    };
    globalThis.Option = function Option(texto, valor) {
        const opcion = new Elemento('option');
        opcion.textContent = texto;
        opcion.value = String(valor ?? texto);
        return opcion;
    };
    return porId;
}

/** Deja correr las promesas pendientes. */
export async function esperar(vueltas = 10) {
    for (let i = 0; i < vueltas; i += 1) await new Promise((resolver) => setImmediate(resolver));
}

/**
 * Sustituye fetch por un servidor controlado: cada solicitud queda pendiente hasta que la prueba la responde, así se
 * pueden simular respuestas demoradas y en desorden.
 */
export function instalarServidor() {
    const pendientes = [];
    globalThis.fetch = (url, opciones = {}) => new Promise((resolver, rechazar) => {
        const direccion = new URL(url, 'https://marketradar.prueba');
        pendientes.push({
            url: direccion,
            parametros: Object.fromEntries(direccion.searchParams),
            // La señal de cancelación queda a la vista, pero la solicitud no se corta sola: así se prueba que una
            // respuesta que llega aunque se haya cancelado tampoco cambia la pantalla.
            senal: opciones.signal ?? null,
            fallar(error = new TypeError('Failed to fetch')) {
                rechazar(error);
            },
            responder(datos, estado = 200) {
                resolver({
                    ok: estado >= 200 && estado < 300,
                    status: estado,
                    json: async () => datos,
                });
            },
        });
    });
    return pendientes;
}
