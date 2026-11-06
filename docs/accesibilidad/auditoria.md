# Auditoría de accesibilidad: Lighthouse y axe

Fecha: 5 de noviembre de 2026. Versión: rama `develop` con los adaptadores de Mercado Libre, Shopify y AliExpress.

Marketradar está pensado para adultos mayores, así que la meta es cumplir **WCAG 2.1 nivel AA**: contraste suficiente, todo usable con teclado, nombres claros para lectores de pantalla y textos que se puedan agrandar.

## Cómo se hizo

| Herramienta | Versión | Qué revisa |
|---|---|---|
| axe-core | 4.10.2 | Reglas WCAG 2.0 y 2.1 niveles A y AA, más buenas prácticas |
| Lighthouse | 12.2.1 | Accesibilidad, buenas prácticas, rendimiento y SEO; en celular y computador |
| Navegador | Chromium 1243 sin interfaz | |

- Web corriendo en local con PostgreSQL 16, los datos de `datos_demo.sql` y una tienda simulada que devuelve 4 ollas arroceras, para que las pantallas de resultados y de historial de precio tengan contenido real.
- Se entró como usuario (`rosa.demo@marketradar.co`) y como administrador (`admin.demo@marketradar.co`).
- La revisión de axe es automática con [auditoria-axe.js](auditoria-axe.js); cualquiera del equipo puede repetirla.

## Resultados de axe

| Pantalla | Reglas aprobadas | Fallas |
|---|---|---|
| Ingreso | 43 | 0 |
| Registro | 45 | 0 |
| Ingreso con alto contraste | 45 | 0 |
| Usuario: Inicio | 40 | 0 |
| Usuario: Buscar | 44 | 0 |
| Usuario: resultados de búsqueda con producto MVP | 44 | 0 |
| Usuario: historial de precio de una oferta | 41 | 0 |
| Usuario: Mis búsquedas | 40 | 0 |
| Usuario: Mi perfil | 45 | 0 |
| Usuario: PQR | 44 | 0 |
| Usuario: Tutorial | 41 | 0 |
| Usuario: Inicio en celular (390 × 844) | 40 | 0 |
| Administrador: lista de PQR | 39 | 0 |
| Administrador: detalle de una PQR | 39 | 0 |
| **Total** | **590** | **0** |

## Resultados de Lighthouse

Pantalla de ingreso, después de las correcciones:

| Categoría | Celular | Computador |
|---|---|---|
| Accesibilidad | 100 | 100 |
| Buenas prácticas | 100 | 100 |
| Rendimiento | 100 | 100 |
| SEO | 100 | 100 |

Antes de las correcciones, buenas prácticas daba 96 y SEO 91 (ver abajo).

Las pantallas con sesión (usuario y administrador) no se pudieron medir con Lighthouse de forma automática porque la herramienta abre el navegador sin la sesión iniciada y la página regresa al ingreso. Para medirlas a mano: ingresar en Chrome, abrir **F12** > **Lighthouse**, marcar las cuatro categorías y pulsar **Analyze page load**. La parte de accesibilidad de Lighthouse usa las mismas reglas de axe, que en esas pantallas dieron 0 fallas.

## Problemas encontrados y corregidos

| N.º | Problema | Dónde | Cómo se notó | Corrección |
|---|---|---|---|---|
| 1 | Al buscar sin resultados aparecía un recuadro amarillo vacío. La clase `.mvp` pone `display: grid` y eso le ganaba al atributo `hidden`. | Buscar | Captura de pantalla de la prueba | Regla global `[hidden] { display: none !important; }` en `estilos.css` |
| 2 | El aviso de tienda caída decía "La tienda shopify…" con el código interno en minúscula. | Buscar | Captura de pantalla | `ServicioBusqueda.nombreVisible` muestra "Shopify", "Mercado Libre" o "AliExpress" |
| 3 | `favicon.ico` y `robots.txt` respondían **error 500** en lugar de 404, y eso dejaba un error en la consola. | Todas | Lighthouse (buenas prácticas 96, SEO 91) | `ManejadorErrores` responde 404 cuando un archivo no existe; se agregaron `favicon.svg` y `robots.txt` |

El problema 1 no lo detecta ninguna herramienta automática: solo se vio en la captura. Por eso esta auditoría no reemplaza la prueba con personas del [guion de usabilidad](../usabilidad/guion-usabilidad.md).

## Lo que ya cumplía el diseño

- Enlace "Ir al contenido" al inicio de cada página.
- Botones A− y A+ para cambiar el tamaño de letra y modo de alto contraste.
- Foco visible en todos los controles.
- Formularios con etiqueta en cada campo y mensajes de error anunciados al lector de pantalla.
- Menú con texto además del ícono, y emojis ocultos al lector de pantalla.
- Aviso antes de abrir una tienda en otra pestaña.
- Política de seguridad de contenido (CSP) que bloquea scripts externos.

## Límites

- Las herramientas automáticas solo detectan una parte de los problemas de accesibilidad. Falta probar con un lector de pantalla (NVDA en Windows o TalkBack en Android) y con personas mayores.
- La tienda usada en la prueba era simulada; con tiendas reales pueden aparecer títulos muy largos o imágenes sin texto alternativo.

## Cómo repetir la auditoría

1. Levantar la web en `http://localhost:8080` y cargar `base-de-datos/datos-prueba/datos_demo.sql`.
2. En una carpeta aparte: `npm install playwright axe-core@4.10.2` y `npx playwright install chromium`.
3. Copiar `docs/accesibilidad/auditoria-axe.js` a esa carpeta y ejecutar `node auditoria-axe.js resultado.json`.
4. Para Lighthouse: Chrome > F12 > Lighthouse.
