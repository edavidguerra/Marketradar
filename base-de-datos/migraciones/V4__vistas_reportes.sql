-- Vistas para reportes. Solo leen las tablas existentes: no cambian datos ni el backend.

-- Precio de compra de cada oferta de una tienda activa, con el envío incluido y convertido a pesos
-- con la tasa más reciente de su moneda. Si la moneda no tiene tasa, precio_total_cop queda en NULL.
CREATE VIEW vista_ofertas_vigentes AS
SELECT o.id AS oferta_id,
       o.producto_id,
       m.codigo AS marketplace,
       m.nombre AS tienda,
       o.titulo,
       o.precio_actual,
       COALESCE(o.costo_envio, 0) AS costo_envio,
       o.moneda,
       o.precio_actual + COALESCE(o.costo_envio, 0) AS precio_total,
       ROUND((o.precio_actual + COALESCE(o.costo_envio, 0))
             * CASE WHEN o.moneda = 'COP' THEN 1 ELSE t.valor END, 2) AS precio_total_cop,
       o.calificacion,
       o.total_resenas,
       o.condicion,
       o.url_destino,
       o.consultado_en
FROM oferta o
JOIN marketplace m ON m.id = o.marketplace_id
LEFT JOIN LATERAL (
    SELECT tc.valor
    FROM tasa_cambio tc
    WHERE tc.moneda_origen = o.moneda
      AND tc.moneda_destino = 'COP'
    ORDER BY tc.vigente_desde DESC
    LIMIT 1
) t ON TRUE
WHERE m.activo;

-- Ofertas cuyo último precio registrado es menor que el anterior.
CREATE VIEW vista_bajadas_precio AS
WITH cambios AS (
    SELECT h.oferta_id,
           h.precio,
           h.moneda,
           h.registrado_en,
           LAG(h.precio) OVER (PARTITION BY h.oferta_id ORDER BY h.registrado_en) AS precio_anterior,
           ROW_NUMBER() OVER (PARTITION BY h.oferta_id ORDER BY h.registrado_en DESC) AS orden
    FROM historial_precio h
)
SELECT c.oferta_id,
       o.titulo,
       m.nombre AS tienda,
       c.precio_anterior,
       c.precio AS precio_nuevo,
       c.moneda,
       c.precio_anterior - c.precio AS ahorro,
       ROUND((c.precio_anterior - c.precio) * 100 / c.precio_anterior, 1) AS porcentaje_bajada,
       c.registrado_en AS bajo_en
FROM cambios c
JOIN oferta o ON o.id = c.oferta_id
JOIN marketplace m ON m.id = o.marketplace_id
WHERE c.orden = 1
  AND c.precio < c.precio_anterior;

-- Cantidad de PQR por tipo y estado, con el tiempo promedio de respuesta en horas.
CREATE VIEW vista_resumen_pqr AS
SELECT tipo,
       estado,
       COUNT(*) AS cantidad,
       MIN(creada_en) AS primera,
       MAX(creada_en) AS ultima,
       ROUND(AVG(EXTRACT(EPOCH FROM (respondida_en - creada_en)) / 3600)::NUMERIC, 1) AS horas_promedio_respuesta
FROM pqr
GROUP BY tipo, estado;

-- Disponibilidad de la API de cada tienda: consultas, porcentaje de éxito y tiempo promedio.
CREATE VIEW vista_disponibilidad_api AS
SELECT m.codigo AS marketplace,
       m.nombre AS tienda,
       COUNT(c.id) AS consultas,
       COUNT(c.id) FILTER (WHERE c.exitoso) AS exitosas,
       ROUND(100.0 * COUNT(c.id) FILTER (WHERE c.exitoso) / NULLIF(COUNT(c.id), 0), 1) AS porcentaje_exito,
       ROUND(AVG(c.duracion_ms)) AS duracion_promedio_ms,
       MAX(c.registrado_en) AS ultima_consulta
FROM marketplace m
LEFT JOIN consumo_api c ON c.marketplace_id = m.id
GROUP BY m.id, m.codigo, m.nombre;
