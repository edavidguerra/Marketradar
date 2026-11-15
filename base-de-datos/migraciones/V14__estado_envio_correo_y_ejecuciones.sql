-- V14: estado real de los correos de Seguimientos y registro de las rondas de revisión de precios.
--
-- Todo es aditivo: no se borra ni se cambia ningún dato existente.
--
-- 1. token_correo guarda en qué quedó el envío de cada enlace de verificación: PENDIENTE (se está mandando), ACEPTADO
--    (el servicio de correo lo aceptó; eso NO confirma que haya llegado a la bandeja) o FALLIDO. Los enlaces creados
--    antes de esta versión quedan como SIN_REGISTRO, porque no se sabe qué pasó con ellos.
-- 2. aviso_seguimiento guarda qué proveedor aceptó cada aviso y su identificador. El estado 'ENVIADO' sigue
--    significando "aceptado por el servicio de correo", no "entregado".
-- 3. ejecucion_tarea deja constancia de cada ronda (quién la disparó, cuándo empezó y terminó y qué hizo), para poder
--    comprobar con datos cada cuánto se revisan los precios cuando el alojamiento duerme el servidor.

-- ---------------------------------------------------------------------------------------------------------
-- 1. Enlaces de verificación
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE token_correo
    ADD COLUMN estado_envio         VARCHAR(20) NOT NULL DEFAULT 'SIN_REGISTRO',
    ADD COLUMN intentos             SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN ultimo_error         VARCHAR(300),
    ADD COLUMN aceptado_en          TIMESTAMPTZ,
    ADD COLUMN proveedor_correo     VARCHAR(20),
    ADD COLUMN id_mensaje_proveedor VARCHAR(200);

-- Las filas nuevas empiezan como PENDIENTE; las viejas ya quedaron como SIN_REGISTRO.
ALTER TABLE token_correo ALTER COLUMN estado_envio SET DEFAULT 'PENDIENTE';

ALTER TABLE token_correo
    ADD CONSTRAINT ck_token_correo_estado_envio
        CHECK (estado_envio IN ('SIN_REGISTRO', 'PENDIENTE', 'ACEPTADO', 'FALLIDO')),
    ADD CONSTRAINT ck_token_correo_intentos_no_negativos CHECK (intentos >= 0),
    ADD CONSTRAINT ck_token_correo_aceptado_completo
        CHECK ((estado_envio = 'ACEPTADO') = (aceptado_en IS NOT NULL));

-- Un solo envío en curso por persona: dos clics seguidos en "Enviarme el enlace" no mandan dos correos.
CREATE UNIQUE INDEX uq_token_correo_envio_en_curso ON token_correo (usuario_id) WHERE estado_envio = 'PENDIENTE';

COMMENT ON COLUMN token_correo.estado_envio IS
    'PENDIENTE, ACEPTADO (el servicio de correo lo aceptó; no confirma la entrega), FALLIDO o SIN_REGISTRO (anterior a V14)';

-- ---------------------------------------------------------------------------------------------------------
-- 2. Avisos de Seguimientos
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE aviso_seguimiento
    ADD COLUMN proveedor_correo     VARCHAR(20),
    ADD COLUMN id_mensaje_proveedor VARCHAR(200);

COMMENT ON COLUMN aviso_seguimiento.estado IS
    'PENDIENTE, ENVIADO (aceptado por el servicio de correo; no confirma la entrega), FALLIDO o DESCARTADO';

-- ---------------------------------------------------------------------------------------------------------
-- 3. Rondas de revisión de precios
-- ---------------------------------------------------------------------------------------------------------
CREATE TABLE ejecucion_tarea (
    id                BIGSERIAL    NOT NULL,
    tarea             VARCHAR(40)  NOT NULL,
    origen            VARCHAR(20)  NOT NULL,
    estado            VARCHAR(20)  NOT NULL DEFAULT 'EN_CURSO',
    iniciada_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    terminada_en      TIMESTAMPTZ,
    revisados         INTEGER      NOT NULL DEFAULT 0,
    consultas         INTEGER      NOT NULL DEFAULT 0,
    pospuestos        INTEGER      NOT NULL DEFAULT 0,
    avisos_nuevos     INTEGER      NOT NULL DEFAULT 0,
    correos_aceptados INTEGER      NOT NULL DEFAULT 0,
    detalle           VARCHAR(300),
    CONSTRAINT pk_ejecucion_tarea PRIMARY KEY (id),
    CONSTRAINT ck_ejecucion_tarea_origen CHECK (origen IN ('CRON', 'INTERNA')),
    CONSTRAINT ck_ejecucion_tarea_estado CHECK (estado IN ('EN_CURSO', 'TERMINADA', 'OMITIDA', 'FALLIDA')),
    CONSTRAINT ck_ejecucion_tarea_fin CHECK (terminada_en IS NULL OR terminada_en >= iniciada_en)
);

CREATE INDEX idx_ejecucion_tarea_reciente ON ejecucion_tarea (tarea, iniciada_en DESC);

COMMENT ON TABLE ejecucion_tarea IS
    'Una fila por ronda de una tarea periódica (por ejemplo la revisión de Seguimientos); se guardan 30 días';
