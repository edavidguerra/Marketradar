-- Credenciales que el sistema renueva solo y que deben sobrevivir a un reinicio del servidor.
-- Caso principal: el refresh token de Mercado Libre, que cambia en cada renovación y solo se puede
-- usar una vez. En Render no hay disco permanente, así que se guarda aquí y no en un archivo.
CREATE TABLE credencial_marketplace (
    codigo              VARCHAR(60)  PRIMARY KEY,
    valor               TEXT         NOT NULL,
    valor_inicial_hash  CHAR(64)     NOT NULL,
    actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_credencial_codigo CHECK (codigo ~ '^[a-z0-9_.-]+$'),
    CONSTRAINT ck_credencial_hash CHECK (valor_inicial_hash ~ '^[0-9a-f]{64}$')
);

COMMENT ON TABLE credencial_marketplace IS 'Credenciales renovadas por el sistema (por ejemplo el refresh token de Mercado Libre)';
COMMENT ON COLUMN credencial_marketplace.valor IS 'Valor vigente de la credencial';
COMMENT ON COLUMN credencial_marketplace.valor_inicial_hash IS 'SHA-256 del valor configurado en las variables de entorno del que se partió; si la variable cambia, se descarta el valor guardado';
