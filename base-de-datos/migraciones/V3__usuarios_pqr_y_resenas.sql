-- Usuarios con rol, PQR, historial por usuario, reseñas de ofertas y tasas de cambio.

CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    correo VARCHAR(150) NOT NULL UNIQUE,
    clave_hash VARCHAR(100) NOT NULL,
    rol VARCHAR(20) NOT NULL CHECK (rol IN ('USUARIO', 'ADMINISTRADOR')),
    nombres VARCHAR(80) NOT NULL,
    apellidos VARCHAR(80) NOT NULL,
    tipo_documento VARCHAR(3) CHECK (tipo_documento IN ('CC', 'CE', 'PA')),
    numero_documento VARCHAR(15),
    fecha_nacimiento DATE,
    celular VARCHAR(10),
    ciudad VARCHAR(80),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tipo_documento, numero_documento)
);

CREATE TABLE pqr (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('PETICION', 'QUEJA', 'RECLAMO', 'SUGERENCIA')),
    asunto VARCHAR(120) NOT NULL,
    descripcion VARCHAR(2000) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'RADICADA' CHECK (estado IN ('RADICADA', 'EN_PROCESO', 'RESPONDIDA')),
    respuesta VARCHAR(2000),
    respondida_por BIGINT REFERENCES usuario(id),
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    respondida_en TIMESTAMPTZ
);

CREATE INDEX idx_pqr_usuario ON pqr (usuario_id, creada_en DESC);
CREATE INDEX idx_pqr_estado ON pqr (estado, creada_en);

ALTER TABLE busqueda ADD COLUMN usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL;
CREATE INDEX idx_busqueda_usuario ON busqueda (usuario_id, creada_en DESC);

ALTER TABLE oferta ADD COLUMN calificacion NUMERIC(2,1) CHECK (calificacion BETWEEN 0 AND 5);
ALTER TABLE oferta ADD COLUMN total_resenas INTEGER CHECK (total_resenas >= 0);

CREATE TABLE tasa_cambio (
    id BIGSERIAL PRIMARY KEY,
    moneda_origen CHAR(3) NOT NULL,
    moneda_destino CHAR(3) NOT NULL DEFAULT 'COP',
    valor NUMERIC(14,4) NOT NULL CHECK (valor > 0),
    vigente_desde DATE NOT NULL,
    fuente VARCHAR(80) NOT NULL,
    consultada_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (moneda_origen, moneda_destino, vigente_desde)
);
