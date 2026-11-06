-- Google Shopping por SerpApi: una sola consulta trae precios de muchas tiendas colombianas.
-- La tienda real de cada oferta queda en oferta.vendedor (por ejemplo Éxito o Falabella).
INSERT INTO marketplace (codigo, nombre, sitio_web, activo) VALUES
    ('googleshopping', 'Google Shopping', 'https://shopping.google.com', TRUE)
ON CONFLICT (codigo) DO NOTHING;
