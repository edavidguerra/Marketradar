-- V13: seguimiento de ofertas con avisos por correo y datos para comparar productos.
--
-- Todo es aditivo: no se borra ni se cambia ningún dato. Se reutiliza la tabla seguimiento_oferta de V5 (la pareja
-- usuario-oferta sigue siendo su llave primaria) y se le agregan las columnas que necesita la tarea que revisa los
-- precios. Las filas que ya existían quedan como seguimientos de "bajada de precio" activos y sin precio de referencia:
-- la primera comprobación lo fija sin mandar ningún aviso.

-- ---------------------------------------------------------------------------------------------------------
-- 1. Correo verificado. Los avisos solo se mandan a un correo que la persona confirmó con un enlace.
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE usuario ADD COLUMN correo_verificado_en TIMESTAMPTZ;

COMMENT ON COLUMN usuario.correo_verificado_en IS
    'Cuándo la persona abrió el enlace de verificación de su correo; NULL si no lo ha verificado';

-- Enlaces de un solo uso que se mandan por correo. Solo se guarda el SHA-256 del enlace, nunca el enlace.
CREATE TABLE token_correo (
    token_hash  CHAR(64)     NOT NULL,
    usuario_id  BIGINT       NOT NULL,
    proposito   VARCHAR(20)  NOT NULL,
    correo      VARCHAR(150) NOT NULL,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    vence_en    TIMESTAMPTZ  NOT NULL,
    usado_en    TIMESTAMPTZ,
    CONSTRAINT pk_token_correo PRIMARY KEY (token_hash),
    CONSTRAINT fk_token_correo_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_token_correo_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_token_correo_proposito CHECK (proposito IN ('VERIFICAR_CORREO')),
    CONSTRAINT ck_token_correo_vigencia CHECK (vence_en > creado_en)
);

CREATE INDEX idx_token_correo_usuario ON token_correo (usuario_id, creado_en DESC);

-- ---------------------------------------------------------------------------------------------------------
-- 2. Seguimiento: qué avisar, en qué estado está y qué pasó en la última comprobación.
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE seguimiento_oferta
    ADD COLUMN tipo_aviso VARCHAR(20) NOT NULL DEFAULT 'BAJA_PRECIO',
    ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    -- Precio con que se compara la siguiente comprobación y de quién era (vendedor, condición y moneda): si cambia el
    -- vendedor o la condición, el precio nuevo es de otra oferta y no se toma como descuento.
    ADD COLUMN precio_referencia NUMERIC(14,2),
    ADD COLUMN moneda_referencia CHAR(3),
    ADD COLUMN precio_original_referencia NUMERIC(14,2),
    ADD COLUMN moneda_original_referencia CHAR(3),
    ADD COLUMN vendedor_referencia VARCHAR(150),
    ADD COLUMN condicion_referencia VARCHAR(20),
    ADD COLUMN variante_referencia VARCHAR(200),
    ADD COLUMN referencia_desde TIMESTAMPTZ,
    -- Lo que buscó la persona cuando empezó a seguir la oferta: se repite esa búsqueda para encontrarla otra vez.
    ADD COLUMN termino_busqueda VARCHAR(100),
    ADD COLUMN ultimo_precio NUMERIC(14,2),
    ADD COLUMN ultima_comprobacion TIMESTAMPTZ,
    ADD COLUMN resultado_comprobacion VARCHAR(30) NOT NULL DEFAULT 'SIN_COMPROBAR',
    ADD COLUMN fallos_consecutivos SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN proxima_comprobacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- El aviso de precio objetivo se manda una vez; se vuelve a mandar solo si el precio sube por encima y baja otra vez.
    ADD COLUMN objetivo_avisado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN token_cancelacion VARCHAR(64),
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now();

-- Filas anteriores a esta versión (nadie aceptó recibir correos con ellas): si tenían precio objetivo, avisan por
-- ese precio, y quedan en pausa hasta que la persona las reanude en "Mis seguimientos".
UPDATE seguimiento_oferta
SET tipo_aviso = CASE WHEN precio_objetivo IS NOT NULL THEN 'PRECIO_OBJETIVO' ELSE 'BAJA_PRECIO' END,
    estado     = 'PAUSADO';

ALTER TABLE seguimiento_oferta
    ADD CONSTRAINT ck_seguimiento_tipo_aviso CHECK (tipo_aviso IN ('BAJA_PRECIO', 'PRECIO_OBJETIVO')),
    ADD CONSTRAINT ck_seguimiento_estado CHECK (estado IN ('ACTIVO', 'PAUSADO', 'CANCELADO')),
    ADD CONSTRAINT ck_seguimiento_objetivo_definido
        CHECK (tipo_aviso <> 'PRECIO_OBJETIVO' OR precio_objetivo IS NOT NULL),
    ADD CONSTRAINT ck_seguimiento_resultado CHECK (resultado_comprobacion IN (
        'SIN_COMPROBAR', 'REFERENCIA', 'SIN_CAMBIO', 'SUBIO', 'BAJO', 'OBJETIVO_ALCANZADO', 'NO_ENCONTRADA',
        'VARIANTE_DISTINTA', 'PRECIO_DUDOSO', 'ERROR_TIENDA')),
    ADD CONSTRAINT ck_seguimiento_referencia_no_negativa CHECK (precio_referencia IS NULL OR precio_referencia >= 0),
    ADD CONSTRAINT ck_seguimiento_ultimo_no_negativo CHECK (ultimo_precio IS NULL OR ultimo_precio >= 0),
    ADD CONSTRAINT ck_seguimiento_moneda_iso CHECK (moneda_referencia IS NULL OR moneda_referencia ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_seguimiento_fallos_no_negativos CHECK (fallos_consecutivos >= 0),
    ADD CONSTRAINT uq_seguimiento_token_cancelacion UNIQUE (token_cancelacion);

CREATE INDEX idx_seguimiento_pendientes ON seguimiento_oferta (proxima_comprobacion) WHERE estado = 'ACTIVO';

COMMENT ON COLUMN seguimiento_oferta.precio_referencia IS
    'Último precio comprobado de la misma oferta (mismo vendedor, condición y moneda); con él se decide si bajó';
COMMENT ON COLUMN seguimiento_oferta.token_cancelacion IS
    'Clave aleatoria del enlace "Dejar de seguir" de los correos; solo permite cancelar este seguimiento';

-- ---------------------------------------------------------------------------------------------------------
-- 3. Avisos por correo. Cada evento tiene una clave única: la misma bajada nunca se avisa dos veces.
-- ---------------------------------------------------------------------------------------------------------
CREATE TABLE aviso_seguimiento (
    id               BIGSERIAL    NOT NULL,
    usuario_id       BIGINT       NOT NULL,
    oferta_id        BIGINT       NOT NULL,
    tipo             VARCHAR(20)  NOT NULL,
    clave_evento     VARCHAR(200) NOT NULL,
    correo_destino   VARCHAR(150) NOT NULL,
    precio_anterior  NUMERIC(14,2),
    precio_actual    NUMERIC(14,2),
    moneda           CHAR(3),
    comprobado_en    TIMESTAMPTZ,
    estado           VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    intentos         SMALLINT     NOT NULL DEFAULT 0,
    ultimo_error     VARCHAR(300),
    proximo_intento  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    enviado_en       TIMESTAMPTZ,
    CONSTRAINT pk_aviso_seguimiento PRIMARY KEY (id),
    CONSTRAINT uq_aviso_clave_evento UNIQUE (clave_evento),
    -- Si se borra el seguimiento (o la cuenta, o la oferta), sus avisos se borran con él.
    CONSTRAINT fk_aviso_seguimiento FOREIGN KEY (usuario_id, oferta_id)
        REFERENCES seguimiento_oferta(usuario_id, oferta_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_aviso_tipo CHECK (tipo IN ('CONFIRMACION', 'BAJA_PRECIO', 'PRECIO_OBJETIVO')),
    CONSTRAINT ck_aviso_estado CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO', 'DESCARTADO')),
    CONSTRAINT ck_aviso_intentos_no_negativos CHECK (intentos >= 0),
    CONSTRAINT ck_aviso_enviado_completo CHECK ((estado = 'ENVIADO') = (enviado_en IS NOT NULL)),
    CONSTRAINT ck_aviso_moneda_iso CHECK (moneda IS NULL OR moneda ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_aviso_pendientes ON aviso_seguimiento (proximo_intento) WHERE estado = 'PENDIENTE';
CREATE INDEX idx_aviso_seguimiento ON aviso_seguimiento (usuario_id, oferta_id, creado_en DESC);

COMMENT ON TABLE aviso_seguimiento IS
    'Correos de los seguimientos: confirmación y avisos de precio, con su estado de envío y reintentos';

-- ---------------------------------------------------------------------------------------------------------
-- 4. Comparación de productos: las características que informa la tienda (por ejemplo "128 GB", "Wi-Fi").
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE oferta ADD COLUMN detalles VARCHAR(1000);

COMMENT ON COLUMN oferta.detalles IS
    'Características cortas que informa la tienda, separadas por " | "; NULL si no informó ninguna';
