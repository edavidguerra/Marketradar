# Diccionario de datos

Base de datos `marketradar` en PostgreSQL 16, esquema `public`. Describe las tablas creadas por las migraciones V1 a
V3 y las vistas de V4 de `base-de-datos/migraciones`.

Convenciones de las tablas:

- **Nulo:** "No" significa que la columna tiene `NOT NULL`.
- **Llave:** PK llave primaria, FK llave foránea, UQ parte de una restricción única.
- Todos los `id` son enteros autoincrementales (`SERIAL`), generados por la base.
- Las fechas con hora usan `TIMESTAMPTZ` (con zona horaria) y su valor por defecto `now()` es el momento de la inserción.
- El dinero usa `NUMERIC`, nunca `FLOAT`, para no perder centavos por redondeo.

## marketplace

Tienda en línea que consulta Marketradar. Se crea con los datos iniciales de V2.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `id` | SMALLSERIAL | No | PK | | Identificador de la tienda. |
| `codigo` | VARCHAR(30) | No | UQ | `UNIQUE` | Código interno que usa el backend para elegir el adaptador (`aliexpress`, `shopify`, `mercadolibre`). |
| `nombre` | VARCHAR(80) | No | | | Nombre que se muestra a la persona. |
| `sitio_web` | VARCHAR(255) | No | | | Dirección principal de la tienda. |
| `activo` | BOOLEAN | No | | `DEFAULT TRUE` | Si la tienda se muestra como disponible. V2 las deja en `FALSE` hasta validar su API. |

## producto

Producto normalizado que agrupa las ofertas del mismo artículo en distintas tiendas.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGSERIAL | No | PK | | Identificador del producto. |
| `nombre_normalizado` | VARCHAR(300) | No | | | Nombre unificado del producto, igual para todas sus ofertas, que permite compararlas entre tiendas. |
| `marca` | VARCHAR(100) | Sí | | | Marca del producto, si se conoce. |
| `creado_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Fecha y hora de registro. |

## oferta

Publicación concreta de un producto en una tienda. Hay una fila por publicación y se actualiza en cada consulta.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGSERIAL | No | PK | | Identificador de la oferta. |
| `producto_id` | BIGINT | Sí | FK | `REFERENCES producto(id)` | Producto normalizado al que pertenece. Nulo mientras no se haya asociado. |
| `marketplace_id` | SMALLINT | No | FK, UQ | `REFERENCES marketplace(id)` | Tienda donde está publicada. |
| `id_externo` | VARCHAR(120) | No | UQ | `UNIQUE (marketplace_id, id_externo)` | Identificador de la publicación en la tienda. No se repite dentro de la misma tienda. |
| `titulo` | VARCHAR(400) | No | | Índice de texto completo en español | Título tal como lo publica la tienda. |
| `url_destino` | TEXT | No | | | Enlace que abre exactamente esa publicación en la tienda. |
| `url_imagen` | TEXT | Sí | | | Enlace a la imagen del producto. |
| `precio_actual` | NUMERIC(14,2) | No | | `CHECK (precio_actual >= 0)`, índice | Último precio consultado, en la moneda de la columna `moneda`. |
| `moneda` | CHAR(3) | No | | `DEFAULT 'COP'` | Código ISO 4217 de la moneda del precio (`COP`, `USD`...). |
| `costo_envio` | NUMERIC(14,2) | Sí | | | Costo de envío a Colombia. Nulo si la tienda no lo informa; 0 si es gratis. |
| `condicion` | VARCHAR(20) | Sí | | | Estado del producto según la tienda: nuevo, usado o reacondicionado. |
| `vendedor` | VARCHAR(150) | Sí | | | Nombre del vendedor dentro de la tienda. |
| `calificacion` | NUMERIC(2,1) | Sí | | `CHECK (calificacion BETWEEN 0 AND 5)` | Promedio de estrellas de las reseñas. Agregada en V3. |
| `total_resenas` | INTEGER | Sí | | `CHECK (total_resenas >= 0)` | Cantidad de reseñas. Agregada en V3. |
| `consultado_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Momento de la última consulta a la tienda; se muestra junto al precio. |

## historial_precio

Cada precio distinto que tuvo una oferta. Se inserta una fila cuando el precio consultado cambia.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGSERIAL | No | PK | | Identificador del registro. |
| `oferta_id` | BIGINT | No | FK | `REFERENCES oferta(id) ON DELETE CASCADE`, índice con `registrado_en` | Oferta a la que pertenece el precio. Si se borra la oferta, se borra su historial. |
| `precio` | NUMERIC(14,2) | No | | `CHECK (precio >= 0)` | Precio registrado. |
| `moneda` | CHAR(3) | No | | | Moneda del precio. |
| `registrado_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Momento en que se registró el precio. |

## busqueda

Cada búsqueda hecha en la aplicación. Sirve para el historial de cada persona y para los reportes de lo más buscado.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGSERIAL | No | PK | | Identificador de la búsqueda. |
| `termino` | VARCHAR(200) | No | | Índice | Texto que escribió la persona. |
| `total_resultados` | INTEGER | No | | | Cantidad de ofertas encontradas. |
| `duracion_ms` | INTEGER | No | | | Tiempo total de la búsqueda en milisegundos. |
| `creada_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Momento de la búsqueda. |
| `usuario_id` | BIGINT | Sí | FK | `REFERENCES usuario(id) ON DELETE SET NULL`, índice con `creada_en` | Persona que buscó. Agregada en V3. Queda en nulo si se borra la cuenta, para conservar las estadísticas. |

## consumo_api

Cada llamada a la API de una tienda. Permite medir disponibilidad y vigilar los límites de uso.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGSERIAL | No | PK | | Identificador del registro. |
| `marketplace_id` | SMALLINT | No | FK | `REFERENCES marketplace(id)` | Tienda consultada. |
| `exitoso` | BOOLEAN | No | | | Si la llamada respondió correctamente. |
| `codigo_http` | INTEGER | Sí | | | Código HTTP de la respuesta. Nulo si no hubo respuesta (por ejemplo, tiempo agotado). |
| `duracion_ms` | INTEGER | No | | | Tiempo de respuesta en milisegundos. |
| `registrado_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Momento de la llamada. |

## usuario

Persona registrada. Creada en V3.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGSERIAL | No | PK | | Identificador de la persona. |
| `correo` | VARCHAR(150) | No | UQ | `UNIQUE` | Correo con el que ingresa. No se puede cambiar. |
| `clave_hash` | VARCHAR(100) | No | | | Hash BCrypt de la contraseña. La contraseña nunca se guarda en texto. |
| `rol` | VARCHAR(20) | No | | `CHECK (rol IN ('USUARIO', 'ADMINISTRADOR'))` | Interfaz a la que entra. |
| `nombres` | VARCHAR(80) | No | | | Nombres. |
| `apellidos` | VARCHAR(80) | No | | | Apellidos. |
| `tipo_documento` | VARCHAR(3) | Sí | UQ | `CHECK (tipo_documento IN ('CC', 'CE', 'PA'))` | Cédula de ciudadanía, cédula de extranjería o pasaporte. |
| `numero_documento` | VARCHAR(15) | Sí | UQ | `UNIQUE (tipo_documento, numero_documento)` | Número del documento. No se repite para el mismo tipo. |
| `fecha_nacimiento` | DATE | Sí | | | Fecha de nacimiento; el backend exige mayoría de edad. |
| `celular` | VARCHAR(10) | Sí | | | Celular colombiano de 10 dígitos. |
| `ciudad` | VARCHAR(80) | Sí | | | Ciudad de residencia. |
| `activo` | BOOLEAN | No | | `DEFAULT TRUE` | Si la cuenta puede ingresar. |
| `creado_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Fecha de registro. |
| `actualizado_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Última modificación del perfil. |

## pqr

Peticiones, quejas, reclamos y sugerencias. El `id` es el número de radicado que ve la persona. Creada en V3.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGSERIAL | No | PK | | Número de radicado. |
| `usuario_id` | BIGINT | No | FK | `REFERENCES usuario(id)`, índice con `creada_en` | Persona que la radicó. |
| `tipo` | VARCHAR(20) | No | | `CHECK (tipo IN ('PETICION', 'QUEJA', 'RECLAMO', 'SUGERENCIA'))` | Tipo de solicitud. |
| `asunto` | VARCHAR(120) | No | | | Asunto corto (el backend exige de 5 a 120 caracteres). |
| `descripcion` | VARCHAR(2000) | No | | | Detalle (el backend exige de 20 a 2000 caracteres). |
| `estado` | VARCHAR(20) | No | | `DEFAULT 'RADICADA'`, `CHECK (estado IN ('RADICADA', 'EN_PROCESO', 'RESPONDIDA'))`, índice con `creada_en` | Pasa a `EN_PROCESO` cuando el administrador la abre y a `RESPONDIDA` cuando la contesta. |
| `respuesta` | VARCHAR(2000) | Sí | | | Respuesta del administrador. |
| `respondida_por` | BIGINT | Sí | FK | `REFERENCES usuario(id)` | Administrador que respondió. |
| `creada_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Fecha de radicación. |
| `respondida_en` | TIMESTAMPTZ | Sí | | | Fecha de la respuesta. |

## tasa_cambio

Valor de una moneda en pesos colombianos. Se guarda una fila por moneda y fecha. Creada en V3.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGSERIAL | No | PK | | Identificador del registro. |
| `moneda_origen` | CHAR(3) | No | UQ | | Moneda que se convierte (por ejemplo `USD`). |
| `moneda_destino` | CHAR(3) | No | UQ | `DEFAULT 'COP'` | Moneda a la que se convierte. |
| `valor` | NUMERIC(14,4) | No | | `CHECK (valor > 0)` | Pesos por cada unidad de la moneda de origen. |
| `vigente_desde` | DATE | No | UQ | `UNIQUE (moneda_origen, moneda_destino, vigente_desde)` | Día desde el que rige la tasa. |
| `fuente` | VARCHAR(80) | No | | | De dónde salió el valor (TRM de datos abiertos del Gobierno, ExchangeRate-API...). |
| `consultada_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Momento en que se consultó la fuente. |

## Índices

| Índice | Tabla | Columnas | Para qué sirve |
| --- | --- | --- | --- |
| `idx_oferta_titulo` | oferta | `to_tsvector('spanish', titulo)` (GIN) | Búsqueda de texto completo en los títulos. |
| `idx_oferta_precio` | oferta | `precio_actual` | Ordenar y filtrar por precio. |
| `idx_historial_oferta_fecha` | historial_precio | `oferta_id, registrado_en DESC` | Traer el historial de una oferta del más reciente al más antiguo. |
| `idx_busqueda_termino` | busqueda | `termino` | Reportes de términos más buscados. |
| `idx_busqueda_usuario` | busqueda | `usuario_id, creada_en DESC` | Historial de búsquedas de cada persona. |
| `idx_pqr_usuario` | pqr | `usuario_id, creada_en DESC` | PQR de cada persona. |
| `idx_pqr_estado` | pqr | `estado, creada_en` | Lista del administrador filtrada por estado. |

Además, PostgreSQL crea un índice por cada llave primaria y cada restricción `UNIQUE`.

## Vistas (V4)

Las vistas no guardan datos: se calculan cada vez a partir de las tablas.

| Vista | Columnas principales | Descripción |
| --- | --- | --- |
| `vista_ofertas_vigentes` | `oferta_id`, `producto_id`, `tienda`, `titulo`, `precio_total`, `precio_total_cop`, `calificacion`, `url_destino` | Ofertas de tiendas activas con el envío sumado y el total en pesos según la tasa más reciente. |
| `vista_bajadas_precio` | `oferta_id`, `titulo`, `tienda`, `precio_anterior`, `precio_nuevo`, `ahorro`, `porcentaje_bajada` | Ofertas cuyo último precio es menor que el anterior. |
| `vista_resumen_pqr` | `tipo`, `estado`, `cantidad`, `horas_promedio_respuesta` | Conteo de PQR por tipo y estado. |
| `vista_disponibilidad_api` | `marketplace`, `consultas`, `exitosas`, `porcentaje_exito`, `duracion_promedio_ms` | Disponibilidad de la API de cada tienda. |
