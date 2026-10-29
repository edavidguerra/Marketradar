# Decisiones pendientes

Estas decisiones le corresponden al equipo. El código está preparado para cualquiera de las opciones.

| # | Decisión | Opciones | Estado actual en el código |
| --- | --- | --- | --- |
| 1 | Interfaz gráfica exigida en la Fase 3 (la rúbrica menciona NetBeans) | (A) Frontend web y consultar al docente si lo acepta. (B) Agregar un cliente de escritorio Swing que consuma la misma API REST | Frontend web; la API REST permite agregar un cliente Swing sin cambiar el backend |
| 2 | Proveedor de IA del asistente | OpenAI, Gemini, Claude u otro | Interfaz `ProveedorIA` con `ProveedorIANoConfigurado`, que responde un mensaje amable |
| 3 | Alcance del asistente | Solo responder dudas, o también lanzar búsquedas y explicar resultados | Solo responde dudas |
| 4 | Vía de acceso a Shopify | Catalog API global o Storefront MCP por tienda | Adaptador creado y desactivado |
| 5 | Mercado Libre | Incluirlo si la prueba de acceso OAuth funciona | Adaptador creado y desactivado |
| 6 | Criterio para agrupar ofertas en `producto` | Ver [arquitectura.md](arquitectura.md) | Sin agrupar |

## Por confirmar en la documentación oficial

- AliExpress: método de búsqueda de afiliados, algoritmo de firma, parámetros y límites de uso.
- Shopify: requisitos de acceso y términos de uso de cada vía.
- Mercado Libre: si las búsquedas sin OAuth responden 403 y los límites de uso.
- Restricciones de cada API para guardar precios o imágenes.
