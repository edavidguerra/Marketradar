# Integración con Google Shopping (SerpApi)

`AdaptadorGoogleShopping` trae precios reales de tiendas colombianas desde Google Shopping, usando la API de
[SerpApi](https://serpapi.com/google-shopping-api). Una sola consulta devuelve productos de muchas tiendas (Éxito,
Falabella, Alkosto, Mercado Libre y otras), así que reemplaza a las tiendas cuya API está cerrada o pide aprobación.

## Por qué SerpApi

- Mercado Libre cerró su búsqueda pública para aplicaciones de terceros y responde 403.
- AliExpress y Shopify piden aprobar la aplicación, lo que tarda días.
- SerpApi da la clave al registrarse. El plan gratis trae 250 búsquedas al mes (máximo 50 por hora) y el plan
  Starter cuesta 25 USD por 1.000 búsquedas ([precios](https://serpapi.com/pricing)).
- Marketradar guarda cada búsqueda en caché, así que repetir la misma búsqueda no gasta otra consulta. Si SerpApi falla o no responde a tiempo, el resultado solo se recuerda 30 segundos, para volver a intentarlo pronto.

## Cómo funciona

1. `GET https://serpapi.com/search.json?engine=google_shopping&q=<término>&gl=co&hl=es&google_domain=google.com.co&api_key=<clave>`.
2. Se toman máximo 40 productos, sin repetir: primero los de `categorized_shopping_results` (con productos populares como "playstation 5" Google agrupa ahí el producto en sí), después `shopping_results` y al final `inline_shopping_results`. Luego se dejan primero los que traen todas las palabras buscadas y se quitan los juegos y accesorios que la persona no pidió (ver [busqueda-flexible.md](../busqueda-flexible.md#3-primero-lo-que-más-se-parece-a-lo-buscado)).
3. Si la API falla, la persona ve "La tienda Google Shopping no respondió" y el resto de tiendas sigue funcionando.
   Los mensajes de error nunca incluyen la URL, porque lleva la clave.

| SerpApi | `OfertaNormalizada` | Notas |
| --- | --- | --- |
| `product_id` | `idExterno` | |
| `title` | `titulo` | |
| `extracted_price` | `precio` | Número sin formato. |
| `price` | `moneda` | `COP`, o `USD` si el texto dice US$. |
| `delivery` | `costoEnvio` | 0 si dice "gratis" o "free"; si no, queda sin valor. |
| `source` | `vendedor` | La tienda real. En pantalla se ve solo "Éxito". |
| `link` (o `product_link`) | `urlDestino` | Si SerpApi trae el enlace directo de la tienda, se usa ese. Si no, se abre el producto en Google Shopping, con las tiendas que lo venden. Solo se aceptan enlaces `https`. |
| `thumbnail` | `urlImagen` | Si llega con `http`, se cambia a `https`. |
| `rating`, `reviews` | `calificacion`, `totalResenas` | |
| `immersive_product_page_token` | `tokenResenas` | Clave para pedir después las reseñas. No se guarda en la base de datos. |

### Reseñas

Cada tarjeta tiene el botón **Ver reseñas** (reemplaza al historial de precio, que salía en blanco porque casi ningún
producto se había consultado dos veces). Al presionarlo:

1. La página llama `GET /api/v1/ofertas/{id}/resenas`.
2. `ServicioResenas` busca la clave que llegó con esa oferta en la última búsqueda (se recuerda en memoria 24 horas) y
   pide `GET https://serpapi.com/search.json?engine=google_immersive_product&page_token=<clave>&gl=co&hl=es&api_key=<clave>`.
3. De `product_results` se toman la calificación (`rating`), el total (`reviews`), cuántas personas dieron cada número de
   estrellas (`ratings`) y hasta 10 opiniones escritas (`user_reviews`: título, texto, autor, sitio y fecha).
4. Si la tienda no tiene opiniones, la página dice "Este producto todavía no tiene reseñas de compradores".

**Costo:** cada producto consultado gasta una búsqueda del plan de SerpApi (250 al mes en el gratuito). Por eso la respuesta
queda en caché 24 horas y solo se pide cuando la persona presiona el botón. Si la aplicación se reinicia, las claves se
pierden y la persona debe volver a buscar el producto. El endpoint `/api/v1/ofertas/{id}/historial` sigue existiendo.

### Errores

| Respuesta | Qué hacer |
| --- | --- |
| 401 | La clave está mal. Revisar `SERPAPI_API_KEY`. |
| 429 | Se acabaron las búsquedas del mes o se pasó el límite de 50 por hora. Esperar o subir de plan. |
| 200 con "Google hasn't returned any results" | No es error: no hay productos con ese nombre. |

### Enlaces seguros

`AdaptadorGoogleShopping.esSeguro` solo acepta enlaces que empiezan por `https://`, con un dominio válido y de máximo
2.048 caracteres. Si una oferta no tiene ningún enlace seguro, la página no muestra botón de compra y explica por qué.
Antes de abrir la tienda, una ventana dice a qué dominio va la persona y le recuerda que Marketradar nunca pide datos
bancarios.

## Cómo sacar la clave

1. Entrar a <https://serpapi.com/users/sign_up> y crear la cuenta (con correo o Google). Confirmar el correo.
2. Abrir <https://serpapi.com/manage-api-key> y copiar la **Private API Key**.
3. En Render, servicio **marketradar** > **Environment**:
   - `SERPAPI_API_KEY` = la clave.
   - `SERPAPI_HABILITADO` = `true`.
4. **Save Changes** y esperar unos 5 minutos.

La clave va solo en Render o en el `.env`, nunca en el código ni en GitHub. El consumo se ve en
<https://serpapi.com/dashboard>.

## Variables

| Variable | Valor |
|---|---|
| `SERPAPI_HABILITADO` | `true` para usarla |
| `SERPAPI_API_KEY` | Clave privada de SerpApi |
| `BUSQUEDA_TIMEOUT_SEGUNDOS` | Espera máxima por tienda; 8 por defecto porque SerpApi puede tardar |
