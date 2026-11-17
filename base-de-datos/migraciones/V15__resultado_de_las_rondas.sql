-- V15: separa en cada ronda de Seguimientos el fin técnico, el resultado de las consultas a las tiendas y el del correo.
--
-- Todo es aditivo. Hasta V14, una ronda que terminaba sin excepción quedaba 'TERMINADA' aunque todas sus consultas a
-- las tiendas hubieran fallado, y el estado del cron la contaba como una comprobación correcta. Ahora cada ronda guarda
-- cuántas consultas funcionaron, cuántas fallaron, cuántos seguimientos no aparecieron en una consulta que sí
-- funcionó y cuántos correos rechazó el proveedor. Las filas anteriores quedan con resultado_consultas NULL: no se
-- sabe qué pasó con sus consultas y no cuentan como evidencia de una comprobación.

ALTER TABLE ejecucion_tarea
    ADD COLUMN consultas_exitosas     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN consultas_fallidas     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN ofertas_no_encontradas INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN correos_rechazados     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN resultado_consultas    VARCHAR(20);

ALTER TABLE ejecucion_tarea
    ADD CONSTRAINT ck_ejecucion_tarea_resultado_consultas
        CHECK (resultado_consultas IS NULL
            OR resultado_consultas IN ('SIN_TRABAJO', 'SOLO_POSPUESTOS', 'CORRECTAS', 'PARCIALES', 'FALLIDAS')),
    ADD CONSTRAINT ck_ejecucion_tarea_contadores_no_negativos
        CHECK (consultas_exitosas >= 0 AND consultas_fallidas >= 0 AND ofertas_no_encontradas >= 0
            AND correos_rechazados >= 0);

COMMENT ON COLUMN ejecucion_tarea.resultado_consultas IS
    'SIN_TRABAJO, SOLO_POSPUESTOS, CORRECTAS, PARCIALES (unas consultas funcionaron y otras no) o FALLIDAS; NULL antes de V15';
COMMENT ON COLUMN ejecucion_tarea.ofertas_no_encontradas IS
    'Seguimientos cuya consulta funcionó pero la oferta ya no apareció (no es un error de la tienda)';
COMMENT ON COLUMN ejecucion_tarea.correos_rechazados IS
    'Intentos de envío que el proveedor de correo rechazó en esta ronda (aceptado tampoco significa entregado)';
