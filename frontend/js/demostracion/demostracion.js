/**
 * Página de la tienda de demostración: aplica el tamaño de letra y el contraste guardados
 * y muestra el código del producto de ejemplo que se abrió.
 */
import { iniciarAccesibilidad } from '../comun/accesibilidad.js';

iniciarAccesibilidad();

const codigo = new URLSearchParams(window.location.search).get('producto');
if (codigo && /^DEMO-\d{3}$/.test(codigo)) {
    const parrafo = document.getElementById('producto-codigo');
    parrafo.textContent = `Código del producto de ejemplo: ${codigo}`;
    parrafo.hidden = false;
}
