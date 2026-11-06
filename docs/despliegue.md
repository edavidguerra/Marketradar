# Despliegue en Render

Marketradar se publica en [Render](https://render.com) con el plan gratuito. El archivo `render.yaml` de la raíz del repositorio es un *Blueprint*: le dice a Render que cree dos cosas.

| Recurso | Qué es |
|---|---|
| `marketradar-db` | Base de datos PostgreSQL 16. Las migraciones de `base-de-datos/migraciones` se aplican solas al arrancar la web. |
| `marketradar` | La web (backend y frontend juntos), construida con `backend/Dockerfile` desde la rama `develop`. |

## Antes de empezar

- Una cuenta en Render. Se puede crear con la cuenta de GitHub del líder (`edavidguerra`).
- El `render.yaml` ya debe estar en la rama `develop`.
- A mano: el correo y la clave del administrador, y la clave de Claude para el chatbot (ver [asistente-claude.md](asistente-claude.md)).

## Pasos

1. En Render, menú **New** > **Blueprint**.
2. Conectar GitHub si lo pide y elegir el repositorio `edavidguerra/Marketradar`.
3. Render lee el `render.yaml` y muestra la base y la web. Escribir los valores que pide:

| Variable | Qué poner |
|---|---|
| `CORS_ORIGENES` | `https://marketradar.onrender.com` (o la dirección que Render le dé a la web) |
| `ADMIN_CORREO` | Correo del administrador |
| `ADMIN_CLAVE` | Clave del administrador: mayúscula, minúscula, número y carácter especial, sin contener el correo |
| `ASISTENTE_API_KEY` | Clave de Claude (empieza por `sk-ant-`). Si se deja vacía, el chatbot no responde, pero el resto funciona |

4. Pulsar **Apply** y esperar. La primera construcción tarda entre 5 y 10 minutos.
5. Cuando la web diga **Live**, abrir la dirección que muestra Render (algo como `https://marketradar.onrender.com`).
6. Si la dirección final es distinta a la que se puso en `CORS_ORIGENES`, corregirla en la web > **Environment** y guardar.

## Activar una tienda

En la web > **Environment**, cambiar por ejemplo `ALIEXPRESS_HABILITADO` a `true` y agregar sus claves (`ALIEXPRESS_APP_KEY`, `ALIEXPRESS_APP_SECRET`, `ALIEXPRESS_TRACKING_ID`). Render reinicia la web con los cambios. Las claves de cada tienda están explicadas en `docs/integraciones/`.

## Actualizar

Cada vez que se fusiona un PR en `develop`, Render vuelve a construir y publicar la web sola. Para publicar desde `main`, cambiar `branch: develop` por `branch: main` en `render.yaml`.

## Límites del plan gratuito

- La web se duerme tras 15 minutos sin visitas. La primera visita después tarda cerca de un minuto en responder. Antes de una presentación, abrirla unos minutos antes.
- La base de datos gratuita vence a los 30 días de creada. Render avisa por correo. Para no perder datos, pasarla a un plan pago o crear otra y volver a desplegar.
- 512 MB de memoria. `JAVA_TOOL_OPTIONS` ya ajusta Java a ese límite.
- No hay disco permanente. Si se activa Mercado Libre, el refresh token que se guarda en `datos/` se pierde en cada reinicio; después de un reinicio hay que poner en `MERCADOLIBRE_REFRESH_TOKEN` el último token vigente (ver `docs/integraciones/mercadolibre.md`).

## Si algo falla

En la web > **Logs** se ve el arranque. Mensajes comunes:

| Mensaje | Causa |
|---|---|
| `Connection refused` o `password authentication failed` | La base no terminó de crearse. Esperar y pulsar **Manual Deploy** > **Deploy latest commit** |
| `La clave no puede contener el correo` o error al crear el administrador | Revisar `ADMIN_CLAVE` |
| `Out of memory` | Volver a desplegar; si se repite, subir la web al plan Starter |
