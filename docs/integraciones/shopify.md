# Integración con Shopify

Marketradar busca productos de tiendas Shopify con el **Global Catalog** de Shopify. El adaptador está en `backend/src/main/java/co/edu/unicesar/marketradar/adapter/shopify/AdaptadorShopify.java` y viene apagado hasta que se configure su token.

## Qué se necesita

| Variable | Qué es |
|---|---|
| `SHOPIFY_HABILITADO` | `true` para que el adaptador participe en las búsquedas |
| `SHOPIFY_TOKEN` | Token de acceso al catálogo, que se envía como `Authorization: Bearer` |

Opcionales, con valor por defecto en el código:

| Propiedad | Valor por defecto |
|---|---|
| `marketradar.marketplaces.shopify.url-api` | `https://catalog.shopify.com/api/ucp/mcp` |
| `marketradar.marketplaces.shopify.perfil-agente` | Perfil de agente de ejemplo de Shopify (`valid-with-capabilities.json`) |

El token va **solo** en el `.env` o en las variables de entorno del servidor.

## Cómo funciona la llamada

- `POST` a la dirección del catálogo con JSON-RPC 2.0.
- Método `tools/call` con la herramienta `search_catalog`, el término de búsqueda y un máximo de 20 productos.
- Cada consulta envía la dirección del perfil de agente en `meta.ucp-agent.profile`.

## Cómo se convierte la respuesta

Los productos llegan en `result.structuredContent.products`.

| Campo de Shopify | Campo de `OfertaNormalizada` |
|---|---|
| `variants[0].id` (o `id` del producto) | `idExterno` |
| `title` | `titulo` |
| `price_range.min.amount` (viene en centavos) | `precio`, dividido entre 100 |
| `price_range.min.currency` | `moneda` |
| `variants[0].checkout_url` (o `url`) | `urlDestino` |
| Primera imagen de `media` | `urlImagen` |
| `variants[0].condition` (`new`, `secondhand`, `refurbished`) | `condicion`: nuevo, usado, reacondicionado |
| `variants[0].seller.name` | `vendedor` |
| `rating.value`, `rating.scale_max`, `rating.count` | `calificacion` (en escala de 5) y `totalResenas` |

`structuredContent` viene en camelCase y el resto de Marketradar lee el JSON en snake_case; por eso el campo lleva `@JsonProperty("structuredContent")`. Sin esa anotación la búsqueda devolvía cero productos aunque la API respondiera bien.

## Errores

| Respuesta | Qué hace Marketradar |
|---|---|
| 401 o 403 | Error del marketplace: revisar el token y el perfil de agente |
| 429 | Error del marketplace: Shopify limitó las consultas |
| Error JSON-RPC (`error.message`) | Error del marketplace con el mensaje de Shopify |
| Sin productos | Lista vacía, sin error |

En todos los casos la búsqueda sigue con las demás tiendas.

## Restricción importante: guardar resultados

Según los términos de Shopify, los resultados del catálogo no se pueden guardar en caché ni reutilizar fuera de la consulta. Marketradar hoy guarda cada oferta en la base de datos, lleva su historial de precio y usa una caché de 15 minutos para todas las tiendas. Antes de activar Shopify en producción el equipo debe decidir una de estas opciones:

1. No guardar ni cachear las ofertas de Shopify (solo mostrarlas).
2. Pedir a Shopify permiso para guardarlas.
3. Dejar Shopify apagado en producción y usarlo solo en la demostración.

La decisión está registrada en [decisiones-pendientes.md](../decisiones-pendientes.md).

## Pendiente de confirmar con un token real

- Cómo se solicita el token del Global Catalog y qué límite de consultas tiene.
- Si el perfil de agente de ejemplo sirve o hay que publicar uno propio.
