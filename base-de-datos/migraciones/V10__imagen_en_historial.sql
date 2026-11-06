-- Foto de cada búsqueda en el historial, para que la persona reconozca lo que buscó sin tener que leer.
-- Se guarda la foto del producto MVP o, si no tiene, la de la primera oferta con foto.
ALTER TABLE busqueda ADD COLUMN url_imagen TEXT;

ALTER TABLE busqueda
    ADD CONSTRAINT ck_busqueda_url_imagen_segura
        CHECK (url_imagen IS NULL OR (url_imagen LIKE 'https://%' AND char_length(url_imagen) <= 2048));

-- Las búsquedas que ya estaban toman la foto de la oferta guardada más reciente cuyo título coincide con lo buscado.
UPDATE busqueda b
SET url_imagen = (
    SELECT o.url_imagen
    FROM oferta o
    WHERE o.url_imagen LIKE 'https://%'
      AND char_length(o.url_imagen) <= 2048
      AND to_tsvector('spanish', o.titulo) @@ plainto_tsquery('spanish', b.termino)
    ORDER BY o.consultado_en DESC
    LIMIT 1
)
WHERE b.url_imagen IS NULL
  AND b.total_resultados > 0;
