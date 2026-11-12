# Decisiones del equipo

Qué se decidió, por qué, y lo que todavía falta decidir. Cuando una decisión cambie, se actualiza esta tabla en el
mismo PR que cambia el código.

## Decididas

| # | Tema | Decisión | Por qué |
| --- | --- | --- | --- |
| 1 | Proveedor de IA del asistente | Claude con Spring AI (modelo en `ASISTENTE_MODELO`) | El equipo tiene créditos de Anthropic. Ver [asistente-claude.md](asistente-claude.md). |
| 2 | Alcance del asistente | Responde dudas y busca precios reales con herramientas, y responde con botones para abrir la tienda | Que ayude a comprar, no que dé direcciones largas que confunden. |
| 3 | Fuente de precios en producción | Google Shopping por SerpApi | Reúne muchas tiendas colombianas con una sola clave. Ver [integraciones/google-shopping.md](integraciones/google-shopping.md). |
| 4 | Otras tiendas | AliExpress, Shopify y Mercado Libre quedan programadas y se activan con sus claves | Sus claves piden aprobar una aplicación o autorizar una cuenta. |
| 5 | Tienda de demostración | Retirada (migración V9) | Los productos reales ya llegan por Google Shopping. |
| 6 | Vía de acceso a Shopify | Global Catalog (herramienta `search_catalog` por MCP) | Busca en todas las tiendas Shopify a la vez. |
| 7 | Mercado Libre | Incluido, con OAuth y renovación del token guardada en la base de datos | Render no tiene disco permanente. |
| 8 | Tasa de cambio | TRM oficial (datos.gov.co) para el dólar y ExchangeRate-API para otras monedas | Google no ofrece una API pública de divisas. |
| 9 | Pesos del producto MVP | 60 % precio, 30 % reseñas y 10 % envío, configurables | El precio total es lo que más pesa para la persona. |
| 10 | Búsqueda con errores | Se busca directamente lo corregido, como Google, con un botón para buscar lo escrito | Muchas personas mayores escriben con errores. Ver [busqueda-flexible.md](busqueda-flexible.md). |
| 11 | Velocidad | Caché de 60 minutos (`BUSQUEDA_CACHE_MINUTOS`), ofertas guardadas en una sola consulta y archivos comprimidos | Cada búsqueda repetida responde al instante y gasta menos consultas de SerpApi. Ver [arquitectura.md](arquitectura.md#velocidad). |
| 12 | Animaciones | Cortas (menos de 5 s), decorativas y apagadas con "reducir movimiento" | Llamativas sin marear ni distraer (WCAG 2.2.2 y 2.3.3). |
| 13 | IA en la búsqueda | Gemini (plan gratis de Google AI Studio) entiende la búsqueda, elige los productos que sí son lo buscado y escribe un resumen; el chatbot sigue con Claude | Google Shopping trae forros y accesorios mezclados con el producto, y los filtros por palabras no bastan. Sin clave o si falla, se usan los filtros propios. Ver [integraciones/gemini.md](integraciones/gemini.md). |
| 14 | Envío, ubicación y traducción | El envío se lee del texto de Google; la ubicación de la tienda se deduce de su nombre y dirección; las reseñas en otro idioma se traducen con Gemini solo cuando la persona lo pide | Google no dice desde dónde vende cada tienda, y comprar del exterior tarda y cobra impuestos. Traducir solo al pulsar ahorra cuota. Ver [integraciones/google-shopping.md](integraciones/google-shopping.md) y [integraciones/gemini.md](integraciones/gemini.md). |

## Pendientes

| # | Tema | Opciones | Estado en el código |
| --- | --- | --- | --- |
| 1 | Interfaz gráfica de la Fase 3 (la rúbrica menciona NetBeans) | (A) Frontend web, si el docente lo acepta. (B) Agregar un cliente de escritorio Swing que use la misma API REST | Frontend web; la API REST permite agregar un cliente Swing sin cambiar el backend. |
| 2 | Guardar resultados de Shopify | Sus términos no permiten guardar ni cachear el catálogo | Ver [integraciones/shopify.md](integraciones/shopify.md). |
| 3 | Agrupar ofertas en `producto` | Ver [arquitectura.md](arquitectura.md#agrupación-de-ofertas-en-producto) | Sin agrupar (`oferta.producto_id` vacío). |

## Por confirmar en la documentación oficial

- AliExpress: límite diario de llamadas de la aplicación (el método, la firma y los parámetros están en [integraciones/aliexpress.md](integraciones/aliexpress.md)).
- Shopify: cómo se obtiene el token del Global Catalog y su límite de consultas.
- Mercado Libre: si las búsquedas sin OAuth responden 403 y los límites de uso.
- Formato del conjunto 32sa-8pi3 de datos.gov.co (TRM) y condiciones de uso de open.er-api.com.
| 15 | Avisos del inicio en carrusel | Los avisos cambian solos cada 5 segundos y se pasan con flechas, deslizando el dedo o con ← y →; hay botón "Pausar" y el cambio se detiene con el ratón o el foco encima | Muestra todas las ofertas sin que la persona tenga que pulsar. Para cumplir WCAG 2.2.2 (pausar lo que se mueve solo), empieza en pausa si el sistema pide reducir el movimiento y el lector de pantalla solo anuncia los cambios que pide la persona |
| 17 | Lo que pidió la persona, precio total y puntaje | Se recomienda solo entre las ofertas que cumplen modelo, versión, capacidad y condición, y tienen el precio verificado; las demás se muestran aparte con lo que les falta. El envío tiene cuatro estados y uno desconocido nunca vale cero. El puntaje conserva los pesos 60/30/10; lo que no es nuevo confirmado (incluido "no informado") baja 10 % y lo que vende del exterior baja otro 10 % | Antes un reacondicionado o una versión Pro podían salir como recomendación, y un envío sin informar contaba como medio punto. Las rebajas del 10 % no cambian el orden cuando la diferencia de precio es grande (más de 10 %), pero sí desempatan ofertas parecidas a favor de la que confirma ser nueva o vende desde Colombia, donde no hay impuestos de importación por sorpresa. Los pesos no se cambiaron porque no hubo datos de uso que lo justificaran. Ver [recomendacion.md](recomendacion.md) |
| 16 | Cajas vacías y filtro de precio | Las cajas vacías se quitan siempre y lo que elige la IA se revisa con reglas propias; el filtro de precio se aplica sobre los resultados en caché | Con "iphone 17 pro" la IA llegó a mostrar solo la caja. Filtrar en el servidor, sobre la caché, mantiene bien la recomendación y la paginación sin gastar búsquedas de SerpApi. Ver [busqueda-flexible.md](busqueda-flexible.md) |
