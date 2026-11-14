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
- "Más barato con envío" solo aparece si todas las tiendas informan el envío. Un envío desconocido nunca cuenta como
  gratis.

## Seguir un producto

1. La persona pulsa **Seguir producto** y elige "Cuando baje de precio" o "Cuando llegue a un precio que yo elija".
2. Antes debe verificar su correo. Si no lo ha hecho, la API responde `409 CORREO_SIN_VERIFICAR` y la página ofrece
   mandar el enlace. El enlace vence en 24 horas, se usa una vez y se mandan como máximo 3 por hora.
3. Al activar el seguimiento se manda un correo de confirmación. Si se vuelve a seguir el mismo producto, se guardan
   las preferencias nuevas sin mandar otra confirmación y sin reiniciar el precio de referencia.
4. En **Mis seguimientos** se ve el estado, la última revisión y los últimos correos, y se puede pausar, reanudar o
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
- Un correo queda `ENVIADO` solo si el servidor SMTP lo aceptó. Si falla, se reintenta a los 10 y a los 20 minutos y
  al tercer fallo queda `FALLIDO`. Un aviso que no salió en 48 horas se descarta, para no
  mandar un precio viejo.
- Al pausar o cancelar, los avisos pendientes se descartan.

## Lo que hay que configurar

| Variable | Para qué | Sin ella |
|---|---|---|
| `SMTP_HOST`, `SMTP_PUERTO`, `SMTP_USUARIO`, `SMTP_CLAVE`, `SMTP_STARTTLS`/`SMTP_SSL` | Servidor de correo | **No se manda ningún correo.** La página lo dice, la verificación responde 503 y los avisos quedan pendientes |
| `CORREO_REMITENTE`, `CORREO_NOMBRE_REMITENTE` | Remitente (debe estar autorizado en el SMTP) | Igual que arriba |
| `URL_PUBLICA` | Enlaces de los correos. En Render se toma de `RENDER_EXTERNAL_URL` | Enlaces a `localhost` |
| `TAREAS_TOKEN` | Cron externo (ver abajo) | El endpoint responde 404 |

Las credenciales solo viven en el servidor. Los errores del SMTP se registran sin usuario ni clave, y
`PropiedadesCorreo.toString()` no muestra la clave.

### Revisión periódica en Render

La revisión usa `@Scheduled`, que solo corre mientras el servidor está despierto. El plan gratis de Render duerme el
servicio tras 15 minutos sin visitas, así que **hace falta un cron externo** (por ejemplo cron-job.org o un Cron Job de
Render) que cada 15 a 30 minutos haga:

```
POST https://marketradar-c3zr.onrender.com/api/v1/tareas/seguimientos
X-Tarea-Token: <TAREAS_TOKEN>
```

Responde con un resumen (`revisados`, `consultas`, `avisos_nuevos`, `correos_enviados`, `envio_configurado`). Si ya
había una ronda en curso, responde `ejecutada: false`.
