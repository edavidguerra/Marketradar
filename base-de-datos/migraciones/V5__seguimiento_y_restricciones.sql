-- V5: relación muchos a muchos entre usuario y oferta, y restricciones con nombre propio.
--
-- No cambia ni borra datos: agrega la tabla seguimiento_oferta, renombra las restricciones que PostgreSQL
-- había nombrado solo, declara la acción referencial de cada llave foránea y agrega como CHECK las reglas del
-- negocio que hasta ahora solo validaba el backend. Las migraciones V1 a V4 no se editan.

-- ---------------------------------------------------------------------------------------------------------
-- 1. Relación muchos a muchos: una persona sigue muchas ofertas y una oferta la siguen muchas personas.
--    Se resuelve con la tabla intermedia seguimiento_oferta, cuya llave primaria es la pareja de llaves foráneas.
-- ---------------------------------------------------------------------------------------------------------
CREATE TABLE seguimiento_oferta (
    usuario_id BIGINT NOT NULL,
    oferta_id BIGINT NOT NULL,
    precio_objetivo NUMERIC(14,2),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_seguimiento_oferta PRIMARY KEY (usuario_id, oferta_id),
    -- Si se borra la cuenta, sus seguimientos ya no tienen dueño: se borran con ella.
    CONSTRAINT fk_seguimiento_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE ON UPDATE CASCADE,
    -- Si se borra la oferta, ya no hay nada que seguir: se borran sus seguimientos.
    CONSTRAINT fk_seguimiento_oferta FOREIGN KEY (oferta_id)
        REFERENCES oferta(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT ck_seguimiento_precio_objetivo_positivo CHECK (precio_objetivo IS NULL OR precio_objetivo > 0)
);

CREATE INDEX idx_seguimiento_oferta ON seguimiento_oferta (oferta_id);

COMMENT ON TABLE seguimiento_oferta IS
    'Ofertas que sigue cada persona para enterarse cuando bajan de precio (relación muchos a muchos usuario-oferta).';

-- ---------------------------------------------------------------------------------------------------------
-- 2. Nombres propios para las restricciones que ya existían.
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE marketplace RENAME CONSTRAINT marketplace_pkey TO pk_marketplace;
ALTER TABLE marketplace RENAME CONSTRAINT marketplace_codigo_key TO uq_marketplace_codigo;

ALTER TABLE producto RENAME CONSTRAINT producto_pkey TO pk_producto;

ALTER TABLE oferta RENAME CONSTRAINT oferta_pkey TO pk_oferta;
ALTER TABLE oferta RENAME CONSTRAINT oferta_marketplace_id_id_externo_key TO uq_oferta_publicacion_por_tienda;
ALTER TABLE oferta RENAME CONSTRAINT oferta_precio_actual_check TO ck_oferta_precio_no_negativo;
ALTER TABLE oferta RENAME CONSTRAINT oferta_calificacion_check TO ck_oferta_calificacion_0_a_5;
ALTER TABLE oferta RENAME CONSTRAINT oferta_total_resenas_check TO ck_oferta_resenas_no_negativas;

ALTER TABLE historial_precio RENAME CONSTRAINT historial_precio_pkey TO pk_historial_precio;
ALTER TABLE historial_precio RENAME CONSTRAINT historial_precio_precio_check TO ck_historial_precio_no_negativo;

ALTER TABLE busqueda RENAME CONSTRAINT busqueda_pkey TO pk_busqueda;

ALTER TABLE consumo_api RENAME CONSTRAINT consumo_api_pkey TO pk_consumo_api;

ALTER TABLE usuario RENAME CONSTRAINT usuario_pkey TO pk_usuario;
ALTER TABLE usuario RENAME CONSTRAINT usuario_correo_key TO uq_usuario_correo;
ALTER TABLE usuario RENAME CONSTRAINT usuario_tipo_documento_numero_documento_key TO uq_usuario_documento;
ALTER TABLE usuario RENAME CONSTRAINT usuario_rol_check TO ck_usuario_rol_valido;
ALTER TABLE usuario RENAME CONSTRAINT usuario_tipo_documento_check TO ck_usuario_tipo_documento_valido;

ALTER TABLE pqr RENAME CONSTRAINT pqr_pkey TO pk_pqr;
ALTER TABLE pqr RENAME CONSTRAINT pqr_tipo_check TO ck_pqr_tipo_valido;
ALTER TABLE pqr RENAME CONSTRAINT pqr_estado_check TO ck_pqr_estado_valido;

ALTER TABLE tasa_cambio RENAME CONSTRAINT tasa_cambio_pkey TO pk_tasa_cambio;
ALTER TABLE tasa_cambio RENAME CONSTRAINT tasa_cambio_moneda_origen_moneda_destino_vigente_desde_key
    TO uq_tasa_cambio_moneda_fecha;
ALTER TABLE tasa_cambio RENAME CONSTRAINT tasa_cambio_valor_check TO ck_tasa_cambio_valor_positivo;

-- ---------------------------------------------------------------------------------------------------------
-- 3. Llaves foráneas con nombre y con la acción referencial que corresponde.
--    Los id son autoincrementales y nunca cambian, pero se declara ON UPDATE CASCADE para que, si cambiaran,
--    las filas hijas los sigan. ON DELETE se elige según la regla de cada relación.
-- ---------------------------------------------------------------------------------------------------------

-- Una tienda con ofertas no se borra: se desactiva con marketplace.activo, para no perder ofertas ni historial.
ALTER TABLE oferta DROP CONSTRAINT oferta_marketplace_id_fkey;
ALTER TABLE oferta ADD CONSTRAINT fk_oferta_marketplace FOREIGN KEY (marketplace_id)
    REFERENCES marketplace(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- Si se borra un producto normalizado, sus ofertas siguen existiendo en la tienda: quedan sin asociar.
ALTER TABLE oferta DROP CONSTRAINT oferta_producto_id_fkey;
ALTER TABLE oferta ADD CONSTRAINT fk_oferta_producto FOREIGN KEY (producto_id)
    REFERENCES producto(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- El historial no tiene sentido sin su oferta: se borra con ella.
ALTER TABLE historial_precio DROP CONSTRAINT historial_precio_oferta_id_fkey;
ALTER TABLE historial_precio ADD CONSTRAINT fk_historial_oferta FOREIGN KEY (oferta_id)
    REFERENCES oferta(id) ON DELETE CASCADE ON UPDATE CASCADE;

-- Las mediciones de una API se conservan: una tienda con registros de consumo no se borra.
ALTER TABLE consumo_api DROP CONSTRAINT consumo_api_marketplace_id_fkey;
ALTER TABLE consumo_api ADD CONSTRAINT fk_consumo_marketplace FOREIGN KEY (marketplace_id)
    REFERENCES marketplace(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- Al borrar una cuenta, sus búsquedas se conservan sin dueño para no perder las estadísticas.
ALTER TABLE busqueda DROP CONSTRAINT busqueda_usuario_id_fkey;
ALTER TABLE busqueda ADD CONSTRAINT fk_busqueda_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id) ON DELETE SET NULL ON UPDATE CASCADE;

-- Una PQR es un registro de atención que se debe conservar: no se borra una cuenta que tenga PQR.
ALTER TABLE pqr DROP CONSTRAINT pqr_usuario_id_fkey;
ALTER TABLE pqr ADD CONSTRAINT fk_pqr_usuario FOREIGN KEY (usuario_id)
    REFERENCES usuario(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- Tampoco se borra el administrador que respondió una PQR, para saber siempre quién la contestó.
ALTER TABLE pqr DROP CONSTRAINT pqr_respondida_por_fkey;
ALTER TABLE pqr ADD CONSTRAINT fk_pqr_respondida_por FOREIGN KEY (respondida_por)
    REFERENCES usuario(id) ON DELETE RESTRICT ON UPDATE CASCADE;

-- ---------------------------------------------------------------------------------------------------------
-- 4. Reglas del negocio que ahora también protege la base de datos.
-- ---------------------------------------------------------------------------------------------------------
ALTER TABLE marketplace
    ADD CONSTRAINT ck_marketplace_codigo_formato CHECK (codigo ~ '^[a-z0-9_]+$'),
    ADD CONSTRAINT ck_marketplace_sitio_web_https CHECK (sitio_web LIKE 'https://%');

ALTER TABLE oferta
    ADD CONSTRAINT ck_oferta_envio_no_negativo CHECK (costo_envio IS NULL OR costo_envio >= 0),
    ADD CONSTRAINT ck_oferta_moneda_iso CHECK (moneda ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_oferta_url_destino_web CHECK (url_destino ~ '^https?://'),
    ADD CONSTRAINT ck_oferta_url_destino_longitud CHECK (char_length(url_destino) <= 2048),
    ADD CONSTRAINT ck_oferta_url_imagen_longitud CHECK (url_imagen IS NULL OR char_length(url_imagen) <= 2048);

ALTER TABLE historial_precio
    ADD CONSTRAINT ck_historial_moneda_iso CHECK (moneda ~ '^[A-Z]{3}$');

ALTER TABLE busqueda
    ADD CONSTRAINT ck_busqueda_termino_longitud CHECK (char_length(btrim(termino)) BETWEEN 2 AND 100),
    ADD CONSTRAINT ck_busqueda_resultados_no_negativos CHECK (total_resultados >= 0),
    ADD CONSTRAINT ck_busqueda_duracion_no_negativa CHECK (duracion_ms >= 0);

ALTER TABLE consumo_api
    ADD CONSTRAINT ck_consumo_duracion_no_negativa CHECK (duracion_ms >= 0),
    ADD CONSTRAINT ck_consumo_codigo_http_valido CHECK (codigo_http IS NULL OR codigo_http BETWEEN 100 AND 599);

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_correo_formato CHECK (correo ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    ADD CONSTRAINT ck_usuario_celular_colombiano CHECK (celular IS NULL OR celular ~ '^3[0-9]{9}$'),
    ADD CONSTRAINT ck_usuario_documento_completo
        CHECK ((tipo_documento IS NULL) = (numero_documento IS NULL)),
    -- Cédulas de 6 a 10 dígitos; pasaporte de 6 a 12 letras mayúsculas o números.
    ADD CONSTRAINT ck_usuario_numero_documento_formato CHECK (
        numero_documento IS NULL
        OR (tipo_documento IN ('CC', 'CE') AND numero_documento ~ '^[0-9]{6,10}$')
        OR (tipo_documento = 'PA' AND numero_documento ~ '^[A-Z0-9]{6,12}$')),
    ADD CONSTRAINT ck_usuario_nacimiento_razonable
        CHECK (fecha_nacimiento IS NULL OR fecha_nacimiento >= DATE '1900-01-01');

ALTER TABLE pqr
    ADD CONSTRAINT ck_pqr_asunto_longitud CHECK (char_length(asunto) BETWEEN 5 AND 120),
    ADD CONSTRAINT ck_pqr_descripcion_longitud CHECK (char_length(descripcion) BETWEEN 20 AND 2000),
    -- Una PQR está RESPONDIDA si y solo si tiene el texto de la respuesta y la fecha en que se respondió.
    ADD CONSTRAINT ck_pqr_respuesta_completa CHECK (
        (estado = 'RESPONDIDA') = (respuesta IS NOT NULL AND respondida_en IS NOT NULL)),
    ADD CONSTRAINT ck_pqr_respuesta_posterior CHECK (respondida_en IS NULL OR respondida_en >= creada_en);

ALTER TABLE tasa_cambio
    ADD CONSTRAINT ck_tasa_cambio_monedas_iso CHECK (moneda_origen ~ '^[A-Z]{3}$' AND moneda_destino ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT ck_tasa_cambio_monedas_distintas CHECK (moneda_origen <> moneda_destino);
