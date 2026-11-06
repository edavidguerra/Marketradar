-- Retira la tienda de demostración: los productos reales ya llegan por Google Shopping (V8).
-- Se borran sus ofertas de ejemplo (el historial de precio y los seguimientos se borran en cascada)
-- y la tienda queda inactiva. La fila no se borra porque consumo_api guarda su uso.
DELETE FROM oferta
WHERE marketplace_id = (SELECT id FROM marketplace WHERE codigo = 'demostracion');

UPDATE marketplace SET activo = FALSE WHERE codigo = 'demostracion';
