# Al por mayor: Alibaba con API.SH

La sección **Al por mayor** busca proveedores de Alibaba con [API.SH](https://api.sh) y calcula el subtotal de la
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
- Escalas que se pisan, precios que suben al comprar más, un rango sin escalas o una variante con un precio que no
  coincide con las escalas: **precio ambiguo**, sin subtotal.
- Si los datos de la búsqueda no cuadran (mínimo distinto de la primera escala, rango distinto de las escalas, sin
  escalas), se consulta el detalle, como máximo `APISH_DETALLES_POR_BUSQUEDA` por búsqueda. Si búsqueda y detalle se
  contradicen, la oferta queda fuera del orden.
- Solo entran al orden por subtotal las ofertas con precio claro y en la misma moneda. Las demás se muestran después,
  con el motivo.
- Se quitan los repetidos (mismo id, o mismo título y proveedor) y los resultados que no mencionan lo buscado (solo si
  al menos el 30 % sí lo menciona; si no, se avisa que Alibaba funciona mejor en inglés). Los anuncios pagados se
  marcan.
- Cada subtotal dice **"Subtotal de mercancía; envío e impuestos pendientes"**. No se inventan envío, impuestos,
  disponibilidad para Colombia ni fechas de entrega. El valor en pesos es aproximado, con la TRM vigente.

## Caché y consumo

- API.SH guarda cada respuesta 24 horas y cobra igual las repetidas. Sus términos permiten guardar y mostrar los
  datos. Aquí se guardan en memoria `APISH_CACHE_HORAS` (6, máximo 24).
- Páginas por búsqueda: 3 (`paginas-maximas`).
- Tope diario: `APISH_CONSULTAS_POR_DIA` (200, hora de Colombia). El contador está en memoria y se reinicia si el
  servidor se reinicia. Por eso conviene poner también un **límite de gasto en el panel de API.SH** (llega como 402).
- La sección exige sesión, para que los créditos no los gaste cualquiera.

## Pendiente

Las llamadas reales no se probaron: no hay `APISH_KEY` y la red de desarrollo no llega a api.sh. Las pruebas usan
respuestas con la forma del OpenAPI publicado. Con la clave, conviene hacer una búsqueda de prueba y revisar que
`min_order`, `quantity_prices` y `price.tiers` lleguen como dice la documentación.
