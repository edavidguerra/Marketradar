/**
 * Construcción de las pantallas. Todo el texto se inserta con textContent para evitar inyección de HTML.
 */

import { fechaCorta, haceCuanto, nombreTienda, pesos } from './formato.js';

const PANTALLAS = ['inicio', 'resultados', 'detalle'];

export function mostrarPantalla(nombre) {
    for (const pantalla of PANTALLAS) {
        document.getElementById(`pantalla-${pantalla}`).hidden = pantalla !== nombre;
    }
    const titulo = document.querySelector(`#pantalla-${nombre} h1`);
    titulo.focus();
}

function crear(etiqueta, clase, texto) {
    const elemento = document.createElement(etiqueta);
    if (clase) elemento.className = clase;
    if (texto !== undefined) elemento.textContent = texto;
    return elemento;
}

export function mostrarEstado(contenedor, mensaje, reintentar) {
    contenedor.replaceChildren(crear('p', null, mensaje));
    if (reintentar) {
        const boton = crear('button', 'boton boton-secundario', 'Intentar de nuevo');
        boton.type = 'button';
        boton.addEventListener('click', reintentar);
        contenedor.append(boton);
    }
}

export function mostrarAdvertencias(lista, advertencias) {
    lista.replaceChildren(...advertencias.map((texto) => crear('li', null, texto)));
    lista.hidden = advertencias.length === 0;
}

export function crearTarjeta(oferta, alVerHistorial) {
    const tienda = nombreTienda(oferta.tienda);
    const tarjeta = crear('li', oferta.mejor_precio ? 'tarjeta mejor' : 'tarjeta');

    if (oferta.mejor_precio) {
        tarjeta.append(crear('p', 'etiqueta-mejor', '★ Mejor precio'));
    }
    if (oferta.url_imagen) {
        const imagen = document.createElement('img');
        imagen.src = oferta.url_imagen;
        imagen.alt = oferta.titulo;
        imagen.loading = 'lazy';
        tarjeta.append(imagen);
    }
    tarjeta.append(crear('h2', null, oferta.titulo));
    tarjeta.append(crear('p', 'precio', `${pesos(oferta.precio_total)} en total`));
    const envio = oferta.costo_envio == null ? 'Envío: consultar en la tienda' : `Incluye envío de ${pesos(oferta.costo_envio)}`;
    tarjeta.append(crear('p', null, envio));
    tarjeta.append(crear('p', null, `Tienda: ${tienda}`));
    tarjeta.append(crear('p', 'ayuda',
        `Precio consultado ${haceCuanto(oferta.consultado_en)}. Puede cambiar en la tienda.`));

    const acciones = crear('div', 'acciones');
    const enlace = crear('a', 'boton boton-principal', `Ver en ${tienda}`);
    enlace.href = oferta.url_destino;
    enlace.target = '_blank';
    enlace.rel = 'noopener noreferrer';
    enlace.addEventListener('click', (evento) => {
        const continuar = window.confirm(`Va a abrir la página de ${tienda} en otra pestaña.`);
        if (!continuar) evento.preventDefault();
    });
    acciones.append(enlace);

    if (oferta.id != null) {
        const botonHistorial = crear('button', 'boton boton-secundario', 'Ver historial de precio');
        botonHistorial.type = 'button';
        botonHistorial.addEventListener('click', () => alVerHistorial(oferta.id));
        acciones.append(botonHistorial);
    }
    tarjeta.append(acciones);
    return tarjeta;
}

function crearGrafico(historial) {
    const ancho = 600;
    const alto = 240;
    const margen = 20;
    const precios = historial.map((punto) => Number(punto.precio));
    const minimo = Math.min(...precios);
    const maximo = Math.max(...precios);
    const rango = maximo - minimo || 1;
    const pasoX = historial.length > 1 ? (ancho - 2 * margen) / (historial.length - 1) : 0;
    const puntos = precios.map((precio, i) => {
        const x = margen + i * pasoX;
        const y = alto - margen - ((precio - minimo) / rango) * (alto - 2 * margen);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
    });

    const svgNs = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNs, 'svg');
    svg.setAttribute('viewBox', `0 0 ${ancho} ${alto}`);
    svg.setAttribute('class', 'grafico');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', 'Gráfico del precio a lo largo del tiempo');
    const linea = document.createElementNS(svgNs, 'polyline');
    linea.setAttribute('points', puntos.join(' '));
    svg.append(linea);
    return svg;
}

function explicarHistorial(historial) {
    if (historial.length < 2) {
        return 'Todavía no tenemos cambios de precio para este producto. Vuelva a consultarlo otro día.';
    }
    const primero = historial[0];
    const ultimo = historial[historial.length - 1];
    const diferencia = Number(ultimo.precio) - Number(primero.precio);
    const desde = fechaCorta(primero.registrado_en);
    if (diferencia < 0) return `El precio bajó ${pesos(-diferencia)} desde el ${desde}.`;
    if (diferencia > 0) return `El precio subió ${pesos(diferencia)} desde el ${desde}.`;
    return `El precio es el mismo que el ${desde}.`;
}

export function mostrarDetalle(contenedor, oferta, historial) {
    const elementos = [
        crear('h2', null, oferta.titulo),
        crear('p', 'precio', `Precio actual: ${pesos(oferta.precio_total)}`),
    ];
    if (historial.length > 0) {
        elementos.push(crearGrafico(historial));
    }
    elementos.push(crear('p', null, explicarHistorial(historial)));
    const lista = crear('ul');
    for (const punto of historial) {
        lista.append(crear('li', null, `${fechaCorta(punto.registrado_en)}: ${pesos(punto.precio)}`));
    }
    elementos.push(lista);
    contenedor.replaceChildren(...elementos);
}
