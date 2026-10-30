# Integración con Mercado Libre Colombia

El adaptador `AdaptadorMercadoLibre` consulta la API oficial de Mercado Libre para el sitio de Colombia (`MCO`).
Este documento explica cómo funciona y cómo conseguir las credenciales para activarlo.

## Cómo funciona

1. **Token de acceso.** Mercado Libre pide un token OAuth 2.0 en todas las consultas. El backend lo obtiene con el
   *refresh token* en `POST https://api.mercadolibre.com/oauth/token` (`grant_type=refresh_token`) y lo reutiliza
   hasta cinco minutos antes de que venza (la documentación indica una vigencia de 6 horas).
2. **El refresh token cambia en cada renovación.** Mercado Libre solo deja usar cada refresh token una vez y devuelve
   uno nuevo. El backend guarda el nuevo en el archivo `MERCADOLIBRE_ARCHIVO_TOKEN`
   (por defecto `datos/mercadolibre-refresh-token.txt`) y al arrancar lo lee de ahí antes que del `.env`.
   En Docker ese archivo queda en el volumen `datos-app`, así que se conserva al reiniciar el contenedor.
3. **Búsqueda.** `GET /sites/MCO/search?q=<término>&limit=50` con la cabecera `Authorization: Bearer <token>`.
   Si responde 401, el backend pide otro token y repite la búsqueda una vez.
4. **Reseñas.** La búsqueda no trae calificaciones. Para los primeros resultados (5 por defecto, propiedad
   `resenas-por-busqueda`) se consulta en paralelo `GET /reviews/item/<id>`. Si tarda más de 2 segundos o falla, la
   oferta se muestra sin reseñas.

### Campos que se usan

| Mercado Libre | `OfertaNormalizada` | Notas |
| --- | --- | --- |
| `id` | `idExterno` | Por ejemplo `MCO123456789`. |
| `title` | `titulo` | |
| `price` | `precio` | |
| `currency_id` | `moneda` | `COP` en Colombia. |
| `permalink` | `urlDestino` | Abre la publicación exacta. |
| `thumbnail` | `urlImagen` | Se cambia `http://` por `https://`. |
| `condition` | `condicion` | `new` → nuevo, `used` → usado, `refurbished` → reacondicionado. |
| `shipping.free_shipping` | `costoEnvio` | 0 si es gratis; si no, queda sin valor porque la búsqueda no trae el costo. |
| `seller.nickname` | `vendedor` | No siempre viene. |
| `rating_average` (reseñas) | `calificacion` | Redondeado a un decimal. |
| Suma de `rating_levels` (reseñas) | `totalResenas` | Cantidad de calificaciones. |

Los resultados sin `id`, `title`, `price`, `currency_id` o `permalink` se descartan.

### Errores

| Respuesta | Qué ve la persona | Qué hacer |
| --- | --- | --- |
| 400 al renovar el token (`invalid_grant`) | Mercado Libre aparece como no disponible | El refresh token venció (dura unos 6 meses) o ya se usó. Generar uno nuevo con los pasos de abajo. |
| 401 en la búsqueda | Nada: el backend renueva el token y repite | |
| 403 en la búsqueda | Mercado Libre aparece como no disponible | Revisar que la aplicación tenga permiso de lectura. Varios desarrolladores reportan 403 en esta búsqueda; si persiste, se deja `MERCADOLIBRE_HABILITADO=false`. |
| 429 | Mercado Libre aparece como no disponible | Se superó el límite de consultas. El adaptador ya limita a 5 búsquedas por segundo. |

## Cómo obtener las credenciales

Estos pasos se hacen una sola vez. Los valores entre `< >` se reemplazan.

### 1. Crear la aplicación

1. Entrar con una cuenta de Mercado Libre Colombia a <https://developers.mercadolibre.com.co> y abrir
   **Mis aplicaciones → Crear aplicación**.
2. Llenar nombre y descripción. En **URI de redirect** poner una dirección `https` fija, por ejemplo la del repositorio:
   `https://github.com/edavidguerra/Marketradar`. Mercado Libre exige que sea exactamente la misma en todos los pasos.
3. En permisos, activar al menos **lectura** y **offline access** (este último es el que entrega el refresh token).
4. Guardar y copiar el **App ID** (Client ID) y la **Clave secreta** (Client Secret).

### 2. Autorizar la aplicación

Abrir en el navegador, con la sesión de Mercado Libre iniciada:

```text
https://auth.mercadolibre.com.co/authorization?response_type=code&client_id=<APP_ID>&redirect_uri=https://github.com/edavidguerra/Marketradar
```

Al aceptar, el navegador vuelve a la URI de redirect. Lo que se necesita es el valor de `code` que aparece en la barra de direcciones (`...?code=TG-xxxxxxxx`). El código vence en pocos minutos,
así que el paso 3 se hace enseguida.

### 3. Cambiar el código por el refresh token

En la terminal (en Windows funciona en cmd o PowerShell con `curl.exe`):

```bash
curl -X POST "https://api.mercadolibre.com/oauth/token" -H "accept: application/json" -H "content-type: application/x-www-form-urlencoded" -d "grant_type=authorization_code" -d "client_id=<APP_ID>" -d "client_secret=<CLAVE_SECRETA>" -d "code=<CODIGO>" -d "redirect_uri=https://github.com/edavidguerra/Marketradar"
```

La respuesta trae `access_token` y `refresh_token`. Se copia el `refresh_token`.

### 4. Configurar el `.env`

```text
MERCADOLIBRE_HABILITADO=true
MERCADOLIBRE_CLIENT_ID=<APP_ID>
MERCADOLIBRE_CLIENT_SECRET=<CLAVE_SECRETA>
MERCADOLIBRE_REFRESH_TOKEN=<REFRESH_TOKEN>
```

El `.env` nunca se sube al repositorio. Si se borra el archivo `datos/mercadolibre-refresh-token.txt` (o el volumen
de Docker), el backend vuelve a usar el del `.env`, que ya está gastado, y hay que repetir los pasos 2 a 4.

### 5. Probar

Arrancar el backend, iniciar sesión y buscar un producto, por ejemplo "olla arrocera". En `GET /api/v1/marketplaces`
Mercado Libre debe aparecer como disponible. Anotar el resultado en la tabla "Prueba de acceso" de
`docs/fase1/validacion-apis.md`.

## Referencias

- Mercado Libre. (s. f.). *Autenticación y autorización*. Mercado Libre Developers.
  <https://developers.mercadolibre.cl/en_us/authentication-and-authorization>
- Mercado Libre. (s. f.). *Ítems y búsquedas*. Mercado Libre Developers.
  <https://developers.mercadolibre.com.mx/items-y-busquedas>
- Mercado Libre. (s. f.). *Opiniones sobre productos*. Mercado Libre Developers.
  <https://developers.mercadolibre.com.mx/en_us/products-reviews>
