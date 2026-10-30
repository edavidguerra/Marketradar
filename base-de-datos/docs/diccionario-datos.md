# Diccionario de datos

Base de datos `marketradar` en PostgreSQL 16, esquema `public`. Describe las tablas creadas por las migraciones V1 a
V3 y V5, y las vistas de V4 de `base-de-datos/migraciones`.

Convenciones de las tablas:

- **Nulo:** "No" significa que la columna tiene `NOT NULL`.
- **Llave:** PK llave primaria, FK llave foránea, UQ parte de una restricción única.
- Todos los `id` son enteros autoincrementales (`SERIAL`), generados por la base.
- Las fechas con hora usan `TIMESTAMPTZ` (con zona horaria) y su valor por defecto `now()` es el momento de la inserción.
- El dinero usa `NUMERIC`, nunca `FLOAT`, para no perder centavos por redondeo.
- Los textos tienen longitud máxima: `VARCHAR(n)`, o `TEXT` con una regla `CHECK` de longitud en las direcciones web.
- Desde V5 cada restricción tiene nombre propio (`pk_`, `fk_`, `uq_`, `ck_`); la lista completa está al final.

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
| `producto_id` | BIGINT | Sí | FK | `fk_oferta_producto`: `REFERENCES producto(id) ON DELETE SET NULL` | Producto normalizado al que pertenece. Nulo mientras no se haya asociado. |
| `marketplace_id` | SMALLINT | No | FK, UQ | `fk_oferta_marketplace`: `REFERENCES marketplace(id) ON DELETE RESTRICT` | Tienda donde está publicada. |
| `id_externo` | VARCHAR(120) | No | UQ | `UNIQUE (marketplace_id, id_externo)` | Identificador de la publicación en la tienda. No se repite dentro de la misma tienda. |
| `titulo` | VARCHAR(400) | No | | Índice de texto completo en español | Título tal como lo publica la tienda. |
| `url_destino` | TEXT | No | | `ck_oferta_url_destino_web`, `ck_oferta_url_destino_longitud` (máx. 2048) | Enlace que abre exactamente esa publicación en la tienda. |
| `url_imagen` | TEXT | Sí | | `ck_oferta_url_imagen_longitud` (máx. 2048) | Enlace a la imagen del producto. |
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
| `oferta_id` | BIGINT | No | FK | `fk_historial_oferta`: `REFERENCES oferta(id) ON DELETE CASCADE`, índice con `registrado_en` | Oferta a la que pertenece el precio. Si se borra la oferta, se borra su historial. |
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
| `usuario_id` | BIGINT | Sí | FK | `fk_busqueda_usuario`: `REFERENCES usuario(id) ON DELETE SET NULL`, índice con `creada_en` | Persona que buscó. Agregada en V3. Queda en nulo si se borra la cuenta, para conservar las estadísticas. |

## consumo_api

Cada llamada a la API de una tienda. Permite medir disponibilidad y vigilar los límites de uso.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGSERIAL | No | PK | | Identificador del registro. |
| `marketplace_id` | SMALLINT | No | FK | `fk_consumo_marketplace`: `REFERENCES marketplace(id) ON DELETE RESTRICT` | Tienda consultada. |
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
| `usuario_id` | BIGINT | No | FK | `fk_pqr_usuario`: `REFERENCES usuario(id) ON DELETE RESTRICT`, índice con `creada_en` | Persona que la radicó. |
| `tipo` | VARCHAR(20) | No | | `CHECK (tipo IN ('PETICION', 'QUEJA', 'RECLAMO', 'SUGERENCIA'))` | Tipo de solicitud. |
| `asunto` | VARCHAR(120) | No | | | Asunto corto (el backend exige de 5 a 120 caracteres). |
| `descripcion` | VARCHAR(2000) | No | | | Detalle (el backend exige de 20 a 2000 caracteres). |
| `estado` | VARCHAR(20) | No | | `DEFAULT 'RADICADA'`, `CHECK (estado IN ('RADICADA', 'EN_PROCESO', 'RESPONDIDA'))`, índice con `creada_en` | Pasa a `EN_PROCESO` cuando el administrador la abre y a `RESPONDIDA` cuando la contesta. |
| `respuesta` | VARCHAR(2000) | Sí | | | Respuesta del administrador. |
| `respondida_por` | BIGINT | Sí | FK | `fk_pqr_respondida_por`: `REFERENCES usuario(id) ON DELETE RESTRICT` | Administrador que respondió. |
| `creada_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Fecha de radicación. |
| `respondida_en` | TIMESTAMPTZ | Sí | | | Fecha de la respuesta. |

## seguimiento_oferta

Ofertas que sigue cada persona para enterarse cuando bajan de precio. Es la tabla intermedia que resuelve la relación
muchos a muchos entre `usuario` y `oferta`: una persona sigue muchas ofertas y una oferta la siguen muchas personas.
Creada en V5.

| Columna | Tipo | Nulo | Llave | Restricciones y valor por defecto | Descripción |
| --- | --- | --- | --- | --- | --- |
| `usuario_id` | BIGINT | No | PK, FK | `fk_seguimiento_usuario`: `REFERENCES usuario(id) ON DELETE CASCADE ON UPDATE CASCADE` | Persona que sigue la oferta. |
| `oferta_id` | BIGINT | No | PK, FK | `fk_seguimiento_oferta`: `REFERENCES oferta(id) ON DELETE CASCADE ON UPDATE CASCADE`, índice | Oferta seguida. |
| `precio_objetivo` | NUMERIC(14,2) | Sí | | `ck_seguimiento_precio_objetivo_positivo` (mayor que 0) | Precio por debajo del cual la persona quiere que se le avise. Nulo si quiere saber de cualquier bajada. |
| `creado_en` | TIMESTAMPTZ | No | | `DEFAULT now()` | Momento en que empezó a seguirla. |

La llave primaria `pk_seguimiento_oferta` es la pareja `(usuario_id, oferta_id)`, así que una persona no puede seguir
dos veces la misma oferta.

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

## Restricciones con nombre (V5)

Lista generada desde PostgreSQL después de aplicar V1 a V5. Cuando un dato rompe una de estas reglas, el
mensaje del servidor trae su nombre, por ejemplo `violates check constraint "ck_pqr_asunto_longitud"`.

| Tabla | Restricción | Tipo | Definición |
| --- | --- | --- | --- |
| `busqueda` | `ck_busqueda_duracion_no_negativa` | Regla (CHECK) | `CHECK ((duracion_ms >= 0))` |
| `busqueda` | `ck_busqueda_resultados_no_negativos` | Regla (CHECK) | `CHECK ((total_resultados >= 0))` |
| `busqueda` | `ck_busqueda_termino_longitud` | Regla (CHECK) | `CHECK (((char_length(btrim((termino)::text)) >= 2) AND (char_length(btrim((termino)::text)) <= 100)))` |
| `busqueda` | `fk_busqueda_usuario` | Llave foránea | `FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE SET NULL` |
| `busqueda` | `pk_busqueda` | Llave primaria | `PRIMARY KEY (id)` |
| `consumo_api` | `ck_consumo_codigo_http_valido` | Regla (CHECK) | `CHECK (((codigo_http IS NULL) OR ((codigo_http >= 100) AND (codigo_http <= 599))))` |
| `consumo_api` | `ck_consumo_duracion_no_negativa` | Regla (CHECK) | `CHECK ((duracion_ms >= 0))` |
| `consumo_api` | `fk_consumo_marketplace` | Llave foránea | `FOREIGN KEY (marketplace_id) REFERENCES marketplace(id) ON UPDATE CASCADE ON DELETE RESTRICT` |
| `consumo_api` | `pk_consumo_api` | Llave primaria | `PRIMARY KEY (id)` |
| `historial_precio` | `ck_historial_moneda_iso` | Regla (CHECK) | `CHECK ((moneda ~ '^[A-Z]{3}$'::text))` |
| `historial_precio` | `ck_historial_precio_no_negativo` | Regla (CHECK) | `CHECK ((precio >= (0)::numeric))` |
| `historial_precio` | `fk_historial_oferta` | Llave foránea | `FOREIGN KEY (oferta_id) REFERENCES oferta(id) ON UPDATE CASCADE ON DELETE CASCADE` |
| `historial_precio` | `pk_historial_precio` | Llave primaria | `PRIMARY KEY (id)` |
| `marketplace` | `ck_marketplace_codigo_formato` | Regla (CHECK) | `CHECK (((codigo)::text ~ '^[a-z0-9_]+$'::text))` |
| `marketplace` | `ck_marketplace_sitio_web_https` | Regla (CHECK) | `CHECK (((sitio_web)::text ~~ 'https://%'::text))` |
| `marketplace` | `pk_marketplace` | Llave primaria | `PRIMARY KEY (id)` |
| `marketplace` | `uq_marketplace_codigo` | Única | `UNIQUE (codigo)` |
| `oferta` | `ck_oferta_calificacion_0_a_5` | Regla (CHECK) | `CHECK (((calificacion >= (0)::numeric) AND (calificacion <= (5)::numeric)))` |
| `oferta` | `ck_oferta_envio_no_negativo` | Regla (CHECK) | `CHECK (((costo_envio IS NULL) OR (costo_envio >= (0)::numeric)))` |
| `oferta` | `ck_oferta_moneda_iso` | Regla (CHECK) | `CHECK ((moneda ~ '^[A-Z]{3}$'::text))` |
| `oferta` | `ck_oferta_precio_no_negativo` | Regla (CHECK) | `CHECK ((precio_actual >= (0)::numeric))` |
| `oferta` | `ck_oferta_resenas_no_negativas` | Regla (CHECK) | `CHECK ((total_resenas >= 0))` |
| `oferta` | `ck_oferta_url_destino_longitud` | Regla (CHECK) | `CHECK ((char_length(url_destino) <= 2048))` |
| `oferta` | `ck_oferta_url_destino_web` | Regla (CHECK) | `CHECK ((url_destino ~ '^https?://'::text))` |
| `oferta` | `ck_oferta_url_imagen_longitud` | Regla (CHECK) | `CHECK (((url_imagen IS NULL) OR (char_length(url_imagen) <= 2048)))` |
| `oferta` | `fk_oferta_marketplace` | Llave foránea | `FOREIGN KEY (marketplace_id) REFERENCES marketplace(id) ON UPDATE CASCADE ON DELETE RESTRICT` |
| `oferta` | `fk_oferta_producto` | Llave foránea | `FOREIGN KEY (producto_id) REFERENCES producto(id) ON UPDATE CASCADE ON DELETE SET NULL` |
| `oferta` | `pk_oferta` | Llave primaria | `PRIMARY KEY (id)` |
| `oferta` | `uq_oferta_publicacion_por_tienda` | Única | `UNIQUE (marketplace_id, id_externo)` |
| `pqr` | `ck_pqr_asunto_longitud` | Regla (CHECK) | `CHECK (((char_length((asunto)::text) >= 5) AND (char_length((asunto)::text) <= 120)))` |
| `pqr` | `ck_pqr_descripcion_longitud` | Regla (CHECK) | `CHECK (((char_length((descripcion)::text) >= 20) AND (char_length((descripcion)::text) <= 2000)))` |
| `pqr` | `ck_pqr_estado_valido` | Regla (CHECK) | `CHECK (((estado)::text = ANY ((ARRAY['RADICADA'::character varying, 'EN_PROCESO'::character varying, 'RESPONDIDA'::character varying])::text[])))` |
| `pqr` | `ck_pqr_respuesta_completa` | Regla (CHECK) | `CHECK ((((estado)::text = 'RESPONDIDA'::text) = ((respuesta IS NOT NULL) AND (respondida_en IS NOT NULL))))` |
| `pqr` | `ck_pqr_respuesta_posterior` | Regla (CHECK) | `CHECK (((respondida_en IS NULL) OR (respondida_en >= creada_en)))` |
| `pqr` | `ck_pqr_tipo_valido` | Regla (CHECK) | `CHECK (((tipo)::text = ANY ((ARRAY['PETICION'::character varying, 'QUEJA'::character varying, 'RECLAMO'::character varying, 'SUGERENCIA'::character varying])::text[])))` |
| `pqr` | `fk_pqr_respondida_por` | Llave foránea | `FOREIGN KEY (respondida_por) REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE RESTRICT` |
| `pqr` | `fk_pqr_usuario` | Llave foránea | `FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE RESTRICT` |
| `pqr` | `pk_pqr` | Llave primaria | `PRIMARY KEY (id)` |
| `producto` | `pk_producto` | Llave primaria | `PRIMARY KEY (id)` |
| `seguimiento_oferta` | `ck_seguimiento_precio_objetivo_positivo` | Regla (CHECK) | `CHECK (((precio_objetivo IS NULL) OR (precio_objetivo > (0)::numeric)))` |
| `seguimiento_oferta` | `fk_seguimiento_oferta` | Llave foránea | `FOREIGN KEY (oferta_id) REFERENCES oferta(id) ON UPDATE CASCADE ON DELETE CASCADE` |
| `seguimiento_oferta` | `fk_seguimiento_usuario` | Llave foránea | `FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON UPDATE CASCADE ON DELETE CASCADE` |
| `seguimiento_oferta` | `pk_seguimiento_oferta` | Llave primaria | `PRIMARY KEY (usuario_id, oferta_id)` |
| `tasa_cambio` | `ck_tasa_cambio_monedas_distintas` | Regla (CHECK) | `CHECK ((moneda_origen <> moneda_destino))` |
| `tasa_cambio` | `ck_tasa_cambio_monedas_iso` | Regla (CHECK) | `CHECK (((moneda_origen ~ '^[A-Z]{3}$'::text) AND (moneda_destino ~ '^[A-Z]{3}$'::text)))` |
| `tasa_cambio` | `ck_tasa_cambio_valor_positivo` | Regla (CHECK) | `CHECK ((valor > (0)::numeric))` |
| `tasa_cambio` | `pk_tasa_cambio` | Llave primaria | `PRIMARY KEY (id)` |
| `tasa_cambio` | `uq_tasa_cambio_moneda_fecha` | Única | `UNIQUE (moneda_origen, moneda_destino, vigente_desde)` |
| `usuario` | `ck_usuario_celular_colombiano` | Regla (CHECK) | `CHECK (((celular IS NULL) OR ((celular)::text ~ '^3[0-9]{9}$'::text)))` |
| `usuario` | `ck_usuario_correo_formato` | Regla (CHECK) | `CHECK (((correo)::text ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'::text))` |
| `usuario` | `ck_usuario_documento_completo` | Regla (CHECK) | `CHECK (((tipo_documento IS NULL) = (numero_documento IS NULL)))` |
| `usuario` | `ck_usuario_nacimiento_razonable` | Regla (CHECK) | `CHECK (((fecha_nacimiento IS NULL) OR (fecha_nacimiento >= '1900-01-01'::date)))` |
| `usuario` | `ck_usuario_numero_documento_formato` | Regla (CHECK) | `CHECK (((numero_documento IS NULL) OR (((tipo_documento)::text = ANY ((ARRAY['CC'::character varying, 'CE'::character varying])::text[])) AND ((numero_documento)::text ~ '^[0-9]{6,10}$'::text)) OR (((tipo_documento)::text = 'PA'::text) AND ((numero_documento)::text ~ '^[A-Z0-9]{6,12}$'::text))))` |
| `usuario` | `ck_usuario_rol_valido` | Regla (CHECK) | `CHECK (((rol)::text = ANY ((ARRAY['USUARIO'::character varying, 'ADMINISTRADOR'::character varying])::text[])))` |
| `usuario` | `ck_usuario_tipo_documento_valido` | Regla (CHECK) | `CHECK (((tipo_documento)::text = ANY ((ARRAY['CC'::character varying, 'CE'::character varying, 'PA'::character varying])::text[])))` |
| `usuario` | `pk_usuario` | Llave primaria | `PRIMARY KEY (id)` |
| `usuario` | `uq_usuario_correo` | Única | `UNIQUE (correo)` |
| `usuario` | `uq_usuario_documento` | Única | `UNIQUE (tipo_documento, numero_documento)` |
