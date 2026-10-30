-- Consultas de reportes de Marketradar. Cada una responde una pregunta del negocio.
-- Se ejecutan con psql sobre la base que ya tiene las migraciones V1 a V4:
--   psql -U marketradar -d marketradar -f consultas/reportes.sql

\echo '1. ¿Qué productos buscan más las personas en los últimos 30 días?'
SELECT LOWER(TRIM(termino)) AS termino,
       COUNT(*) AS veces,
       COUNT(DISTINCT usuario_id) AS personas,
       ROUND(AVG(total_resultados), 1) AS resultados_promedio
FROM busqueda
WHERE creada_en >= now() - INTERVAL '30 days'
GROUP BY LOWER(TRIM(termino))
ORDER BY veces DESC, termino
LIMIT 10;

\echo '2. ¿En qué tienda está más barato cada producto, con envío y en pesos?'
SELECT p.nombre_normalizado AS producto,
       v.tienda,
       v.precio_total_cop
FROM vista_ofertas_vigentes v
JOIN producto p ON p.id = v.producto_id
WHERE v.precio_total_cop = (
    SELECT MIN(v2.precio_total_cop)
    FROM vista_ofertas_vigentes v2
    WHERE v2.producto_id = v.producto_id
)
ORDER BY producto;

\echo '3. ¿Qué ofertas cuestan menos que el promedio de su producto?'
SELECT v.oferta_id,
       v.titulo,
       v.tienda,
       v.precio_total_cop,
       promedio.valor AS promedio_producto
FROM vista_ofertas_vigentes v
JOIN (
    SELECT producto_id, ROUND(AVG(precio_total_cop), 2) AS valor
    FROM vista_ofertas_vigentes
    GROUP BY producto_id
    HAVING COUNT(*) > 1
) promedio ON promedio.producto_id = v.producto_id
WHERE v.precio_total_cop < promedio.valor
ORDER BY v.precio_total_cop;

\echo '4. ¿Cuánto bajó de precio cada oferta en su último cambio?'
SELECT titulo, tienda, precio_anterior, precio_nuevo, moneda, porcentaje_bajada, bajo_en
FROM vista_bajadas_precio
ORDER BY porcentaje_bajada DESC;

\echo '5. ¿Cuántas personas se registran por mes y qué edad promedio tienen?'
SELECT TO_CHAR(DATE_TRUNC('month', creado_en), 'YYYY-MM') AS mes,
       COUNT(*) AS registros,
       ROUND(AVG(EXTRACT(YEAR FROM AGE(fecha_nacimiento))), 1) AS edad_promedio
FROM usuario
WHERE rol = 'USUARIO'
GROUP BY DATE_TRUNC('month', creado_en)
ORDER BY mes;

\echo '6. ¿En qué ciudades hay más personas mayores de 60 años registradas?'
SELECT COALESCE(ciudad, 'Sin ciudad') AS ciudad,
       COUNT(*) AS personas
FROM usuario
WHERE rol = 'USUARIO'
  AND fecha_nacimiento <= CURRENT_DATE - INTERVAL '60 years'
GROUP BY COALESCE(ciudad, 'Sin ciudad')
ORDER BY personas DESC, ciudad;

\echo '7. ¿Qué personas registradas todavía no han hecho ninguna búsqueda?'
SELECT u.id, u.nombres, u.apellidos, u.correo, u.creado_en
FROM usuario u
WHERE u.rol = 'USUARIO'
  AND NOT EXISTS (SELECT 1 FROM busqueda b WHERE b.usuario_id = u.id)
ORDER BY u.creado_en;

\echo '8. ¿Quiénes han radicado más de una PQR y cuántas siguen sin respuesta?'
SELECT u.nombres || ' ' || u.apellidos AS persona,
       u.correo,
       COUNT(*) AS pqr_radicadas,
       COUNT(*) FILTER (WHERE q.estado <> 'RESPONDIDA') AS sin_respuesta
FROM pqr q
JOIN usuario u ON u.id = q.usuario_id
GROUP BY u.id, u.nombres, u.apellidos, u.correo
HAVING COUNT(*) > 1
ORDER BY sin_respuesta DESC, pqr_radicadas DESC;

\echo '9. ¿Qué PQR llevan más de 15 días sin respuesta?'
SELECT q.id AS radicado, q.tipo, q.asunto, q.creada_en,
       CURRENT_DATE - q.creada_en::DATE AS dias_abierta
FROM pqr q
WHERE q.estado <> 'RESPONDIDA'
  AND q.creada_en < now() - INTERVAL '15 days'
ORDER BY q.creada_en;

\echo '10. ¿Cómo están las PQR por tipo y estado?'
SELECT * FROM vista_resumen_pqr ORDER BY tipo, estado;

\echo '11. ¿Qué tan disponible está la API de cada tienda?'
SELECT * FROM vista_disponibilidad_api ORDER BY marketplace;
