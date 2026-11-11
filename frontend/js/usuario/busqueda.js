/**
 * Sección Buscar: resultados de todas las tiendas, la mejor opción de compra (producto MVP) con su
 * enlace directo, y las reseñas de compradores de cada producto.
 */

import { buscar, obtenerResenas, traducirResena } from '../comun/api.js';
import { crear, crearIcono, boton, enlaceTienda, mostrarEstado } from '../comun/dom.js';
import { decimal, estrellas, haceCuanto, pesos, tiendaDeOferta } from '../comun/formato.js';
import { irA } from './navegacion.js';
import { iniciarSugerencias } from './sugerencias.js';

const TAMANO_PAGINA = 10;
const estado = { termino: '', pagina: 1, exacto: false, precioMinimo: null, precioMaximo: null };

const $ = (id) => document.getElementById(id);

function validarTermino(termino) {
    if (termino.length < 2) return 'Escriba al menos 2 letras del producto que busca.';
    if (termino.length > 100) return 'Escriba un nombre más corto, de máximo 100 letras.';
    return '';
}

/** Lee "1.500.000", "$ 1500000" o "1,500,000" como 1500000; vacío es null y lo que no es número, NaN. */
export function leerPrecio(texto) {
    const limpio = String(texto ?? '').trim().replace(/^\$\s*/, '').replace(/,\d{1,2}$/, '').replace(/[.,\s]/g, '');
    if (!limpio) return null;
    return /^\d{1,15}$/.test(limpio) ? Number(limpio) : NaN;
}

/** "entre $ 1.000.000 y $ 3.000.000", "desde $ 1.000.000" o "hasta $ 3.000.000". */
export function textoRango(minimo, maximo) {
    if (minimo != null && maximo != null) return `entre ${pesos(minimo)} y ${pesos(maximo)}`;
    if (minimo != null) return `desde ${pesos(minimo)}`;
    return `hasta ${pesos(maximo)}`;
}

function hayFiltroPrecio() {
    return estado.precioMinimo != null || estado.precioMaximo != null;
}

function textoResultados(respuesta) {
    if (!hayFiltroPrecio()) {
        return respuesta.total_resultados === 0
            ? `No encontramos resultados para "${estado.termino}". Pruebe con otra palabra.`
            : `Encontramos ${respuesta.total_resultados} resultados para "${estado.termino}". Primero le mostramos `
              + 'la mejor opción de compra y después todas las ofertas, de la más barata a la más cara.';
    }
    const rango = textoRango(estado.precioMinimo, estado.precioMaximo);
    if (respuesta.total_resultados === 0) {
        return respuesta.total_sin_filtro > 0
            ? `No hay ofertas de "${estado.termino}" ${rango}. Sin el filtro hay ${respuesta.total_sin_filtro}: `
              + 'pruebe con otros precios o pulse "Quitar filtro".'
            : `No encontramos resultados para "${estado.termino}". Pruebe con otra palabra.`;
    }
    return `Encontramos ${respuesta.total_resultados} de ${respuesta.total_sin_filtro} resultados para `
        + `"${estado.termino}" con precio total ${rango}, de la más barata a la más cara.`;
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

/** "Envío gratis", "Incluye envío de $ 12.900" o, si la tienda no lo informa, que se confirma allá. */
function textoEnvio(oferta) {
    if (oferta.costo_envio == null) return 'Envío: se confirma en la tienda';
    return Number(oferta.costo_envio) === 0 ? 'Envío gratis' : `Incluye envío de ${pesos(oferta.costo_envio)}`;
}

/** Desde dónde vende la tienda; si es del exterior, avisa que puede tardar y cobrar impuestos. */
function crearUbicacion(oferta) {
    if (!oferta.ubicacion) {
        return crearDato('ubicacion', 'Vende desde: revíselo en la tienda');
    }
    const dato = crearDato('ubicacion', `Vende desde: ${oferta.ubicacion}${oferta.desde_exterior ? '.' : ''}`);
    if (oferta.desde_exterior) {
        dato.classList.add('dato-exterior');
        dato.append(crear('span', 'nota-exterior',
            ' Puede tardar más en llegar y cobrar impuestos de importación.'));
    }
    return dato;
}

/** El precio total, o el precio y un aviso si el envío no se sabe todavía. */
function crearPrecio(oferta, sufijo = '') {
    const precio = crear('p', 'precio', `${pesos(oferta.precio_total)}${sufijo}`);
    precio.append(crear('span', 'precio-nota', oferta.costo_envio == null ? ' más envío' : ' en total'));
    return precio;
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
    const precio = crearPrecio(oferta);
    const datos = crear('ul', 'datos');
    datos.append(
        crearDato('tienda', `Tienda: ${tienda}`),
        crearDato('camion', textoEnvio(oferta)),
        crearUbicacion(oferta),
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
    const datosMvp = crear('ul', 'datos');
    datosMvp.append(crearDato('camion', textoEnvio(oferta)), crearUbicacion(oferta));
    contenedor.replaceChildren(
        sello,
        imagen,
        crear('h2', null, oferta.titulo),
        crearPrecio(oferta, ` en ${tienda}`),
        crearCalificacion(oferta),
        datosMvp,
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
        const respuesta = await buscar(estado.termino, estado.pagina, TAMANO_PAGINA, estado.exacto,
            { precioMinimo: estado.precioMinimo, precioMaximo: estado.precioMaximo });
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
        mostrarEstado(estadoResultados, textoResultados(respuesta));
        // El filtro de precio aparece cuando hay algo que filtrar, o si ya se está usando.
        $('filtro-precio').hidden = (respuesta.total_sin_filtro ?? respuesta.total_resultados) === 0;
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
    // Una búsqueda nueva empieza sin filtro de precio, para no esconder resultados sin que la persona lo note.
    limpiarFiltroPrecio();
    $('filtro-precio').hidden = true;
    recargarResultados();
    irA('buscar');
}

function recargarResultados() {
    estado.pagina = 1;
    $('lista-resultados').replaceChildren();
    mostrarResumen(null);
    mostrarMvp(null);
    mostrarCorreccion(null);
    cargarPagina();
}

function limpiarFiltroPrecio() {
    estado.precioMinimo = null;
    estado.precioMaximo = null;
    $('precio-minimo').value = '';
    $('precio-maximo').value = '';
    $('quitar-filtro-precio').hidden = true;
    mostrarErrorPrecio('', null);
}

function mostrarErrorPrecio(mensaje, campo) {
    const error = $('error-precio');
    error.textContent = mensaje;
    error.hidden = !mensaje;
    ['precio-minimo', 'precio-maximo'].forEach((id) =>
        $(id).setAttribute('aria-invalid', String(Boolean(mensaje) && (campo === null || campo === id))));
    if (mensaje && campo) $(campo).focus();
}

/** Valida los dos precios y vuelve a mostrar los resultados de la misma búsqueda, sin consultar otra vez las tiendas. */
function aplicarFiltroPrecio(evento) {
    evento.preventDefault();
    const minimo = leerPrecio($('precio-minimo').value);
    const maximo = leerPrecio($('precio-maximo').value);
    if (Number.isNaN(minimo) || Number.isNaN(maximo)) {
        mostrarErrorPrecio('Escriba el precio solo con números, por ejemplo 500.000.',
            Number.isNaN(minimo) ? 'precio-minimo' : 'precio-maximo');
        return;
    }
    if (minimo == null && maximo == null) {
        mostrarErrorPrecio('Escriba al menos un precio: desde cuánto, hasta cuánto o los dos.', 'precio-minimo');
        return;
    }
    if (minimo != null && maximo != null && minimo > maximo) {
        mostrarErrorPrecio('El precio "Desde" es mayor que el precio "Hasta". Revise los dos valores.', 'precio-minimo');
        return;
    }
    mostrarErrorPrecio('', null);
    estado.precioMinimo = minimo;
    estado.precioMaximo = maximo;
    $('precio-minimo').value = minimo == null ? '' : minimo.toLocaleString('es-CO');
    $('precio-maximo').value = maximo == null ? '' : maximo.toLocaleString('es-CO');
    $('quitar-filtro-precio').hidden = false;
    recargarResultados();
}

function iniciarFiltroPrecio() {
    $('filtro-precio').addEventListener('submit', aplicarFiltroPrecio);
    $('quitar-filtro-precio').addEventListener('click', () => {
        limpiarFiltroPrecio();
        $('precio-minimo').focus();
        recargarResultados();
    });
    // Al salir del campo se ponen los puntos de miles, para que la persona vea bien la cifra.
    ['precio-minimo', 'precio-maximo'].forEach((id) => $(id).addEventListener('blur', () => {
        const valor = leerPrecio($(id).value);
        if (valor != null && !Number.isNaN(valor)) $(id).value = valor.toLocaleString('es-CO');
    }));
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

/**
 * Botón "Traducir al español" de una reseña en otro idioma. La primera vez pide la traducción; después alterna
 * entre la traducción y el texto original sin volver a pedirla. El cambio se anuncia a los lectores de pantalla.
 */
function crearTraduccion(oferta, resena, idioma, titulo, texto, aviso) {
    const original = { titulo: resena.titulo, texto: resena.texto };
    let traduccion = null;
    let mostrandoTraduccion = false;
    const elemento = boton(`Traducir al ${idioma}`, 'boton-secundario boton-traducir', async () => {
        if (mostrandoTraduccion) {
            mostrar(original, '');
            mostrandoTraduccion = false;
            elemento.textContent = `Traducir al ${idioma}`;
            elemento.prepend(crearIcono('idioma'));
            return;
        }
        elemento.disabled = true;
        elemento.textContent = 'Traduciendo…';
        try {
            traduccion ??= await traducirResena(oferta.id, resena.indice);
            const desde = traduccion.nombre_idioma_original ? ` del ${traduccion.nombre_idioma_original}` : '';
            mostrar(traduccion, `Traducido automáticamente${desde} con inteligencia artificial.`);
            mostrandoTraduccion = true;
            elemento.textContent = 'Ver el texto original';
        } catch (error) {
            aviso.textContent = error.message;
            aviso.hidden = false;
            elemento.textContent = `Traducir al ${idioma}`;
        } finally {
            elemento.disabled = false;
            elemento.prepend(crearIcono('idioma'));
        }
    });
    elemento.prepend(crearIcono('idioma'));

    function mostrar(contenido, nota) {
        if (titulo) titulo.textContent = contenido.titulo || original.titulo || '';
        texto.textContent = contenido.texto;
        aviso.textContent = nota;
        aviso.hidden = !nota;
    }
    return elemento;
}

function crearResena(resena, oferta, idioma) {
    const elemento = crear('li', 'resena');
    if (resena.calificacion != null) {
        const nota = crear('p', 'calificacion');
        const figura = crear('span', 'estrellas', estrellas(resena.calificacion));
        figura.setAttribute('aria-hidden', 'true');
        nota.append(figura, ` ${decimal(resena.calificacion)} de 5`);
        elemento.append(nota);
    }
    const titulo = resena.titulo ? crear('h4', null, resena.titulo) : null;
    const texto = resena.texto ? crear('p', 'resena-texto', resena.texto) : null;
    if (titulo) elemento.append(titulo);
    if (texto) elemento.append(texto);
    const firma = [resena.autor, resena.fuente && `en ${resena.fuente}`, resena.fecha].filter(Boolean).join(' · ');
    if (firma) elemento.append(crear('p', 'ayuda', firma));
    if (resena.traducible && texto) {
        // Si está en otro idioma que el de la cuenta, se ofrece traducirla. El aviso dice que es una traducción.
        const aviso = crear('p', 'nota-traduccion');
        aviso.setAttribute('role', 'status');
        aviso.hidden = true;
        elemento.append(crearTraduccion(oferta, resena, idioma, titulo, texto, aviso), aviso);
    }
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
            crearPrecio(oferta, ` en ${tienda}`),
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
                const idioma = resenas.nombre_idioma_usuario || 'español';
                resenas.resenas.forEach((resena) => lista.append(crearResena(resena, oferta, idioma)));
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
    iniciarFiltroPrecio();
}
