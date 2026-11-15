# Comparar productos y seguir precios

## Comparar productos

En los resultados de una búsqueda cada oferta tiene el botón **Comparar**. Se eligen de 2 a 4. Abajo aparece una barra
con las miniaturas, el contador ("2 de 4"), un botón para quitar cada una y **Ver comparación**. La selección se
mantiene al pulsar "Ver más" o al filtrar por precio, y se borra al buscar otra cosa.

`GET /api/v1/ofertas/comparacion?ids=1,2,3` (con sesión) devuelve una tabla: los productos son columnas y las
características son filas.

- **Oferta**: tienda, precio, moneda, envío, condición, disponibilidad (solo si la tienda la informa), valoración
  (opiniones del producto, no de la tienda), desde dónde vende y fecha de consulta.
- **Características**: se leen de lo que informó la tienda y, si falta, del título. La celda dice "según el título"
  cuando sale de ahí. Las unidades se normalizan: "256GB", "256 gb" y "256 gigas" quedan como "256 GB", y 1 TB son
  1024 GB.
- Lo que no se sabe dice **No disponible**. No se completa nada con suposiciones.
- Cada fila dice si los productos difieren. "Mostrar solo las diferencias" deja solo esas filas.
- Si los productos son de tipos distintos (un celular y un forro), de otro modelo o versión (iPhone 15 y 15 Pro), de
  otra capacidad o en otra moneda, la respuesta lo advierte y no marca ninguno como "más barato".
- La respuesta trae `equivalencia`: `EQUIVALENTES`, `DIFERENTES` o `NO_CONFIRMADA`. Para decir que son el mismo
  producto deben coincidir los datos esenciales de su tipo: condición, marca y modelo siempre; además versión y
  almacenamiento en celulares y consolas, conectividad en tabletas y relojes, RAM y almacenamiento en portátiles,
  pantalla en televisores y capacidad en electrodomésticos. Esas filas vienen con `esencial: true`. Un dato esencial
  distinto los hace `DIFERENTES`; uno que falta deja `NO_CONFIRMADA` y `faltantes` dice cuál. En los dos casos se
  muestran lado a lado con el aviso, sin ganador. Como Google Shopping casi nunca informa la condición, lo normal en
  esas ofertas es `NO_CONFIRMADA`: es a propósito, porque lo que no se sabe no se da por igual.
- "Más barato con envío" solo aparece si todas las tiendas informan el envío. Un envío desconocido nunca cuenta como
  gratis.

## Seguir un producto

1. La persona pulsa **Seguir producto** y elige "Cuando baje de precio" o "Cuando llegue a un precio que yo elija".
2. Antes debe verificar su correo. Si no lo ha hecho, la API responde `409 CORREO_SIN_VERIFICAR` y la página ofrece
   mandar el enlace. El enlace vence en 24 horas, se usa una vez y se mandan como máximo 3 por hora.
3. Al activar el seguimiento se manda un correo de confirmación. Si se vuelve a seguir el mismo producto, se guardan
   las preferencias nuevas sin mandar otra confirmación y sin reiniciar el precio de referencia.
4. En **Seguimientos** se ve el estado, la última revisión y los últimos correos, y se puede pausar, reanudar o
   dejar de seguir. Cada correo trae un enlace para dejar de seguir sin iniciar sesión.

Cada seguimiento queda ligado a la persona con sesión y a una oferta concreta: tienda, vendedor, condición, moneda y
variante (modelo, versión, capacidad). Se reutiliza la tabla `seguimiento_oferta` (migración V13).

### Cuándo se avisa

La revisión corre en el servidor (`TareaSeguimientos`, cada `SEGUIMIENTOS_REVISAR_CADA_MINUTOS`). Cada producto se
revisa cada `SEGUIMIENTOS_FRECUENCIA_MINUTOS` (por defecto 12 horas, mínimo 30 minutos). Busca otra vez con lo que
escribió la persona, como máximo `SEGUIMIENTOS_CONSULTAS_POR_RONDA` consultas por ronda, y lo que no alcanza queda
para 30 minutos después.

**No** se avisa, y el precio de referencia no cambia, cuando:

- la tienda falló (se reintenta con espera creciente);
- la oferta no apareció;
- el precio llegó con una alerta ("desde", por cuotas, distinto al de la tienda);
- cambió el vendedor, la condición, la moneda o la variante;
- el precio se convirtió de dólares: se compara en dólares, porque un cambio de la TRM no es un descuento.

Una bajada cuenta si es de al menos `SEGUIMIENTOS_BAJA_MINIMA_PORCENTAJE` (1 %). El precio objetivo se avisa una vez y
se vuelve a avisar solo si el precio sube por encima y baja otra vez.

### Sin duplicados y con reintentos

- Cada aviso tiene una clave única: persona, oferta, desde qué referencia y precio nuevo. Si la ronda se repite, la
  base de datos no deja crear el mismo aviso (`aviso_seguimiento.clave_evento UNIQUE`).
- Los seguimientos y los avisos se apartan con `FOR UPDATE SKIP LOCKED`, así dos rondas no toman lo mismo. La
  comprobación se guarda solo si nadie cambió el seguimiento mientras tanto (columna `version`).
- Un correo queda `ENVIADO` cuando el servicio de correo lo **aceptó**, no cuando llegó: ningún proveedor confirma la
  entrega en el momento. Se guarda el proveedor y el id del mensaje (`proveedor_correo`, `id_mensaje_proveedor`) para
  buscarlo en el panel del proveedor. Un error pasajero (tiempo agotado, 429, 5xx) se reintenta a los 10 y a los 20
  minutos y al tercer fallo queda `FALLIDO`; un error definitivo (clave mala, remitente no autorizado) queda `FALLIDO`
  de una vez. Un aviso que no salió en 48 horas se descarta, para no mandar un precio viejo.
- Al pausar o cancelar, los avisos pendientes se descartan. Justo antes de mandar un aviso se vuelve a leer el
  seguimiento: si se pausó o canceló mientras tanto, el aviso se descarta y no sale.

### Enlace de verificación

`token_correo.estado_envio` distingue `PENDIENTE` (se está mandando), `ACEPTADO` (el proveedor lo aceptó, con hora,
proveedor e id) y `FALLIDO` (con el error). Las filas anteriores a V14 quedan `SIN_REGISTRO`. Reglas:

- El enlace vence a las 24 horas y sirve una sola vez.
- Como mucho 3 enlaces aceptados y 6 solicitudes por hora por persona, y 1 minuto entre un enlace aceptado y el
  siguiente (un doble clic no manda dos correos). Un índice único parcial impide dos envíos en curso a la vez.
- Se reintenta una vez si el error es pasajero. Un envío que quedó `PENDIENTE` más de 2 minutos (el servidor se
  reinició a mitad) se marca `FALLIDO` y no cuenta para el límite.
- `GET /api/v1/usuario/correo` trae `ultimo_envio` y la página lo muestra: "El servicio de correo aceptó el último
  enlace. Puede tardar unos minutos en llegar; revise también la carpeta de spam." Nunca dice "entregado".

## Lo que hay que configurar

### Correo: por qué no sirve SMTP en Render gratis

Desde 2025 los servicios gratis de Render **bloquean la salida a los puertos SMTP 25, 465 y 587**
([anuncio de Render](https://render.com/changelog/free-web-services-will-no-longer-allow-outbound-traffic-to-smtp-ports)).
Con `SMTP_HOST` configurado la conexión se queda esperando y el correo nunca sale. Al arrancar en Render con SMTP, el
registro lo advierte. La solución sin pagar plan es mandar el correo por la **API HTTPS** de un proveedor (puerto 443),
con la misma abstracción `TransporteCorreo`:

| Proveedor | Plan gratis (a nov. 2026, verifíquelo) | Variables |
|---|---|---|
| Brevo | 300 correos al día | `CORREO_PROVEEDOR=brevo`, `CORREO_API_KEY=xkeysib-…` |
| Resend | 100 al día, exige dominio propio verificado para mandar a cualquiera | `CORREO_PROVEEDOR=resend`, `CORREO_API_KEY=re_…` |

Con `CORREO_PROVEEDOR=auto` (por defecto) se usa la API si hay `CORREO_API_KEY` (`re_` es Resend, lo demás Brevo) y
si no, SMTP. SMTP sigue funcionando en local o en un plan que lo permita.

Pasos con Brevo: crear la cuenta, en **Senders, domains & dedicated IPs** agregar y confirmar el correo remitente
(el mismo de `CORREO_REMITENTE`), crear la clave en **SMTP & API → API keys** y ponerla en Render como
`CORREO_API_KEY`. Las variables `SMTP_*` pueden quedarse: con clave de API no se usan.

| Variable | Para qué | Sin ella |
|---|---|---|
| `CORREO_PROVEEDOR` | `auto`, `brevo`, `resend` o `smtp` | `auto` |
| `CORREO_API_KEY` | Clave de la API del proveedor | Se intenta SMTP |
| `CORREO_API_URL` | Solo para pruebas (servidor simulado) | La del proveedor |
| `SMTP_HOST`, `SMTP_PUERTO`, `SMTP_USUARIO`, `SMTP_CLAVE`, `SMTP_STARTTLS`/`SMTP_SSL` | Servidor SMTP (no sirve en Render gratis) | Si tampoco hay API, **no se manda ningún correo**: la página lo dice y la verificación responde 503 |
| `CORREO_REMITENTE`, `CORREO_NOMBRE_REMITENTE` | Remitente; debe estar verificado en el proveedor | Igual que arriba |
| `URL_PUBLICA` | Enlaces de los correos. En Render se toma de `RENDER_EXTERNAL_URL` | Enlaces a `localhost` |
| `TAREAS_TOKEN` | Cron externo (ver abajo) | El endpoint responde 404 |

Las credenciales solo viven en el servidor. Los registros muestran el proveedor, el id del mensaje y el destinatario
oculto (`a***@dominio`), nunca claves ni cuerpos. `PropiedadesCorreo.toString()` no muestra la clave.

### Revisión periódica en Render

La revisión usa `@Scheduled`, que solo corre mientras el servidor está despierto. El plan gratis de Render duerme el
servicio tras 15 minutos sin visitas, así que **hace falta un cron externo** (por ejemplo cron-job.org) que cada 15
minutos haga:

```
POST https://marketradar-c3zr.onrender.com/api/v1/tareas/seguimientos
X-Tarea-Token: <TAREAS_TOKEN>
```

- Sin `TAREAS_TOKEN` en el servidor responde 404; con la cabecera mala, 403. La clave se compara en tiempo constante.
- Nunca corren dos rondas a la vez: un candado en memoria y otro en PostgreSQL (`pg_try_advisory_lock`), que sirve
  aunque haya dos copias del servidor. Si ya había una, responde `ejecutada: false` y queda registrada como `OMITIDA`.
- Cada ronda tiene un tope de 20 segundos de consultas a tiendas; lo que no alcanza se pospone a la siguiente. Así
  la respuesta llega antes de que cron-job.org corte (30 s en el plan gratis). Si el servidor estaba dormido, el
  primer intento puede tardar más por el arranque; configure en cron-job.org el tiempo máximo más alto que permita y
  que reintente.
- Cada ronda queda en la tabla `ejecucion_tarea` (se borran las de más de 30 días).

Para comprobar con datos que el cron llega con la frecuencia esperada:

```
GET https://marketradar-c3zr.onrender.com/api/v1/tareas/seguimientos
X-Tarea-Token: <TAREAS_TOKEN>
```

Devuelve las últimas rondas (origen `cron` o `interna`, estado, horas y conteos, sin datos de personas),
`mayor_espera_minutos` y `frecuencia_cumplida`: `true` si ninguna espera entre rondas pasó de la frecuencia más 5
minutos, `false` si alguna sí, y `null` si todavía hay menos de 3 rondas. Mientras no se mire esa respuesta después de
un día de funcionamiento, no se puede afirmar que corre cada 15 minutos.
