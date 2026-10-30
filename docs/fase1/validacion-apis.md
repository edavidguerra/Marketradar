# Fase 1: Validación de las APIs de las tiendas

Este documento hace parte del estado del arte de la Fase 1. Revisa, para cada tienda que el proyecto quiere comparar,
si existe una vía oficial para consultar productos y precios, cómo se obtiene el acceso, qué datos entrega, qué límites
tiene y qué restricciones de uso hay que respetar.

Marketradar solo usa APIs oficiales. No se hace *web scraping* ni se usan servicios de terceros que extraigan datos sin
autorización.

> **Cómo leer este documento.** Lo marcado como **por confirmar** no aparece claramente en la documentación oficial
> consultada y debe verificarse al crear la cuenta de desarrollador. La sección *Prueba de acceso* de cada tienda se
> completa con el resultado real de la consulta de ejemplo, ejecutada con credenciales propias.

## 1. Resumen

| Tienda | Vía oficial | Autenticación | Estado para el MVP |
| --- | --- | --- | --- |
| AliExpress | API de afiliados de AliExpress Open Platform (`aliexpress.affiliate.product.query`) | App Key y App Secret con firma de cada petición, más Tracking ID de afiliado | Incluida. Requiere aprobación de la aplicación |
| Shopify | Global Catalog (UCP Catalog sobre MCP) en `https://catalog.shopify.com/api/ucp/mcp` | Perfil de agente; según la documentación no requiere API key | Incluida. Ver restricción de caché en la sección 3.6 |
| Mercado Libre Colombia | API REST `GET /sites/MCO/search` | OAuth 2.0 (token de acceso) | Incluida solo si la prueba de acceso funciona |
| Temu y Shein | No ofrecen API pública de consulta de precios; sus APIs son para vendedores | No aplica | Fuera del alcance |

## 2. AliExpress

### 2.1 Acceso

1. Crear una cuenta en AliExpress Open Platform y aceptar el acuerdo de la plataforma.
2. Registrar los datos de desarrollador y crear una aplicación. El acceso es restringido y requiere aprobación; las guías
   publicadas indican que tarda de uno a dos días hábiles (Zuplo, s. f.).
3. Al aprobarse, la plataforma entrega el **App Key** y el **App Secret**.
4. Inscribirse en el programa de afiliados de AliExpress para obtener el **Tracking ID**. Sin Tracking ID la API puede
   devolver resultados vacíos sin indicar error.

### 2.2 Autenticación y firma

Cada petición lleva los parámetros comunes `method`, `app_key`, `timestamp`, `sign_method` y `sign`. La firma se
calcula ordenando los parámetros por nombre, concatenando nombre y valor y aplicando el algoritmo indicado en
`sign_method` con el App Secret (Van de Vliet, s. f.).

- **Por confirmar:** la puerta de enlace vigente. Las guías antiguas usan `http://gw.api.taobao.com/router/rest` con
  firma MD5; la plataforma actual de AliExpress publica una puerta de enlace propia con firma HMAC-SHA256.
- **Por confirmar:** formato exacto del `timestamp` en la puerta de enlace vigente.

### 2.3 Búsqueda de productos

Método: `aliexpress.affiliate.product.query`, descrito como búsqueda de productos por palabra clave, precio, días de
entrega y país de disponibilidad.

| Parámetro | Uso en Marketradar | Estado |
| --- | --- | --- |
| `keywords` | Término que escribe la persona | Confirmado |
| `tracking_id` | Tracking ID de afiliado | Confirmado |
| `target_currency` | `USD`; el backend convierte a COP con la TRM | Por confirmar si acepta `COP` |
| `target_language` | `ES` | Por confirmar |
| `ship_to_country` | `CO`, para traer solo productos con envío a Colombia | Por confirmar |
| `page_no`, `page_size` | Paginación de resultados | Por confirmar el máximo de `page_size` |

### 2.4 Datos útiles de la respuesta

| Campo de AliExpress | Campo en `OfertaNormalizada` | Estado |
| --- | --- | --- |
| `product_id` | `idExterno` | Por confirmar |
| `product_title` | `titulo` | Confirmado |
| `target_sale_price` | `precio` | Confirmado |
| `target_sale_price_currency` | `moneda` | Por confirmar |
| `product_main_image_url` | `urlImagen` | Confirmado |
| `promotion_link` | `urlDestino` (enlace de afiliado) | Confirmado |
| Calificación y número de reseñas | `calificacion`, `totalResenas` | Por confirmar qué campos los entregan |
| Costo de envío | `costoEnvio` | Por confirmar; puede no venir en la búsqueda |

### 2.5 Límites y términos

- **Por confirmar:** límite de llamadas por segundo y por día de la aplicación. El adaptador ya limita a 5 llamadas por
  segundo con Resilience4j.
- Los enlaces de `promotion_link` generan comisión de afiliado. La interfaz debe indicarlo de forma visible.
- **Por confirmar:** si los términos permiten guardar precios e imágenes en la base de datos.

### 2.6 Prueba de acceso

Consulta de ejemplo, cuando la aplicación esté aprobada (los valores entre `< >` se reemplazan):

```text
POST <puerta de enlace vigente>
Content-Type: application/x-www-form-urlencoded;charset=utf-8

method=aliexpress.affiliate.product.query
app_key=<APP_KEY>
timestamp=<fecha y hora>
sign_method=<md5 o sha256>
keywords=olla a presion
tracking_id=<TRACKING_ID>
target_currency=USD
target_language=ES
ship_to_country=CO
page_no=1
page_size=5
sign=<firma calculada>
```

| Fecha | Quién la ejecutó | Código HTTP | Resultados | Observaciones |
| --- | --- | --- | --- | --- |
| | | | | |

## 3. Shopify

### 3.1 Opciones de acceso

Shopify ofrece dos catálogos para agentes que implementan la capacidad *Catalog* del protocolo UCP. Ambos exponen las
herramientas `search_catalog`, `lookup_catalog` y `get_product` (Shopify, s. f.-a):

| Catálogo | Alcance | Punto de acceso |
| --- | --- | --- |
| Global Catalog | Productos de todos los comercios de Shopify; pensado para descubrir y comparar | `https://catalog.shopify.com/api/ucp/mcp` |
| Storefront Catalog | Una sola tienda | `https://{dominio-de-la-tienda}/api/ucp/mcp` |

Para un comparador conviene el **Global Catalog**, porque busca en todos los comercios con una sola consulta. Esta
elección cierra la decisión pendiente número 4 del proyecto y debe aprobarla el equipo.

### 3.2 Autenticación

La documentación indica que ambos catálogos se identifican con un **perfil de agente** y no requieren API key. El
tutorial de búsqueda usa además un token *Bearer* con el permiso `read_global_api_catalog_search`, obtenido según su
tutorial de autenticación, y los catálogos guardados se crean en el Dev Dashboard (Shopify, s. f.-b).

- **Por confirmar:** si la búsqueda funciona sin token o si hay que registrar una aplicación en el Dev Dashboard.

### 3.3 Búsqueda de productos

La consulta usa JSON-RPC 2.0 con `method: "tools/call"` y la herramienta `search_catalog`. Admite filtros por condición,
rango de precio y país de envío (`ships_to`).

### 3.4 Datos útiles de la respuesta

| Campo de Shopify | Campo en `OfertaNormalizada` | Estado |
| --- | --- | --- |
| `products[].id` o `variants[].id` | `idExterno` | Confirmado |
| `title` | `titulo` | Confirmado |
| `price.amount` (en unidades menores, por ejemplo centavos) | `precio` (dividido entre 100) | Confirmado |
| `price.currency` | `moneda` | Confirmado |
| `variants[].checkout_url` | `urlDestino` | Confirmado; requiere las capacidades del perfil |
| `variants[].condition` | `condicion` | Confirmado |
| `variants[].seller.name` | `vendedor` | Confirmado |
| Imagen | `urlImagen` | Por confirmar; no aparece en los ejemplos |
| Calificación, reseñas y costo de envío | `calificacion`, `totalResenas`, `costoEnvio` | No aparecen en los ejemplos |

### 3.5 Límites

Las consultas tienen límite de uso. El acceso sin llave no admite aumentos; para pedir más hay que escribir a Shopify
desde una organización de Partner (Shopify, s. f.-a).

- **Por confirmar:** el número de consultas permitidas.

### 3.6 Restricciones que afectan al diseño

- **No se permite guardar en caché los resultados** y las imágenes deben mostrarse en tiempo real, sin reutilizarlas
  fuera del producto (Shopify, s. f.-a). Esto choca con la caché de 15 minutos y con el historial de precios del
  proyecto: para Shopify hay que desactivar la caché y no guardar sus precios, o confirmar con Shopify qué está
  permitido. **Decisión del equipo.**
- Algunos campos los infiere una IA y pueden venir incompletos o con errores.
- Los puntos de acceso pueden cambiar, así que la URL debe quedar en la configuración y no en el código.

### 3.7 Prueba de acceso

```json
POST https://catalog.shopify.com/api/ucp/mcp
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "id": 1,
  "params": {
    "name": "search_catalog",
    "arguments": {
      "meta": { "ucp-agent": { "profile": "<URL del perfil de agente>" } },
      "catalog": {
        "query": "olla a presion",
        "filters": { "ships_to": { "country": "CO" } }
      }
    }
  }
}
```

| Fecha | Quién la ejecutó | Código HTTP | Resultados | Observaciones |
| --- | --- | --- | --- | --- |
| | | | | |

## 4. Mercado Libre Colombia

### 4.1 Acceso

1. Crear una aplicación en el portal de desarrolladores de Mercado Libre con una cuenta de Colombia.
2. Obtener el **Client ID** y el **Client Secret**.
3. Generar el token de acceso con OAuth 2.0. El token se renueva con el *refresh token*.

### 4.2 Búsqueda de productos

Recurso: `GET https://api.mercadolibre.com/sites/MCO/search?q=<término>`. `MCO` es el sitio de Colombia. Todos los
ejemplos de la documentación envían la cabecera `Authorization: Bearer $ACCESS_TOKEN` (Mercado Libre, s. f.).

Varios desarrolladores reportan respuestas **403 Forbidden** en el recurso de búsqueda pública
(`/sites/MLB/search` en Brasil), aun cuando antes funcionaba (Reclame Aqui, s. f.). Por eso Mercado Libre queda sujeto a
la prueba de acceso: si la búsqueda responde 403 también con token, el adaptador se deja desactivado y la interfaz lo
indica con un aviso claro.

### 4.3 Datos útiles de la respuesta

| Campo de Mercado Libre | Campo en `OfertaNormalizada` | Estado |
| --- | --- | --- |
| `id` | `idExterno` | Confirmado |
| `title` | `titulo` | Confirmado |
| `price` | `precio` | Confirmado |
| `currency_id` (`COP` en Colombia) | `moneda` | Confirmado |
| `permalink` | `urlDestino` | Confirmado |
| `thumbnail` | `urlImagen` | Confirmado |
| `condition` (`new`, `used`) | `condicion` | Confirmado |
| `shipping.free_shipping` | `costoEnvio` = 0 cuando es verdadero | Confirmado; el costo exacto no viene en la búsqueda |
| `seller` | `vendedor` | No siempre viene |
| Calificación y reseñas | `calificacion`, `totalResenas` | No vienen en la búsqueda; por confirmar otro recurso |

La respuesta trae un objeto `paging` con `total`, `offset` y `limit` para paginar.

### 4.4 Límites

- **Por confirmar:** límites de llamadas de la aplicación y máximo de `offset` en la búsqueda.

### 4.5 Prueba de acceso

```text
GET https://api.mercadolibre.com/sites/MCO/search?q=olla%20a%20presion&limit=5
Authorization: Bearer <ACCESS_TOKEN>
```

Repetirla también sin la cabecera `Authorization` para saber si la búsqueda pública sigue disponible.

| Fecha | Quién la ejecutó | Con token | Código HTTP | Resultados | Observaciones |
| --- | --- | --- | --- | --- | --- |
| | | Sí | | | |
| | | No | | | |

## 5. Tiendas descartadas

- **Temu** y **Shein** no publican una API de consulta de precios para terceros. Sus APIs están dirigidas a vendedores
  para gestionar su propio catálogo y pedidos, así que no sirven para un comparador. Extraer sus precios de la página
  web violaría sus términos de uso.

## 6. Fuentes de tasas de cambio

Aunque no son tiendas, el sistema depende de ellas para mostrar todo en pesos colombianos:

| Fuente | Uso | Acceso |
| --- | --- | --- |
| TRM oficial (datos abiertos del Gobierno, conjunto `32sa-8pi3` de datos.gov.co) | Dólar a peso colombiano | Pública, sin llave. Por confirmar el formato del conjunto |
| ExchangeRate-API (`open.er-api.com`) | Otras monedas a dólar | Pública, sin llave. Por confirmar condiciones de uso |

## 7. Conclusiones

- AliExpress es la fuente más completa para el MVP, pero depende de que aprueben la aplicación y del Tracking ID.
- Shopify tiene la vía más moderna y documentada para comparar entre comercios, pero su prohibición de guardar
  resultados en caché obliga a tratarlo distinto a las demás tiendas.
- Mercado Libre es la tienda más conocida en Colombia, pero su búsqueda pública presenta bloqueos reportados; se incluye
  solo si la prueba de acceso funciona.
- El diseño con un adaptador por tienda permite activar o desactivar cada una sin cambiar el resto del sistema.

## 8. Referencias

- Mercado Libre. (s. f.). *Items & Searches*. Developers Mercado Livre.
  <https://developers.mercadolivre.com.br/en_us/items-and-searches>
- Reclame Aqui. (s. f.). *Erro 403 Forbidden no endpoint de busca pública de anúncios da API do Mercado Livre*.
  <https://www.reclameaqui.com.br/mercado-livre/erro-403-forbidden-no-endpoint-de-busca-publica-de-anuncios-da-api-do-mercado-livre_kl_Q1h2_x_sPnREJ/>
- Shopify. (s. f.-a). *About Catalogs*. Shopify Dev. <https://shopify.dev/docs/agents/catalog>
- Shopify. (s. f.-b). *Discover products with Shopify Catalog*. Shopify Dev.
  <https://shopify.dev/docs/agents/get-started/search-catalog>
- Van de Vliet, J. (s. f.). *Guide: How to use the AliExpress Affiliate API with Node.js*.
  <https://vandevliet.me/how-to-make-aliexpress-affiliate-api-call/>
- Zuplo. (s. f.). *A Developer's Guide to the AliExpress API*. DEV Community.
  <https://dev.to/zuplo/a-developers-guide-to-the-aliexpress-api-4f45>
