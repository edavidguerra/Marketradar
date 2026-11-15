/**
 * "Seguir producto" y "Seguimientos". Para seguir un producto el correo debe estar verificado: los avisos se
 * mandan a ese correo. Si el servidor no tiene correo configurado, se dice: nunca se finge que se mandó algo.
 */

import {
    cambiarSeguimiento, dejarDeSeguir, obtenerEstadoCorreo, obtenerSeguimientos, pedirVerificacionCorreo, seguirOferta,
} from '../comun/api.js';
import { avisar, boton, crear, crearIcono, enlaceTienda, mostrarEstado } from '../comun/dom.js';
import { dinero, fechaHora, pesos, tiendaDeOferta } from '../comun/formato.js';
import { leerPrecio } from './busqueda.js';

const $ = (id) => document.getElementById(id);
let ofertaActual = null;
let terminoActual = '';
let botonOrigen = null;

/**
 * Botón "Seguir producto" de una tarjeta.
 *
 * @param oferta oferta (debe estar guardada: con id)
 * @param termino lo que buscó la persona, para volver a consultar el precio
 */
export function botonSeguir(oferta, termino) {
    if (oferta.id == null) return null;
    const elemento = boton('', 'boton-secundario boton-seguir', (evento) => abrirDialogo(oferta, termino, evento.currentTarget));
    elemento.append(crearIcono('campana'), ' Seguir producto');
    elemento.setAttribute('aria-label', `Seguir el precio de: ${oferta.titulo}`);
    elemento.setAttribute('aria-haspopup', 'dialog');
    return elemento;
}

function abrirDialogo(oferta, termino, origen) {
    ofertaActual = oferta;
    terminoActual = termino;
    botonOrigen = origen;
    $('seguir-producto').textContent = `${oferta.titulo} · ${tiendaDeOferta(oferta)} · ${pesos(oferta.precio)}`;
    $('seguir-tipo-baja').checked = true;
    $('seguir-objetivo').value = '';
    $('campo-objetivo').hidden = true;
    $('seguir-verificar').hidden = true;
    avisar($('resultado-seguir'), '');
    const dialogo = $('dialogo-seguir');
    if (typeof dialogo.showModal === 'function') dialogo.showModal();
    else dialogo.setAttribute('open', '');
    $('seguir-tipo-baja').focus();
}

function cerrarDialogo() {
    const dialogo = $('dialogo-seguir');
    if (typeof dialogo.close === 'function') dialogo.close();
    else dialogo.removeAttribute('open');
}

async function enviarSeguimiento(evento) {
    evento.preventDefault();
    const tipo = $('seguir-tipo-objetivo').checked ? 'PRECIO_OBJETIVO' : 'BAJA_PRECIO';
    let objetivo = null;
    if (tipo === 'PRECIO_OBJETIVO') {
        objetivo = leerPrecio($('seguir-objetivo').value);
        if (objetivo == null || Number.isNaN(objetivo) || objetivo <= 0) {
            avisar($('resultado-seguir'), 'Escriba el precio al que quiere comprar, solo con números.', 'error');
            $('seguir-objetivo').focus();
            return;
        }
        if (ofertaActual.precio != null && objetivo >= Number(ofertaActual.precio)) {
            avisar($('resultado-seguir'), `El precio que espera debe ser menor que el de hoy (${pesos(ofertaActual.precio)}).`, 'error');
            $('seguir-objetivo').focus();
            return;
        }
    }
    const enviar = $('seguir-enviar');
    enviar.disabled = true;
    try {
        const respuesta = await seguirOferta(ofertaActual.id, tipo, objetivo == null ? null : String(objetivo), terminoActual);
        avisar($('resultado-seguir'), respuesta.mensaje, respuesta.confirmacion === 'enviado' ? 'exito' : 'error');
        if (botonOrigen) {
            botonOrigen.replaceChildren(crearIcono('check'), ' Siguiendo');
            botonOrigen.classList.add('siguiendo');
        }
    } catch (error) {
        avisar($('resultado-seguir'), error.message, 'error');
        $('seguir-verificar').hidden = error.codigo !== 'CORREO_SIN_VERIFICAR';
    } finally {
        enviar.disabled = false;
    }
}

let pidiendoVerificacion = false;

async function pedirVerificacion(zona, origen) {
    // Dos clics seguidos no piden dos enlaces (el servidor también lo impide).
    if (pidiendoVerificacion) return;
    pidiendoVerificacion = true;
    if (origen) origen.disabled = true;
    try {
        const respuesta = await pedirVerificacionCorreo();
        // "Aceptado" por el servicio de correo, no "entregado": así se dice.
        avisar(zona, respuesta?.mensaje || 'El servicio de correo aceptó el enlace. Puede tardar unos minutos en llegar; '
            + 'revise también la carpeta de spam.');
    } catch (error) {
        avisar(zona, error.message, 'error');
    } finally {
        pidiendoVerificacion = false;
        if (origen) origen.disabled = false;
    }
}

function textoAviso(seguimiento) {
    if (seguimiento.tipo_aviso === 'PRECIO_OBJETIVO') {
        return `Le avisamos cuando cueste ${dinero(seguimiento.precio_objetivo, seguimiento.moneda)} o menos.`;
    }
    return 'Le avisamos cuando baje de precio.';
}

const ESTADOS = { ACTIVO: 'Activo', PAUSADO: 'En pausa' };

function crearSeguimiento(seguimiento) {
    const item = crear('li', `tarjeta seguimiento${seguimiento.estado === 'PAUSADO' ? ' pausado' : ''}`);
    const imagen = crear('div', 'tarjeta-imagen');
    if (seguimiento.url_imagen) {
        const foto = document.createElement('img');
        foto.src = seguimiento.url_imagen;
        foto.alt = seguimiento.titulo;
        foto.loading = 'lazy';
        imagen.append(foto);
    } else {
        imagen.append(crearIcono('etiqueta'));
    }
    const cuerpo = crear('div', 'tarjeta-cuerpo');
    const tienda = tiendaDeOferta(seguimiento);
    cuerpo.append(
        crear('h2', null, seguimiento.titulo),
        crear('p', `estado-seguimiento estado-${seguimiento.estado.toLowerCase()}`, ESTADOS[seguimiento.estado] || seguimiento.estado),
        crear('p', null, `Tienda: ${tienda}`),
        crear('p', null, seguimiento.precio_guardado != null
            ? `Precio de referencia: ${dinero(seguimiento.precio_guardado, seguimiento.moneda)}`
            : 'Precio de referencia: todavía no se ha tomado'),
        crear('p', null, textoAviso(seguimiento)),
        crear('p', 'ayuda', seguimiento.ultima_comprobacion
            ? `Última revisión: ${fechaHora(seguimiento.ultima_comprobacion)}. ${seguimiento.resultado_texto || ''}`
            : 'Todavía no se ha revisado el precio.'),
    );
    if (seguimiento.avisos?.length) {
        const lista = crear('ul', 'avisos-seguimiento');
        lista.setAttribute('aria-label', 'Correos de este seguimiento');
        seguimiento.avisos.forEach((aviso) => {
            const precio = aviso.precio_actual != null ? ` · ${dinero(aviso.precio_actual, seguimiento.moneda)}` : '';
            lista.append(crear('li', null, `${fechaHora(aviso.creado_en)}: ${aviso.estado_texto}${precio}`));
        });
        cuerpo.append(lista);
    }
    const zona = crear('p', 'aviso-formulario');
    zona.setAttribute('role', 'status');
    zona.hidden = true;
    const acciones = crear('div', 'acciones');
    const texto = seguimiento.enlace?.texto ?? `Ver en ${tienda}`;
    acciones.append(enlaceTienda(texto, seguimiento.url_destino, tienda, 'boton boton-secundario', seguimiento.enlace));
    const pausar = seguimiento.estado === 'ACTIVO';
    acciones.append(boton(pausar ? 'Pausar avisos' : 'Reanudar avisos', 'boton-secundario', async () => {
        try {
            await cambiarSeguimiento(seguimiento.oferta_id, { estado: pausar ? 'PAUSADO' : 'ACTIVO' });
            await cargarSeguimientos(pausar ? 'Pausó los avisos de este producto.' : 'Reanudó los avisos de este producto.');
        } catch (error) {
            avisar(zona, error.message, 'error');
        }
    }));
    acciones.append(boton('Dejar de seguir', 'boton-secundario boton-peligro', async () => {
        if (!window.confirm(`¿Dejar de seguir "${seguimiento.titulo}"? No le llegarán más avisos de este producto.`)) return;
        try {
            await dejarDeSeguir(seguimiento.oferta_id);
            await cargarSeguimientos('Listo, dejó de seguir ese producto.');
        } catch (error) {
            avisar(zona, error.message, 'error');
        }
    }));
    cuerpo.append(zona, acciones);
    item.append(imagen, cuerpo);
    return item;
}

function pintarCorreo(estadoCorreo) {
    const zona = $('estado-correo');
    zona.replaceChildren();
    if (!estadoCorreo.envio_configurado) {
        zona.append(crear('p', 'alerta-precio', 'Por ahora este servidor no puede enviar correos: los seguimientos '
            + 'se guardan y los precios se revisan, pero los avisos quedarán pendientes hasta que se configure el correo.'));
    }
    if (estadoCorreo.verificado) {
        zona.append(crear('p', null, `Los avisos llegan a ${estadoCorreo.correo}, que ya está verificado.`));
    } else {
        zona.append(crear('p', null, `Para recibir avisos, verifique su correo ${estadoCorreo.correo}.`));
        if (estadoCorreo.ultimo_envio?.texto) {
            const cuando = estadoCorreo.ultimo_envio.aceptado_en || estadoCorreo.ultimo_envio.pedido_en;
            zona.append(crear('p', 'ayuda', `${estadoCorreo.ultimo_envio.texto}${cuando ? ` (${fechaHora(cuando)})` : ''}`));
        }
        const resultado = crear('p', 'aviso-formulario');
        resultado.setAttribute('role', 'status');
        resultado.hidden = true;
        const pedir = boton('Enviarme el enlace para verificar', 'boton-principal', () => pedirVerificacion(resultado, pedir));
        zona.append(pedir, resultado);
    }
    const horas = Math.round((estadoCorreo.frecuencia_minutos || 0) / 60);
    zona.append(crear('p', 'ayuda', `Revisamos los precios aproximadamente cada ${horas >= 1 ? `${horas} ${horas === 1 ? 'hora' : 'horas'}`
        : `${estadoCorreo.frecuencia_minutos} minutos`}. Solo avisamos cuando el precio baja de verdad en la misma tienda, `
        + 'con el mismo vendedor y el mismo modelo.'));
}

/** Carga "Seguimientos". */
export async function cargarSeguimientos(mensaje) {
    const estado = $('estado-seguimientos');
    const lista = $('lista-seguimientos');
    mostrarEstado(estado, 'Cargando sus seguimientos…');
    try {
        const [correo, seguimientos] = await Promise.all([obtenerEstadoCorreo(), obtenerSeguimientos()]);
        pintarCorreo(correo);
        lista.replaceChildren(...seguimientos.map(crearSeguimiento));
        mostrarEstado(estado, seguimientos.length === 0
            ? 'Todavía no sigue ningún producto. En los resultados de una búsqueda pulse "Seguir producto".'
            : `Sigue ${seguimientos.length} ${seguimientos.length === 1 ? 'producto' : 'productos'}.`);
        if (mensaje) avisar($('resultado-seguimientos'), mensaje);
    } catch (error) {
        mostrarEstado(estado, error.message, () => cargarSeguimientos());
    }
}

export function iniciarSeguimientos() {
    $('formulario-seguir').addEventListener('submit', enviarSeguimiento);
    $('seguir-cancelar').addEventListener('click', cerrarDialogo);
    $('dialogo-seguir').addEventListener('close', () => botonOrigen?.focus());
    document.querySelectorAll('input[name="seguir-tipo"]').forEach((opcion) => opcion.addEventListener('change', () => {
        $('campo-objetivo').hidden = !$('seguir-tipo-objetivo').checked;
    }));
    $('seguir-verificar').addEventListener('click', (evento) => pedirVerificacion($('resultado-seguir'), evento.currentTarget));
}
