# Decisiones pendientes

Estas decisiones le corresponden al equipo. El código está preparado para cualquiera de las opciones.

| # | Decisión | Opciones | Estado actual en el código |
| --- | --- | --- | --- |
| 1 | Interfaz gráfica exigida en la Fase 3 (la rúbrica menciona NetBeans) | (A) Frontend web y consultar al docente si lo acepta. (B) Agregar un cliente de escritorio Swing que consuma la misma API REST | Frontend web; la API REST permite agregar un cliente Swing sin cambiar el backend |
| 2 | Proveedor de IA del asistente | Gemini u OpenAI con Spring AI | Gemini por defecto (`gemini-2.5-flash`); OpenAI cambiando tres variables |
| 3 | Alcance del asistente | Solo responder dudas, o también lanzar búsquedas y explicar resultados | Responde dudas y busca precios reales con herramientas de Spring AI |
| 4 | Vía de acceso a Shopify | Catalog API global o Storefront MCP por tienda | Adaptador creado y desactivado |
| 5 | Mercado Libre | Incluirlo si la prueba de acceso OAuth funciona | Adaptador creado y desactivado |
| 6 | Criterio para agrupar ofertas en `producto` | Ver [arquitectura.md](arquitectura.md) | Sin agrupar |
| 7 | Fuente de la tasa de cambio | Se pidió "una API de Google", pero Google no ofrece una API pública de divisas | TRM oficial (datos.gov.co) para el dólar y ExchangeRate-API para otras monedas |
| 8 | Pesos del producto MVP | Precio, reseñas y envío | 60 %, 30 % y 10 %, configurables |

## Por confirmar en la documentación oficial

- AliExpress: método de búsqueda de afiliados, algoritmo de firma, parámetros y límites de uso.
- Shopify: requisitos de acceso y términos de uso de cada vía.
- Mercado Libre: si las búsquedas sin OAuth responden 403 y los límites de uso.
- Restricciones de cada API para guardar precios o imágenes.
- Si cada tienda entrega calificación y número de reseñas, y en qué campo.
- Formato del conjunto 32sa-8pi3 de datos.gov.co (TRM) y condiciones de uso de open.er-api.com.
