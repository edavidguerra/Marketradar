-- Marketradar: esquema inicial
-- Regla: el dinero se guarda con NUMERIC, nunca con FLOAT.

CREATE TABLE marketplace (
    id SMALLSERIAL PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(80) NOT NULL,
    sitio_web VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE producto (
    id BIGSERIAL PRIMARY KEY,
    nombre_normalizado VARCHAR(300) NOT NULL,
    marca VARCHAR(100),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE oferta (
    id BIGSERIAL PRIMARY KEY,
    producto_id BIGINT REFERENCES producto(id),
    marketplace_id SMALLINT NOT NULL REFERENCES marketplace(id),
    id_externo VARCHAR(120) NOT NULL,
    titulo VARCHAR(400) NOT NULL,
    url_destino TEXT NOT NULL,
    url_imagen TEXT,
    precio_actual NUMERIC(14,2) NOT NULL CHECK (precio_actual >= 0),
    moneda CHAR(3) NOT NULL DEFAULT 'COP',
    costo_envio NUMERIC(14,2),
    condicion VARCHAR(20),
    vendedor VARCHAR(150),
    consultado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (marketplace_id, id_externo)
);

CREATE TABLE historial_precio (
    id BIGSERIAL PRIMARY KEY,
    oferta_id BIGINT NOT NULL REFERENCES oferta(id) ON DELETE CASCADE,
    precio NUMERIC(14,2) NOT NULL CHECK (precio >= 0),
    moneda CHAR(3) NOT NULL,
    registrado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE busqueda (
    id BIGSERIAL PRIMARY KEY,
    termino VARCHAR(200) NOT NULL,
    total_resultados INTEGER NOT NULL,
    duracion_ms INTEGER NOT NULL,
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE consumo_api (
    id BIGSERIAL PRIMARY KEY,
    marketplace_id SMALLINT NOT NULL REFERENCES marketplace(id),
    exitoso BOOLEAN NOT NULL,
    codigo_http INTEGER,
    duracion_ms INTEGER NOT NULL,
    registrado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_oferta_titulo ON oferta USING gin (to_tsvector('spanish', titulo));
CREATE INDEX idx_oferta_precio ON oferta (precio_actual);
CREATE INDEX idx_historial_oferta_fecha ON historial_precio (oferta_id, registrado_en DESC);
CREATE INDEX idx_busqueda_termino ON busqueda (termino);
