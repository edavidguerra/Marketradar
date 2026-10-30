# Análisis de normalización del esquema

Este documento revisa las tablas creadas por las migraciones V1 a V3 de `base-de-datos/migraciones` y justifica que el
esquema cumple la primera, la segunda y la tercera forma normal (1FN, 2FN y 3FN). También explica las decisiones de
diseño y las dos redundancias que se aceptaron a propósito. Las vistas de V4 no se analizan porque no guardan datos.

Notación: `A → B` se lee "A determina funcionalmente a B": para un mismo valor de A siempre hay un único valor de B.

## 1. Formas normales que se verifican

- **1FN:** cada columna guarda un solo valor atómico, no hay grupos repetidos ni listas dentro de una celda, y cada
  tabla tiene llave primaria.
- **2FN:** está en 1FN y ningún atributo que no es llave depende solo de una parte de una llave candidata compuesta.
- **3FN:** está en 2FN y ningún atributo que no es llave depende de otro atributo que tampoco es llave (no hay
  dependencias transitivas).

Además se revisa la forma normal de Boyce-Codd (FNBC): todo determinante es llave candidata.

## 2. Llaves candidatas

Todas las tablas usan una llave primaria sustituta (`id` autoincremental). Varias tienen además una llave natural,
declarada con `UNIQUE` para que la base la proteja:

| Tabla | Llave primaria | Otras llaves candidatas |
| --- | --- | --- |
| `marketplace` | `id` | `codigo` |
| `producto` | `id` | Ninguna |
| `oferta` | `id` | `(marketplace_id, id_externo)` |
| `historial_precio` | `id` | Ninguna (ver sección 5) |
| `busqueda` | `id` | Ninguna |
| `consumo_api` | `id` | Ninguna |
| `usuario` | `id` | `correo`; `(tipo_documento, numero_documento)` cuando ambos tienen valor |
| `pqr` | `id` | Ninguna |
| `tasa_cambio` | `id` | `(moneda_origen, moneda_destino, vigente_desde)` |

## 3. Dependencias funcionales por tabla

**marketplace**

- `id → codigo, nombre, sitio_web, activo`
- `codigo → id, nombre, sitio_web, activo`

Los dos determinantes son llaves candidatas. Cumple FNBC.

**producto**

- `id → nombre_normalizado, marca, creado_en`

Cumple FNBC.

**oferta**

- `id → producto_id, marketplace_id, id_externo, titulo, url_destino, url_imagen, precio_actual, moneda, costo_envio,
  condicion, vendedor, calificacion, total_resenas, consultado_en`
- `(marketplace_id, id_externo) → id` y, por lo tanto, todos los demás atributos.

Ningún atributo depende solo de `marketplace_id` o solo de `id_externo`: los datos de la tienda (nombre, sitio web)
están en `marketplace` y no se repiten en cada oferta. Por eso cumple 2FN con respecto a la llave compuesta. Tampoco
hay dependencias transitivas: `vendedor` no determina nada más, porque el mismo vendedor publica muchas ofertas con
precios distintos. Cumple FNBC.

**historial_precio**

- `id → oferta_id, precio, moneda, registrado_en`

Cumple FNBC.

**busqueda**

- `id → termino, total_resultados, duracion_ms, creada_en, usuario_id`

`total_resultados` no se puede calcular después a partir de otras tablas, porque depende de lo que respondieron las
tiendas en ese momento; es un hecho de la búsqueda y no una redundancia. Cumple FNBC.

**consumo_api**

- `id → marketplace_id, exitoso, codigo_http, duracion_ms, registrado_en`

Podría pensarse que `codigo_http → exitoso`, pero no se usa como dependencia: una llamada sin respuesta (tiempo
agotado) no tiene código HTTP, y por ahora el backend registra cada llamada solo con `exitoso` y deja `codigo_http` en
nulo. Por eso `exitoso` se guarda como dato propio de la llamada. Cumple FNBC.

**usuario**

- `id → correo, clave_hash, rol, nombres, apellidos, tipo_documento, numero_documento, fecha_nacimiento, celular,
  ciudad, activo, creado_en, actualizado_en`
- `correo → id` (y todo lo demás)
- `(tipo_documento, numero_documento) → id` (y todo lo demás), cuando ambos tienen valor.

Los determinantes son llaves candidatas. La edad no se guarda porque depende de `fecha_nacimiento` y de la fecha
actual; se calcula en las consultas con `AGE(fecha_nacimiento)`. Cumple FNBC.

**pqr**

- `id → usuario_id, tipo, asunto, descripcion, estado, respuesta, respondida_por, creada_en, respondida_en`

El nombre y el correo de quien la radicó no se copian en la PQR: la lista del administrador los obtiene con un `JOIN`
a `usuario`. Guardarlos aquí crearía la dependencia transitiva `id → usuario_id → correo`. `estado` no depende de
`respuesta`: una PQR sin respuesta puede estar `RADICADA` o `EN_PROCESO`. Cumple FNBC.

**tasa_cambio**

- `id → moneda_origen, moneda_destino, valor, vigente_desde, fuente, consultada_en`
- `(moneda_origen, moneda_destino, vigente_desde) → id` (y todo lo demás)

Cumple FNBC.

## 4. Verificación tabla por tabla

| Tabla | 1FN | 2FN | 3FN | FNBC |
| --- | --- | --- | --- | --- |
| `marketplace` | Sí | Sí | Sí | Sí |
| `producto` | Sí | Sí | Sí | Sí |
| `oferta` | Sí | Sí | Sí | Sí |
| `historial_precio` | Sí | Sí | Sí | Sí |
| `busqueda` | Sí | Sí | Sí | Sí |
| `consumo_api` | Sí | Sí | Sí | Sí |
| `usuario` | Sí | Sí | Sí | Sí |
| `pqr` | Sí | Sí | Sí | Sí |
| `tasa_cambio` | Sí | Sí | Sí | Sí |

En 1FN se tuvo en cuenta que `nombres` y `apellidos` guardan el nombre completo de la persona como un solo valor,
porque el sistema nunca consulta por separado el primer y el segundo nombre.

## 5. Decisiones de diseño

**El historial de precios va en una tabla aparte.** Si los precios anteriores se guardaran en la oferta (columnas
`precio_1`, `precio_2`... o una lista en una sola celda) habría un grupo repetido, lo que viola la 1FN, y además un
límite fijo de precios. Con `historial_precio` cada precio es una fila, la cantidad no tiene límite y se puede
consultar por rango de fechas con el índice `(oferta_id, registrado_en DESC)`.

**Las tiendas van en su propia tabla.** El nombre y el sitio web de cada tienda se guardan una sola vez en
`marketplace`. Si se repitieran en cada oferta, cambiar el nombre de una tienda obligaría a actualizar miles de filas
(anomalía de actualización) y una tienda sin ofertas no se podría registrar (anomalía de inserción).

**Producto y oferta son entidades distintas.** Un mismo producto se vende en varias tiendas con precios distintos.
Separar `producto` de `oferta` permite comparar las ofertas de un producto sin repetir su nombre y su marca.

**Las tasas de cambio no se copian en las ofertas.** El precio en pesos se calcula en la vista
`vista_ofertas_vigentes` con la tasa más reciente. Si se guardara el precio convertido en cada oferta, habría que
actualizar todas las ofertas en dólares cada día que cambie la TRM.

**El usuario se separa de sus búsquedas y PQR.** Una persona tiene muchas búsquedas y muchas PQR. Al borrar una cuenta,
sus búsquedas quedan con `usuario_id` nulo (`ON DELETE SET NULL`) para no perder las estadísticas, mientras que una PQR
siempre conserva a quien la radicó.

## 6. Redundancias aceptadas

Hay dos datos que se pueden obtener de otra tabla y que se guardan de todas formas, a propósito:

1. **`oferta.precio_actual` y `oferta.moneda`** repiten el último registro de `historial_precio` de esa oferta.
   Se mantienen porque la búsqueda ordena y filtra por precio en cada consulta, y obtener el último precio desde el
   historial exigiría una subconsulta por oferta. El backend actualiza la oferta y agrega una fila al historial en el mismo
   proceso, solo cuando el precio cambia, así que no quedan desalineados. No es una violación de 3FN, porque
   `precio_actual` depende directamente de la llave de `oferta`; es una redundancia entre tablas controlada por la
   aplicación.
2. **`historial_precio.moneda`** se guarda en cada fila aunque normalmente coincide con `oferta.moneda`. Se conserva
   porque una tienda puede cambiar la moneda en que publica una oferta, y cada precio histórico debe seguir
   interpretándose en la moneda en que se registró.

Fuera de estos dos casos, ningún dato se guarda en más de un lugar.
