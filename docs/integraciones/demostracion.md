# Tienda de demostración

`AdaptadorDemostracion` es una tienda de ejemplo que vive dentro de Marketradar. No llama a ninguna API: busca en
42 productos guardados en `backend/src/main/resources/demostracion/catalogo.json`. Sirve para mostrar y probar el
comparador (búsqueda, producto MVP, historial de precio y chatbot) mientras AliExpress, Shopify o Mercado Libre no
tienen claves.

## Cómo se prende

| Variable | Valor | Para qué |
|---|---|---|
| `DEMOSTRACION_HABILITADO` | `true` | Muestra la tienda en las búsquedas. En `false` (por defecto) no aparece. |
| `DEMOSTRACION_URL` | Opcional | Dirección pública de Marketradar para los enlaces. En Render no hace falta: se usa `RENDER_EXTERNAL_URL`, que Render pone solo. En local queda `http://localhost:8080`. |

La migración V7 agrega la fila `demostracion` a la tabla `marketplace`, así que sus ofertas se guardan y tienen
historial de precio como las de cualquier otra tienda.

## Cómo se ve

- Las ofertas dicen **Tienda: Tienda de demostración**, para que nadie las confunda con una tienda real.
- El botón **Ver en Tienda de demostración** abre `tienda-demostracion.html`, que explica que el producto es de
  ejemplo y no se vende.
- Cuando el chatbot encuentra productos de esta tienda, Marketradar le avisa a Claude que son de ejemplo para que se
  lo diga a la persona.

## Cómo busca

1. Pasa lo que escribió la persona a minúsculas, sin tildes, y quita palabras como "de", "para" o "barato".
2. Un producto aparece si **todas** las palabras que quedan están en su título o en su lista de `palabras`.
   También acepta plurales sencillos ("ollas" encuentra "olla").
3. Devuelve máximo 20 productos. Marketradar los ordena y elige el MVP como con cualquier tienda.

Algunas búsquedas que funcionan: olla arrocera, licuadora, tensiómetro, glucómetro, lupa, celular, bastón,
caminador, pastillero, ventilador, cafetera, televisor, radio, silla de ducha, termómetro, colchón, almohada,
audífono.

## Cómo agregar productos

Agregar un objeto a `catalogo.json` con `id` (`DEMO-` y tres números, sin repetir), `titulo`, `precio` y `envio` en
pesos, `condicion` (`nuevo`, `usado` o `reacondicionado`), `vendedor`, `calificacion` (1 a 5), `resenas` y
`palabras` (otras formas de llamarlo, sin tildes). Los precios son aproximados y solo de ejemplo.

## Cuándo apagarla

Cuando una tienda real esté conectada, poner `DEMOSTRACION_HABILITADO=false` en Render para que los resultados sean
solo reales. Para la sustentación se puede dejar prendida y explicar que es la tienda de ejemplo.
