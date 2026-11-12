-- El historial de precios guarda también el vendedor y la condición de cada medición. Así el aviso "¡Bajó de
-- precio!" solo compara precios de la misma oferta: el mismo vendedor, la misma condición y la misma moneda.
-- Google Shopping puede mostrar otro vendedor con el mismo producto, y ese cambio no es un descuento.
-- Las filas que ya existían quedan sin vendedor y no se usan para los avisos, porque no se puede saber de quién eran.
ALTER TABLE historial_precio ADD COLUMN vendedor VARCHAR(150);
ALTER TABLE historial_precio ADD COLUMN condicion VARCHAR(20);
