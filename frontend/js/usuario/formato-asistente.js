/**
 * Convierte la respuesta del asistente en contenido fácil de leer:
 * - [Ver en Falabella](https://...) se vuelve un botón que abre la tienda.
 * - Una dirección https suelta se vuelve un botón "Abrir en dominio", en vez de un texto largo e ilegible.
 * - **texto** se vuelve negrita, y las líneas que empiezan con "1." o "-" se vuelven listas.
 * Todo se construye con nodos de texto (nunca innerHTML), así que la respuesta no puede inyectar HTML.
 */
import { crear, crearIcono, dominioSeguro } from '../comun/dom.js';

const PATRON = /\[([^\]\n]{1,80})\]\((https?:\/\/[^\s)]+)\)|\*\*([^*\n]+)\*\*|(https?:\/\/[^\s<>()]+)/g;

/** Botón que abre la tienda en otra pestaña, después de avisar a qué página va. */
function enlaceCompra(texto, url) {
    const dominio = dominioSeguro(url);
    if (!dominio) {
        return document.createTextNode(`${texto} (este enlace no es seguro, búsquelo en la página de Buscar)`);
    }
    const enlace = crear('a', 'enlace-chat');
    enlace.href = url;
    enlace.target = '_blank';
    enlace.rel = 'noopener noreferrer';
    enlace.append(crearIcono('tienda'), crear('span', null, texto), crear('span', 'solo-lectores', ' (se abre en otra pestaña)'));
    enlace.addEventListener('click', (evento) => {
        const continuar = window.confirm(`Va a abrir ${dominio} en otra pestaña, con conexión segura. Allí podrá `
            + 'ver y comprar el producto. Nunca le pediremos datos bancarios en Marketradar.');
        if (!continuar) evento.preventDefault();
    });
    return enlace;
}

/** Quita la puntuación que suele quedar pegada al final de una dirección ("...gl=co." o "...udm=28,"). */
function separarPuntuacion(url) {
    const sobra = url.match(/[.,;:!?]+$/);
    return sobra ? [url.slice(0, -sobra[0].length), sobra[0]] : [url, ''];
}

/** Texto de una línea con negritas y enlaces. */
export function formatearLinea(linea) {
    const nodos = [];
    let ultimo = 0;
    for (const coincidencia of linea.matchAll(PATRON)) {
        const [completo, textoEnlace, urlEnlace, negrita, urlSuelta] = coincidencia;
        if (coincidencia.index > ultimo) nodos.push(document.createTextNode(linea.slice(ultimo, coincidencia.index)));
        if (urlEnlace) {
            nodos.push(enlaceCompra(textoEnlace.trim(), urlEnlace));
        } else if (negrita) {
            nodos.push(crear('strong', null, negrita));
        } else {
            const [url, puntuacion] = separarPuntuacion(urlSuelta);
            const dominio = dominioSeguro(url);
            nodos.push(dominio ? enlaceCompra(`Abrir en ${dominio}`, url) : document.createTextNode(url));
            if (puntuacion) nodos.push(document.createTextNode(puntuacion));
        }
        ultimo = coincidencia.index + completo.length;
    }
    if (ultimo < linea.length) nodos.push(document.createTextNode(linea.slice(ultimo)));
    // Asteriscos o numerales sueltos que no formaron negrita ni título no se muestran.
    nodos.forEach((nodo) => {
        if (nodo.nodeType === Node.TEXT_NODE) nodo.textContent = nodo.textContent.replace(/\*\*|^#+\s*/g, '');
    });
    return nodos;
}

/**
 * @param {string} texto respuesta del asistente
 * @returns {HTMLElement} bloque con párrafos, listas y enlaces
 */
export function formatearRespuesta(texto) {
    const bloque = crear('div', 'respuesta-asistente');
    let lista = null;
    let parrafo = null;
    for (const cruda of String(texto ?? '').split('\n')) {
        const linea = cruda.trim();
        if (!linea) {
            lista = null;
            parrafo = null;
            continue;
        }
        const numerada = linea.match(/^(\d+)[.)]\s+(.*)$/);
        const vineta = linea.match(/^[-*•]\s+(.*)$/);
        if (numerada || vineta) {
            const tipo = numerada ? 'ol' : 'ul';
            if (!lista || lista.tagName.toLowerCase() !== tipo) {
                lista = crear(tipo);
                bloque.append(lista);
            }
            const elemento = crear('li');
            elemento.append(...formatearLinea(numerada ? numerada[2] : vineta[1]));
            lista.append(elemento);
            parrafo = null;
            continue;
        }
        lista = null;
        if (parrafo) {
            parrafo.append(crear('br'));
        } else {
            parrafo = crear('p');
            bloque.append(parrafo);
        }
        parrafo.append(...formatearLinea(linea));
    }
    return bloque;
}
