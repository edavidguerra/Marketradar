# Decisiones pendientes

Estas decisiones le corresponden al equipo. El código está preparado para cualquiera de las opciones.

| # | Decisión | Opciones | Estado actual en el código |
| --- | --- | --- | --- |
| 1 | Interfaz gráfica exigida en la Fase 3 (la rúbrica menciona NetBeans) | (A) Frontend web y consultar al docente si lo acepta. (B) Agregar un cliente de escritorio Swing que consuma la misma API REST | Frontend web; la API REST permite agregar un cliente Swing sin cambiar el backend |
| 2 | Proveedor de IA del asistente | Gemini u OpenAI con Spring AI, o Claude | **Decidido:** Claude (`claude-opus-5-5`) con los créditos de Anthropic del equipo (ver [asistente-claude.md](asistente-claude.md)) |
| 3 | Alcance del asistente | Solo responder dudas, o también lanzar búsquedas y explicar resultados | Responde dudas y busca precios reales con herramientas que Claude puede llamar |
| 4 | Vía de acceso a Shopify | Catalog API global o Storefront MCP por tienda | **Decidido:** Global Catalog (herramienta `search_catalog` por MCP). Falta decidir cómo cumplir que Shopify no permite guardar sus resultados (ver [integraciones/shopify.md](integraciones/shopify.md)) |
| 5 | Mercado Libre | Incluirlo si la prueba de acceso OAuth funciona | **Decidido:** incluido, con OAuth y renovación del token |
| 6 | Criterio para agrupar ofertas en `producto` | Ver [arquitectura.md](arquitectura.md) | Sin agrupar |
| 7 | Fuente de la tasa de cambio | Se pidió "una API de Google", pero Google no ofrece una API pública de divisas | TRM oficial (datos.gov.co) para el dólar y ExchangeRate-API para otras monedas |
| 8 | Pesos del producto MVP | Precio, reseñas y envío | 60 %, 30 % y 10 %, configurables |

## Por confirmar en la documentación oficial

- AliExpress: límite diario de llamadas de la aplicación (el método, la firma y los parámetros están en [integraciones/aliexpress.md](integraciones/aliexpress.md)).
- Shopify: cómo se obtiene el token del Global Catalog y su límite de consultas.
- Mercado Libre: si las búsquedas sin OAuth responden 403 y los límites de uso.
- Restricciones de cada API para guardar precios o imágenes.
- Si cada tienda entrega calificación y número de reseñas, y en qué campo.
- Formato del conjunto 32sa-8pi3 de datos.gov.co (TRM) y condiciones de uso de open.er-api.com.
