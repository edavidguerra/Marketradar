/**
 * Sugerencias mientras la persona escribe (patrón combobox de ARIA 1.2).
 * Flechas arriba y abajo para moverse, Enter para elegir (o para buscar lo escrito) y Escape para cerrar.
 * Las sugerencias salen de Marketradar, no de las tiendas, así que no gastan búsquedas.
 */
import { obtenerSugerencias } from '../comun/api.js';

const ESPERA_MS = 250;

export function iniciarSugerencias(campo, lista, estado, alElegir) {
    let temporizador = null;
    let pedido = 0;
    let activa = -1;
    let opciones = [];

    function cerrar() {
        lista.hidden = true;
        lista.replaceChildren();
        document.body.classList.remove('sugerencias-abiertas');
        opciones = [];
        activa = -1;
        campo.setAttribute('aria-expanded', 'false');
        campo.removeAttribute('aria-activedescendant');
    }

    function marcar(indice) {
        activa = indice;
        opciones.forEach((opcion, i) => opcion.setAttribute('aria-selected', String(i === indice)));
        if (indice >= 0) {
            campo.setAttribute('aria-activedescendant', opciones[indice].id);
            opciones[indice].scrollIntoView({ block: 'nearest' });
        } else {
            campo.removeAttribute('aria-activedescendant');
        }
    }

    function elegir(texto) {
        campo.value = texto;
        cerrar();
        alElegir(texto);
    }

    function mostrar(sugerencias) {
        const escrito = campo.value.trim().toLowerCase();
        const utiles = sugerencias.filter((texto) => texto.toLowerCase() !== escrito);
        if (utiles.length === 0 || document.activeElement !== campo) {
            cerrar();
            return;
        }
        opciones = utiles.map((texto, i) => {
            const opcion = document.createElement('li');
            opcion.id = `sugerencia-${i}`;
            opcion.setAttribute('role', 'option');
            opcion.setAttribute('aria-selected', 'false');
            opcion.textContent = texto;
            // mousedown y no click: así el campo no pierde el foco antes de elegir.
            opcion.addEventListener('mousedown', (evento) => {
                evento.preventDefault();
                elegir(texto);
            });
            return opcion;
        });
        lista.replaceChildren(...opciones);
        lista.hidden = false;
        // En celular el botón flotante de ayuda taparía la lista; se oculta mientras está abierta.
        document.body.classList.add('sugerencias-abiertas');
        activa = -1;
        campo.setAttribute('aria-expanded', 'true');
        estado.textContent = utiles.length === 1
            ? 'Hay 1 sugerencia. Use la flecha hacia abajo para elegirla.'
            : `Hay ${utiles.length} sugerencias. Use las flechas para elegir una.`;
    }

    campo.addEventListener('input', () => {
        clearTimeout(temporizador);
        const texto = campo.value.trim();
        if (texto.length < 2) {
            cerrar();
            return;
        }
        temporizador = setTimeout(async () => {
            const numero = ++pedido;
            try {
                const respuesta = await obtenerSugerencias(texto);
                if (numero === pedido) mostrar(respuesta.sugerencias || []);
            } catch {
                // Si las sugerencias fallan, la persona igual puede buscar escribiendo.
                cerrar();
            }
        }, ESPERA_MS);
    });

    campo.addEventListener('keydown', (evento) => {
        if (evento.isComposing) return;
        // Enter siempre busca: con una sugerencia marcada busca esa, si no, busca lo escrito.
        if (evento.key === 'Enter' && (lista.hidden || activa < 0)) {
            evento.preventDefault();
            campo.form.requestSubmit();
            return;
        }
        if (lista.hidden) {
            return;
        }
        if (evento.key === 'ArrowDown') {
            evento.preventDefault();
            marcar(activa + 1 >= opciones.length ? 0 : activa + 1);
        } else if (evento.key === 'ArrowUp') {
            evento.preventDefault();
            marcar(activa - 1 < 0 ? opciones.length - 1 : activa - 1);
        } else if (evento.key === 'Enter' && activa >= 0) {
            evento.preventDefault();
            elegir(opciones[activa].textContent);
        } else if (evento.key === 'Escape') {
            evento.preventDefault();
            cerrar();
        }
    });

    campo.addEventListener('blur', () => setTimeout(cerrar, 150));
    campo.form.addEventListener('submit', () => {
        clearTimeout(temporizador);
        pedido += 1;
        cerrar();
    });
}
