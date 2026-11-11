-- Idioma en el que la persona quiere leer. Se elige al crear la cuenta y se cambia en Mi perfil. Sirve para
-- ofrecer "Traducir" solo en las reseñas que están en otro idioma. Las cuentas que ya existían quedan en español.
ALTER TABLE usuario ADD COLUMN idioma VARCHAR(2) NOT NULL DEFAULT 'es';

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_idioma
        CHECK (idioma IN ('es', 'en', 'pt', 'fr', 'it', 'de'));
