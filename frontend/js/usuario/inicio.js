/**
 * Sección Inicio: saludo, avisos que invitan a comprar y accesos a cada opción.
 *
 * Cuando hay varios avisos se muestran de a uno y cambian solos cada {@link INTERVALO} milisegundos. La persona
 * también los puede pasar con las flechas, deslizando el dedo o con las teclas ← y →, y los puede pausar. El cambio
 * automático se detiene mientras el ratón o el foco están sobre el aviso, mientras se toca la pantalla y cuando la
 * pestaña no está a la vista. Si el sistema pide reducir el movimiento, los avisos empiezan pausados.
 */

import { obtenerAvisos } from '../comun/api.js';
import { boton, crear, crearIcono, enlaceTienda, mostrarEstado } from '../comun/dom.js';
import { buscarTermino } from './busqueda.js';
import { irA } from './navegacion.js';

/** Tiempo que se muestra cada aviso antes de pasar al siguiente. */
export const INTERVALO = 5000;

/** Distancia mínima del dedo, en píxeles, para contar un deslizamiento. */
const DESLIZAMIENTO_MINIMO = 50;

/** Etiqueta, ícono y clase de color según el tipo de aviso. */
const TIPOS = {
    BAJA_PRECIO: { etiqueta: 'Bajó de precio', icono: 'etiqueta', clase: 'tipo-baja' },
    SEGUIMIENTO: { etiqueta: 'Su última búsqueda', icono: 'reloj', clase: 'tipo-seguimiento' },
    INVITACION: { etiqueta: 'Consejo', icono: 'radar', clase: 'tipo-invitacion' },
};

let carrusel = null;

function crearAccion(aviso) {
    const acciones = crear('div', 'acciones');
    if (aviso.enlace) {
        acciones.append(enlaceTienda(aviso.texto_accion, aviso.enlace, 'la tienda'));
    } else if (aviso.termino_busqueda) {
        acciones.append(boton(aviso.texto_accion, 'boton-principal', () => buscarTermino(aviso.termino_busqueda)));
    } else {
        acciones.append(boton(aviso.texto_accion, 'boton-principal', () => irA('buscar')));
    }
    if (aviso.enlace && aviso.termino_busqueda) {
        acciones.append(boton('Comparar otra vez', 'boton-secundario', () => buscarTermino(aviso.termino_busqueda)));
    }
    return acciones;
}

function crearDiapositiva(aviso, indice, total) {
    const tipo = TIPOS[aviso.tipo] || TIPOS.INVITACION;
    const diapositiva = crear('div', `aviso-diapositiva ${tipo.clase}`);
    diapositiva.setAttribute('role', 'group');
    diapositiva.setAttribute('aria-roledescription', 'aviso');
    diapositiva.setAttribute('aria-label', `${indice + 1} de ${total}`);
    const etiqueta = crear('p', 'etiqueta-aviso');
    etiqueta.append(crearIcono(tipo.icono), document.createTextNode(tipo.etiqueta));
    diapositiva.append(etiqueta, crear('p', 'titulo-aviso', aviso.titulo), crear('p', 'mensaje-aviso', aviso.mensaje),
        crearAccion(aviso));
    return diapositiva;
}

function botonIcono(icono, etiqueta, clase, alPulsar) {
    const elemento = boton('', `boton-secundario boton-icono ${clase}`, alPulsar);
    elemento.setAttribute('aria-label', etiqueta);
    elemento.append(crearIcono(icono));
    return elemento;
}

/**
 * Carrusel de avisos. Todas las diapositivas quedan en la misma celda de la cuadrícula, así el recuadro toma la
 * altura del aviso más largo y la página no salta al cambiar.
 */
class Carrusel {

    constructor(contenedor, avisos) {
        this.avisos = avisos;
        this.actual = 0;
        this.pausadoPorPersona = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        this.encima = false;
        this.conFoco = false;
        this.tocando = false;
        this.restante = INTERVALO;
        this.inicio = 0;
        this.temporizador = null;

        this.raiz = crear('div', 'avisos');
        this.raiz.setAttribute('role', 'region');
        this.raiz.setAttribute('aria-roledescription', 'carrusel');
        this.raiz.setAttribute('aria-label', 'Avisos de ofertas');

        this.pista = crear('div', 'avisos-pista');
        this.diapositivas = avisos.map((aviso, i) => crearDiapositiva(aviso, i, avisos.length));
        this.pista.append(...this.diapositivas);

        this.anuncio = crear('p', 'solo-lectores');
        this.anuncio.setAttribute('aria-live', 'polite');

        this.puntos = crear('span', 'avisos-puntos');
        this.puntos.setAttribute('aria-hidden', 'true');
        avisos.forEach(() => this.puntos.append(crear('span', 'punto')));
        this.contador = crear('span', 'avisos-contador');
        const posicion = crear('span', 'avisos-posicion');
        posicion.append(this.puntos, this.contador);

        this.botonPausa = boton('', 'boton-secundario boton-pausa', () => this.alternarPausa());
        const controles = crear('div', 'avisos-controles');
        controles.append(
            botonIcono('anterior', 'Aviso anterior', 'boton-anterior', () => this.mover(-1, true)),
            posicion,
            botonIcono('siguiente', 'Aviso siguiente', 'boton-siguiente', () => this.mover(1, true)),
            this.botonPausa,
        );

        this.raiz.append(this.pista, controles, this.anuncio);
        contenedor.removeAttribute('aria-live');
        contenedor.replaceChildren(this.raiz);

        this.escuchar();
        this.mostrar(0, 0);
        this.programar(true);
    }

    escuchar() {
        this.raiz.addEventListener('mouseenter', () => { this.encima = true; this.programar(); });
        this.raiz.addEventListener('mouseleave', () => { this.encima = false; this.programar(); });
        this.raiz.addEventListener('focusin', () => { this.conFoco = true; this.programar(); });
        this.raiz.addEventListener('focusout', (evento) => {
            if (!this.raiz.contains(evento.relatedTarget)) {
                this.conFoco = false;
                this.programar();
            }
        });
        this.raiz.addEventListener('keydown', (evento) => {
            if (evento.key === 'ArrowLeft' || evento.key === 'ArrowRight') {
                evento.preventDefault();
                this.mover(evento.key === 'ArrowLeft' ? -1 : 1, true);
            }
        });
        this.alCambiarVisibilidad = () => this.programar();
        document.addEventListener('visibilitychange', this.alCambiarVisibilidad);
        this.escucharDeslizamiento();
    }

    /** Deslizar el dedo (o arrastrar con el ratón) hacia la izquierda muestra el siguiente aviso. */
    escucharDeslizamiento() {
        let x = 0;
        let y = 0;
        let arrastrando = false;
        const soltar = (evento, contar) => {
            if (!arrastrando) return;
            arrastrando = false;
            this.tocando = false;
            this.pista.classList.remove('arrastrando');
            this.pista.style.removeProperty('--arrastre');
            const dx = evento.clientX - x;
            const dy = evento.clientY - y;
            if (contar && Math.abs(dx) >= DESLIZAMIENTO_MINIMO && Math.abs(dx) > Math.abs(dy)) {
                this.mover(dx < 0 ? 1 : -1, true);
            } else {
                this.programar();
            }
        };
        this.pista.addEventListener('pointerdown', (evento) => {
            if (evento.button !== 0 || evento.target.closest('a, button')) return;
            x = evento.clientX;
            y = evento.clientY;
            arrastrando = true;
            this.tocando = true;
            this.programar();
        });
        this.pista.addEventListener('pointermove', (evento) => {
            if (!arrastrando) return;
            const dx = evento.clientX - x;
            if (Math.abs(dx) > 8 && Math.abs(dx) > Math.abs(evento.clientY - y)) {
                this.pista.classList.add('arrastrando');
                this.pista.style.setProperty('--arrastre', `${Math.max(-80, Math.min(80, dx / 2))}px`);
            }
        });
        this.pista.addEventListener('pointerup', (evento) => soltar(evento, true));
        this.pista.addEventListener('pointercancel', (evento) => soltar(evento, false));
        this.pista.addEventListener('pointerleave', (evento) => soltar(evento, true));
    }

    /**
     * @param paso -1 para el anterior, 1 para el siguiente
     * @param porPersona si lo pidió la persona; entonces se anuncia al lector de pantalla
     */
    mover(paso, porPersona) {
        const total = this.avisos.length;
        this.mostrar((this.actual + paso + total) % total, paso);
        if (porPersona) {
            const aviso = this.avisos[this.actual];
            this.anuncio.textContent = `Aviso ${this.actual + 1} de ${total}. ${aviso.titulo}. ${aviso.mensaje}`;
        }
        this.programar(true);
    }

    mostrar(indice, paso) {
        this.actual = indice;
        this.diapositivas.forEach((diapositiva, i) => {
            const activa = i === indice;
            diapositiva.classList.toggle('activa', activa);
            diapositiva.classList.remove('entra-derecha', 'entra-izquierda');
            diapositiva.inert = !activa;
            diapositiva.setAttribute('aria-hidden', String(!activa));
            if (activa && paso !== 0) {
                void diapositiva.offsetWidth;
                diapositiva.classList.add(paso > 0 ? 'entra-derecha' : 'entra-izquierda');
            }
        });
        [...this.puntos.children].forEach((punto, i) => punto.classList.toggle('activo', i === indice));
        this.contador.textContent = `${indice + 1} de ${this.avisos.length}`;
    }

    alternarPausa() {
        this.pausadoPorPersona = !this.pausadoPorPersona;
        this.anuncio.textContent = this.pausadoPorPersona
            ? 'Avisos en pausa. Use las flechas para verlos.'
            : `Los avisos cambiarán solos cada ${INTERVALO / 1000} segundos.`;
        this.programar(true);
    }

    detenido() {
        return this.pausadoPorPersona || this.encima || this.conFoco || this.tocando || document.hidden;
    }

    /**
     * Arranca, pausa o reanuda el cambio automático según el estado. Al pausar guarda el tiempo que faltaba, para
     * seguir desde ahí.
     *
     * @param reiniciar empieza de nuevo la cuenta de {@link INTERVALO}
     */
    programar(reiniciar = false) {
        if (this.temporizador) {
            clearTimeout(this.temporizador);
            this.temporizador = null;
            this.restante = Math.max(0, this.restante - (performance.now() - this.inicio));
        }
        if (reiniciar) this.restante = INTERVALO;
        const detenido = this.detenido();
        this.raiz.classList.toggle('en-pausa', detenido);
        this.raiz.classList.toggle('pausado-por-persona', this.pausadoPorPersona);
        this.botonPausa.replaceChildren(crearIcono(this.pausadoPorPersona ? 'reproducir' : 'pausa'),
            document.createTextNode(this.pausadoPorPersona ? 'Reanudar' : 'Pausar'));
        this.botonPausa.setAttribute('aria-label', this.pausadoPorPersona
            ? 'Reanudar el cambio automático de avisos' : 'Pausar el cambio automático de avisos');
        if (detenido) return;
        this.inicio = performance.now();
        this.temporizador = setTimeout(() => {
            this.temporizador = null;
            if (!this.raiz.isConnected) return;
            if (this.raiz.offsetParent === null) {
                this.programar(true);
                return;
            }
            this.mover(1, false);
        }, this.restante);
    }

    destruir() {
        clearTimeout(this.temporizador);
        document.removeEventListener('visibilitychange', this.alCambiarVisibilidad);
    }
}

function mostrarUnSoloAviso(contenedor, aviso) {
    contenedor.setAttribute('aria-live', 'polite');
    const diapositiva = crearDiapositiva(aviso, 0, 1);
    diapositiva.classList.add('activa');
    diapositiva.removeAttribute('role');
    diapositiva.removeAttribute('aria-roledescription');
    diapositiva.removeAttribute('aria-label');
    contenedor.replaceChildren(diapositiva);
}

export async function cargarInicio() {
    const contenedor = document.getElementById('aviso-inicio');
    carrusel?.destruir();
    carrusel = null;
    contenedor.setAttribute('aria-live', 'polite');
    mostrarEstado(contenedor, 'Buscando ofertas para usted…');
    try {
        const avisos = await obtenerAvisos();
        if (avisos.length > 1) {
            carrusel = new Carrusel(contenedor, avisos);
        } else if (avisos.length === 1) {
            mostrarUnSoloAviso(contenedor, avisos[0]);
        } else {
            contenedor.replaceChildren();
        }
    } catch (error) {
        mostrarEstado(contenedor, error.message, cargarInicio);
    }
}
