# Emprender (al por mayor): Alibaba con API.SH

La sección **Emprender** (compras al por mayor) busca proveedores de Alibaba con [API.SH](https://api.sh) y calcula el subtotal de la
mercancía para la cantidad pedida. La compra se hace en Alibaba con el enlace original.

Documentación revisada el 14 de noviembre de 2026: <https://api.sh/docs>, <https://api.sh/openapi/alibaba.json> y
<https://api.sh/legal/terms>.

## Cómo se llama

- `GET https://api.api.sh/alibaba/v1/search?q=…&page=…`: 48 resultados por página, `next_page`, `sponsored`,
  `quantity_prices[]` (`min_quantity`, `max_quantity`, `price`, `unit`), `min_order` como texto ("100 pairs").
- `GET https://api.api.sh/alibaba/v1/product?url=…`: `price.tiers[]`, `min_order` como objeto (`quantity`, `unit`),
  `skus[]` y `options[]` (variantes y disponibilidad).
- La clave va en el encabezado `x-api-key`, desde la variable `APISH_KEY`. Nunca llega al navegador ni a los
  registros.
- Errores: 400 (parámetro), 401/403 (clave), 402 (sin créditos o límite de gasto), 404, 429 (se respeta
  `retry-after` y no se consulta mientras tanto), 502/503 o sin respuesta. Cada uno llega a la persona como un mensaje
  claro (503 o 400), nunca como un error interno.

Código: `adapter/alibaba/ClienteAlibabaApiSh` (HTTP), `LectorRespuestasAlibaba` (formatos),
`domain/mayorista/CalculadoraMayorista` (cálculo) y `service/mayorista/ServicioMayoristas`.

## Cálculo por cantidad

- La persona elige cantidad y unidad (piezas, pares, paquetes, juegos, cajas…).
- Unidades distintas **no se convierten** salvo que el proveedor informe la equivalencia ("pack of 12 pairs"). En ese
  caso se redondea a paquetes completos y se dice cuántas unidades recibe.
- Pedido mínimo efectivo = el mayor entre el informado y el inicio de la primera escala. Por debajo: "El pedido mínimo
  es de…", sin precio.
- La cantidad es un número entero entre 1 y 10.000.000. Los miles solo se aceptan con punto y grupos completos
  (`10.000`, `1.000.000`). Se rechazan `1.5`, `1,5`, `1 5`, `0`, `-1`, vacío y lo que pase del tope; el navegador y el
  servidor usan la misma regla y la página repite la cantidad que entendió ("Cantidad: 10.000 piezas").
- Escalas que se pisan, precios que suben al comprar más o un rango sin escalas: **precio ambiguo**, sin subtotal. El
  precio de una variante solo vale si es igual al de la escala que corresponde a esa cantidad; si no, se explica para
  qué cantidades no cuadra.
- Si los datos de la búsqueda no cuadran (mínimo distinto de la primera escala, rango distinto de las escalas, sin
  escalas) o la persona eligió una variante, se consulta la ficha (`/product`). Con ficha, precios, escalas, mínimo,
  unidad y moneda salen **solo de la ficha**: nunca se reutilizan las escalas de la búsqueda como si la ficha las
  hubiera confirmado. Si búsqueda y ficha se contradicen (moneda, unidad, mínimo, escalas, o una ficha sin escalas
  cuyas variantes cuestan otra cosa, como el caso de US$0,49 en la búsqueda y variantes desde US$5,00), la oferta sale
  del orden con el motivo. Si la búsqueda se contradice sola y no hubo ficha, también queda fuera: no se adivina.
- Al abrir "Ver variantes y detalle" o elegir una variante, la lista se vuelve a pedir y la tarjeta, el subtotal y el
  puesto cambian juntos.
- "Ver más" pide las páginas 1 a N y el servidor las junta: quita repetidos por id, ordena **todas** las ofertas
  cargadas y marca con estrella un solo mínimo **entre las verificadas**, o el empate si hay varios con el mismo
  subtotal. Una oferta con precio preliminar puede quedar antes en el orden y verse más barata: la página lo dice y no
  afirma que la recomendada sea el precio más bajo de todas las ofertas cargadas. Cambiar producto, cantidad o unidad en el formulario no cambia los resultados hasta pulsar
  Buscar (la página avisa que hay cambios sin aplicar): resumen, tarjetas, detalle y páginas siguientes usan siempre la
  búsqueda enviada, y la unidad se escribe con el código que confirmó el servidor y el catálogo de unidades. Buscar de
  nuevo empieza de cero; una respuesta que llega tarde
  de una búsqueda anterior se ignora, y el botón no pide dos veces mientras carga.
- Relevancia: se reconoce el tipo de producto (aretes, collar, pulsera, anillo, forro, cable, medias…) y el material
  (acero, plata, oro, titanio…), en español e inglés, singular y plural. Otro tipo u otro material se quita del todo
  (y se cuenta en el aviso). Si el título no confirma el material ("steel" a secas, "titanium steel") o es dudoso,
  la oferta va aparte, en "Podrían no ser lo que busca", fuera del orden. Lo que el título no dice no se da por
  confirmado. Un baño ("gold plated") no cambia el material de base.
- Cada subtotal dice **"Subtotal de mercancía; envío e impuestos pendientes"**. No se inventan envío, impuestos,
  disponibilidad para Colombia ni fechas de entrega. El valor en pesos es aproximado, con la TRM vigente.

## Caché y consumo

- API.SH guarda cada respuesta 24 horas y cobra igual las repetidas. Sus términos permiten guardar y mostrar los
  datos. Aquí se guardan en memoria `APISH_CACHE_HORAS` (6, máximo 24).
- Páginas por búsqueda: 3 (`paginas-maximas`).
- Cada intento a API.SH cuenta para el tope, también los que fallan o se agotan; leer de la caché no cuenta.
- Cada búsqueda tiene 45 segundos en total. Una página que falla se reintenta una vez; si falla la página 2 o 3 se
  muestran las anteriores y "Ver más" la ofrece otra vez. Tras 2 fichas seguidas con error se dejan de pedir fichas en
  esa búsqueda y se avisa.
- Tope diario: `APISH_CONSULTAS_POR_DIA` (200, hora de Colombia). El contador está en memoria y se reinicia si el
  servidor se reinicia. Por eso conviene poner también un **límite de gasto en el panel de API.SH** (llega como 402).
- La sección exige sesión, para que los créditos no los gaste cualquiera.

## Pendiente

Las llamadas reales no se probaron: no hay `APISH_KEY` y la red de desarrollo no llega a api.sh. Las pruebas usan
respuestas con la forma del OpenAPI publicado. Con la clave, conviene hacer una búsqueda de prueba y revisar que
`min_order`, `quantity_prices` y `price.tiers` lleguen como dice la documentación.
