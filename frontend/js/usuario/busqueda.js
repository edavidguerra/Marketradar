/**
 * Sección Buscar: resultados de todas las tiendas, la mejor opción de compra (producto MVP) con su
 * enlace directo, y las reseñas de compradores de cada producto.
 */

import { buscar, obtenerResenas } from '../comun/api.js';
import { crear, crearIcono, boton, enlaceTienda, mostrarEstado } from '../comun/dom.js';
import { decimal, estrellas, haceCuanto, pesos, tiendaDeOferta } from '../comun/formato.js';
import { irA } from './navegacion.js';
import { iniciarSugerencias } from './sugerencias.js';

const TAMANO_PAGINA = 10;
const estado = { termino: '', pagina: 1, exacto: false };

const $ = (id) => document.getElementById(id);

function validarTermino(termino) {
    if (termino.length < 2) return 'Escriba al menos 2 letras del producto que busca.';
    if (termino.length > 100) return 'Escriba un nombre más corto, de máximo 100 letras.';
    return '';
}

function crearCalificacion(oferta) {
    if (oferta.calificacion == null || !oferta.total_resenas) {
        return crear('p', 'ayuda', 'Sin opiniones de compradores');
    }
    const parrafo = crear('p', 'calificacion');
    const figura = crear('span', 'estrellas', estrellas(oferta.calificacion));
    figura.setAttribute('aria-hidden', 'true');
    parrafo.append(figura, ` ${decimal(oferta.calificacion)} de 5 (${oferta.total_resenas.toLocaleString('es-CO')} opiniones)`);
    return parrafo;
}

function crearDato(icono, texto) {
    const dato = crear('li', 'dato');
    dato.append(crearIcono(icono), texto);
    return dato;
}

function crearTarjeta(oferta) {
    const tienda = tiendaDeOferta(oferta);
    const tarjeta = crear('li', oferta.mejor_precio ? 'tarjeta mejor' : 'tarjeta');
    const imagen = crear('div', 'tarjeta-imagen');
    if (oferta.url_imagen) {
        const foto = document.createElement('img');
        foto.src = oferta.url_imagen;
        foto.alt = oferta.titulo;
        foto.loading = 'lazy';
        imagen.append(foto);
    } else {
        imagen.append(crearIcono('etiqueta'));
    }
    const cuerpo = crear('div', 'tarjeta-cuerpo');
    if (oferta.mejor_precio) cuerpo.append(crear('p', 'etiqueta-mejor', '★ Precio más bajo'));
    const precio = crear('p', 'precio', pesos(oferta.precio_total));
    precio.append(crear('span', 'precio-nota', ' en total'));
    const datos = crear('ul', 'datos');
    datos.append(
        crearDato('tienda', `Tienda: ${tienda}`),
        crearDato('camion', oferta.costo_envio == null ? 'Envío: consultar en la tienda'
            : Number(oferta.costo_envio) === 0 ? 'Envío gratis' : `Incluye envío de ${pesos(oferta.costo_envio)}`),
    );
    cuerpo.append(
        crear('h2', null, oferta.titulo),
        precio,
        crearCalificacion(oferta),
        datos,
        crear('p', 'ayuda', `Precio consultado ${haceCuanto(oferta.consultado_en)}. Puede cambiar en la tienda.`),
    );
    const acciones = crear('div', 'acciones');
    acciones.append(enlaceTienda(`Ver en ${tienda}`, oferta.url_destino, tienda));
    if (oferta.resenas_disponibles) {
        acciones.append(botonResenas(oferta));
    }
    cuerpo.append(acciones);
    tarjeta.append(imagen, cuerpo);
    return tarjeta;
}

/** Tarjetas grises animadas que ocupan el lugar de los resultados mientras llegan. */
function crearEsqueletos(cantidad) {
    return Array.from({ length: cantidad }, () => {
        const esqueleto = crear('li', 'tarjeta esqueleto');
        esqueleto.setAttribute('aria-hidden', 'true');
        esqueleto.append(crear('div', 'tarjeta-imagen'), crear('div', 'tarjeta-cuerpo'));
        return esqueleto;
    });
}

function mostrarMvp(mvp) {
    const contenedor = $('producto-mvp');
    if (!mvp) {
        contenedor.replaceChildren();
        contenedor.hidden = true;
        return;
    }
    const { oferta } = mvp;
    const tienda = tiendaDeOferta(oferta);
    const razones = crear('ul');
    mvp.razones.forEach((razon) => razones.append(crear('li', null, razon)));
    const sello = crear('p', 'sello');
    sello.append(crearIcono('estrella'), 'Nuestra recomendación: la mejor opción de compra');
    const barra = crear('div', 'puntaje');
    barra.setAttribute('aria-hidden', 'true');
    const relleno = crear('span');
    relleno.style.setProperty('--puntaje', `${Math.max(0, Math.min(100, Number(mvp.puntaje)))}%`);
    barra.append(relleno);
    // La foto del producto recomendado, igual que en las tarjetas; si no hay, un ícono.
    const imagen = crear('div', 'tarjeta-imagen mvp-imagen');
    if (oferta.url_imagen) {
        const foto = document.createElement('img');
        foto.src = oferta.url_imagen;
        foto.alt = oferta.titulo;
        imagen.append(foto);
    } else {
        imagen.append(crearIcono('etiqueta'));
    }
    contenedor.replaceChildren(
        sello,
        imagen,
        crear('h2', null, oferta.titulo),
        crear('p', 'precio', `${pesos(oferta.precio_total)} en ${tienda}`),
        crearCalificacion(oferta),
        crear('p', 'razones-titulo', '¿Por qué se la recomendamos?'),
        razones,
        crear('p', 'ayuda', `Puntaje de compra: ${mvp.puntaje} de 100, según precio, opiniones y envío.`),
        barra,
        enlaceTienda(`Comprar este producto en ${tienda}`, mvp.enlace_compra, tienda),
    );
    if (oferta.resenas_disponibles) contenedor.append(botonResenas(oferta));
    contenedor.hidden = false;
}

/**
 * Como Google: si la persona escribió con errores, se buscó directamente lo que quiso decir y aquí se le cuenta,
 * con un botón por si de verdad quería buscar lo que escribió.
 */
function mostrarCorreccion(respuesta) {
    const parrafo = $('quizas');
    const original = respuesta && respuesta.termino_original;
    if (!original) {
        parrafo.replaceChildren();
        parrafo.hidden = true;
        return;
    }
    const buscado = crear('strong', null, respuesta.termino);
    const primeraParte = respuesta.busqueda_ampliada
        ? [`No encontramos "${original}" exacto. Le mostramos resultados de `, buscado, '.']
        : ['Le mostramos resultados de ', buscado, '.'];
    const exacto = boton(`Buscar "${original}" tal como lo escribió`, 'boton-enlace',
        () => buscarTermino(original, { exacto: true }));
    parrafo.replaceChildren(...primeraParte, ' ', exacto);
    parrafo.hidden = false;
}

/** Resumen escrito con IA, como el de Google, arriba de la recomendación. */
function mostrarResumen(texto) {
    $('texto-resumen-ia').textContent = texto || '';
    $('resumen-ia').hidden = !texto;
}

function mostrarAdvertencias(advertencias) {
    const lista = $('advertencias');
    lista.replaceChildren(...advertencias.map((texto) => crear('li', null, texto)));
    lista.hidden = advertencias.length === 0;
}

async function cargarPagina() {
    const estadoResultados = $('estado-resultados');
    mostrarEstado(estadoResultados, 'Buscando los mejores precios, un momento por favor…');
    $('ver-mas').hidden = true;
    const esqueletos = crearEsqueletos(estado.pagina === 1 ? 3 : 1);
    $('lista-resultados').append(...esqueletos);
    // El botón Buscar muestra un círculo que gira mientras llegan los precios.
    const botonBuscar = document.querySelector('#formulario-busqueda [type="submit"]');
    botonBuscar.classList.add('buscando');
    try {
        const respuesta = await buscar(estado.termino, estado.pagina, TAMANO_PAGINA, estado.exacto);
        mostrarAdvertencias(respuesta.advertencias || []);
        if (estado.pagina === 1) {
            mostrarResumen(respuesta.resumen_ia);
            mostrarMvp(respuesta.producto_mvp);
            mostrarCorreccion(respuesta);
            if (respuesta.termino_original) {
                // Las páginas siguientes y el campo usan lo que se buscó de verdad, sin volver a corregir.
                estado.termino = respuesta.termino;
                estado.exacto = true;
                $('campo-busqueda').value = respuesta.termino;
            }
        }
        $('lista-resultados').append(...respuesta.ofertas.map(crearTarjeta));
        mostrarEstado(estadoResultados, respuesta.total_resultados === 0
            ? `No encontramos resultados para "${estado.termino}". Pruebe con otra palabra.`
            : `Encontramos ${respuesta.total_resultados} resultados para "${estado.termino}". Primero le mostramos `
              + 'la mejor opción de compra y después todas las ofertas, de la más barata a la más cara.');
        $('ver-mas').hidden = estado.pagina * TAMANO_PAGINA >= respuesta.total_resultados;
    } catch (error) {
        mostrarEstado(estadoResultados, error.message, cargarPagina);
    } finally {
        esqueletos.forEach((esqueleto) => esqueleto.remove());
        botonBuscar.classList.remove('buscando');
    }
}

/** Busca un término; se usa también desde Inicio e Historial. */
export function buscarTermino(termino, { exacto = false } = {}) {
    $('campo-busqueda').value = termino;
    estado.termino = termino;
    estado.pagina = 1;
    estado.exacto = exacto;
    $('lista-resultados').replaceChildren();
    mostrarResumen(null);
    mostrarMvp(null);
    mostrarCorreccion(null);
    irA('buscar');
    cargarPagina();
}

function botonResenas(oferta) {
    const elemento = boton('Ver reseñas', 'boton-secundario', () => verResenas(oferta));
    elemento.prepend(crearIcono('estrella'));
    return elemento;
}

/** Barras con cuántas personas dieron 5, 4, 3, 2 y 1 estrellas. Se leen también como texto. */
function crearDistribucion(distribucion) {
    const total = distribucion.reduce((suma, fila) => suma + fila.cantidad, 0) || 1;
    const lista = crear('ul', 'distribucion');
    distribucion.forEach((fila) => {
        const elemento = crear('li');
        const etiqueta = crear('span', 'distribucion-estrellas', `${fila.estrellas} ${fila.estrellas === 1 ? 'estrella' : 'estrellas'}`);
        const barra = crear('span', 'distribucion-barra');
        barra.setAttribute('aria-hidden', 'true');
        const relleno = crear('span');
        relleno.style.setProperty('--parte', `${Math.round((fila.cantidad / total) * 100)}%`);
        barra.append(relleno);
        const cantidad = crear('span', 'distribucion-cantidad', `${fila.cantidad.toLocaleString('es-CO')} opiniones`);
        elemento.append(etiqueta, barra, cantidad);
        lista.append(elemento);
    });
    return lista;
}

function crearResena(resena) {
    const elemento = crear('li', 'resena');
    if (resena.calificacion != null) {
        const nota = crear('p', 'calificacion');
        const figura = crear('span', 'estrellas', estrellas(resena.calificacion));
        figura.setAttribute('aria-hidden', 'true');
        nota.append(figura, ` ${decimal(resena.calificacion)} de 5`);
        elemento.append(nota);
    }
    if (resena.titulo) elemento.append(crear('h4', null, resena.titulo));
    if (resena.texto) elemento.append(crear('p', 'resena-texto', resena.texto));
    const firma = [resena.autor, resena.fuente && `en ${resena.fuente}`, resena.fecha].filter(Boolean).join(' · ');
    if (firma) elemento.append(crear('p', 'ayuda', firma));
    return elemento;
}

async function verResenas(oferta) {
    const contenedor = $('contenido-detalle');
    const tienda = tiendaDeOferta(oferta);
    irA('detalle');
    mostrarEstado(contenedor, 'Cargando las reseñas de los compradores…');
    try {
        const resenas = await obtenerResenas(oferta.id);
        const partes = [
            crear('h2', null, oferta.titulo),
            crear('p', 'precio', `${pesos(oferta.precio_total)} en ${tienda}`),
        ];
        if (!resenas.disponibles) {
            partes.push(crear('p', null, 'Este producto todavía no tiene reseñas de compradores.'));
        } else {
            if (resenas.calificacion != null) {
                const resumen = crear('p', 'calificacion resumen-resenas');
                const figura = crear('span', 'estrellas', estrellas(resenas.calificacion));
                figura.setAttribute('aria-hidden', 'true');
                const total = resenas.total_resenas ? ` (${resenas.total_resenas.toLocaleString('es-CO')} opiniones)` : '';
                resumen.append(figura, ` ${decimal(resenas.calificacion)} de 5${total}`);
                partes.push(resumen);
            }
            if (resenas.distribucion.length > 0) partes.push(crearDistribucion(resenas.distribucion));
            if (resenas.resenas.length > 0) {
                const lista = crear('ul', 'resenas');
                resenas.resenas.forEach((resena) => lista.append(crearResena(resena)));
                partes.push(crear('h3', 'resenas-titulo', 'Lo que dicen los compradores'), lista);
            } else {
                partes.push(crear('p', 'ayuda', 'Los compradores calificaron este producto, pero no dejaron comentarios escritos.'));
            }
        }
        partes.push(enlaceTienda(`Ver en ${tienda}`, oferta.url_destino, tienda));
        contenedor.replaceChildren(...partes);
    } catch (error) {
        mostrarEstado(contenedor, error.message, () => verResenas(oferta));
    }
}

export function iniciarBusqueda() {
    const formulario = $('formulario-busqueda');
    const campo = $('campo-busqueda');
    const error = $('error-busqueda');
    formulario.addEventListener('submit', (evento) => {
        evento.preventDefault();
        const termino = campo.value.trim();
        const mensaje = validarTermino(termino);
        error.textContent = mensaje;
        error.hidden = !mensaje;
        campo.setAttribute('aria-invalid', String(Boolean(mensaje)));
        if (mensaje) {
            campo.focus();
            return;
        }
        buscarTermino(termino);
    });
    iniciarSugerencias(campo, $('lista-sugerencias'), $('estado-sugerencias'), (texto) => {
        error.hidden = true;
        campo.setAttribute('aria-invalid', 'false');
        buscarTermino(texto);
    });
    $('ver-mas').addEventListener('click', () => {
        estado.pagina += 1;
        cargarPagina();
    });
    $('volver-resultados').addEventListener('click', () => irA('buscar'));
}
