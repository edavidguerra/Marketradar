# Integración con AliExpress

Marketradar busca productos de AliExpress con la API de afiliados de **AliExpress Open Platform**. El adaptador está en `backend/src/main/java/co/edu/unicesar/marketradar/adapter/aliexpress/AdaptadorAliExpress.java` y viene apagado hasta que se configuren sus claves.

## Qué se necesita

| Variable | De dónde sale |
|---|---|
| `ALIEXPRESS_APP_KEY` | App Key de la aplicación creada en [openservice.aliexpress.com](https://openservice.aliexpress.com) |
| `ALIEXPRESS_APP_SECRET` | App Secret de la misma aplicación |
| `ALIEXPRESS_TRACKING_ID` | Tracking ID del programa de afiliados ([portals.aliexpress.com](https://portals.aliexpress.com)) |
| `ALIEXPRESS_HABILITADO` | `true` para que el adaptador participe en las búsquedas |

Las claves van **solo** en el archivo `.env` (que no se sube a GitHub) o en las variables de entorno del servidor. Nunca se escriben en el código ni en `application.yml`.

## Cómo obtener las claves

1. Crear una cuenta en AliExpress Portals (programa de afiliados) y generar un **Tracking ID** en la sección de seguimiento de anuncios.
2. Entrar a AliExpress Open Platform con la misma cuenta, registrarse como desarrollador y crear una aplicación de la categoría **Affiliates API**.
3. Cuando AliExpress apruebe la aplicación, copiar el **App Key** y el **App Secret** desde la consola de la aplicación.
4. Ponerlos en el `.env`:

```
ALIEXPRESS_HABILITADO=true
ALIEXPRESS_APP_KEY=...
ALIEXPRESS_APP_SECRET=...
ALIEXPRESS_TRACKING_ID=...
```

Sin Tracking ID el adaptador queda apagado, porque la API puede responder vacío sin dar error.

## Cómo funciona la llamada

- Dirección: `POST https://api-sg.aliexpress.com/sync` con los parámetros en formulario (`application/x-www-form-urlencoded`).
- Método: `aliexpress.affiliate.product.query`.
- Parámetros que envía Marketradar:

| Parámetro | Valor |
|---|---|
| `app_key` | App Key |
| `method` | `aliexpress.affiliate.product.query` |
| `sign_method` | `sha256` |
| `timestamp` | Hora actual en milisegundos desde 1970 (UTC) |
| `keywords` | Lo que escribió la persona |
| `tracking_id` | Tracking ID |
| `target_currency` | `USD` (la conversión a pesos la hace Marketradar con la TRM) |
| `target_language` | `ES` |
| `ship_to_country` | `CO` |
| `page_no` / `page_size` | `1` / `50` |
| `sign` | Firma (ver abajo) |

### Firma

1. Se toman todos los parámetros menos `sign` y se ordenan por nombre.
2. Se une cada nombre con su valor, sin separadores: `app_key12345keywordsolla...`.
3. Se calcula HMAC-SHA256 de ese texto con el App Secret como clave.
4. El resultado se escribe en hexadecimal y en mayúsculas.

La prueba `AdaptadorAliExpressTest` compara la firma con un valor calculado aparte en Python, para comprobar el algoritmo sin llamar a la API real.

## Cómo se convierte la respuesta

Los productos llegan en `aliexpress_affiliate_product_query_response.resp_result.result.products.product`.

| Campo de AliExpress | Campo de `OfertaNormalizada` |
|---|---|
| `product_id` | `idExterno` |
| `product_title` | `titulo` |
| `target_sale_price` | `precio` |
| `target_sale_price_currency` | `moneda` (USD si no viene) |
| `promotion_link` (o `product_detail_url` si no hay enlace de afiliado) | `urlDestino` |
| `product_main_image_url` | `urlImagen` |
| `evaluate_rate` (porcentaje de opiniones positivas) | `calificacion`: 100 % equivale a 5 estrellas |

AliExpress no informa el costo de envío ni el número de reseñas en esta búsqueda, así que esos campos quedan vacíos y el producto MVP usa el valor neutro para ellos. La condición siempre es "nuevo".

Los productos a los que les falta el identificador, el título, el precio o el enlace se descartan.

## Errores

| Respuesta | Qué hace Marketradar |
|---|---|
| `resp_code` 200 | Muestra los productos |
| `resp_code` 405 | La búsqueda no encontró nada: lista vacía, sin error |
| `code` distinto de `"0"` (por ejemplo `IncompleteSignature`, `InvalidAppKey`, `ApiCallLimit`) | Error del marketplace; la búsqueda sigue con las demás tiendas |
| Error HTTP (4xx o 5xx) | Error del marketplace; la búsqueda sigue con las demás tiendas |

El adaptador hereda de `AdaptadorMarketplaceBase` el límite de 5 llamadas por segundo.

## Pendiente de confirmar con claves reales

El equipo no tiene todavía una aplicación aprobada, así que la llamada se probó con respuestas simuladas. Al conseguir las claves hay que confirmar:

- Que la cuenta acepte `sign_method=sha256`. Si AliExpress responde `IncompleteSignature`, revisar la hora del equipo (la marca de tiempo no puede estar muy desfasada) y el App Secret.
- Que `target_language=ES` devuelva títulos en español para Colombia.
- El límite diario de llamadas que asigne AliExpress a la aplicación.
