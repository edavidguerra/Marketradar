-- Casos de prueba de la base de datos. Se ejecuta después de datos_demo.sql:
--
--   psql -U marketradar -d marketradar -f datos-prueba/casos_prueba.sql
--
-- Muestra los tres casos: una fila que se relaciona con varias, una que no se relaciona con ninguna y el mensaje
-- del servidor cuando rechaza un dato. Los intentos de la parte 3 fallan a propósito y no cambian nada, porque cada
-- uno va dentro de una transacción que se deshace.

\set ON_ERROR_STOP off

\echo ''
\echo '=== Caso 1: filas que se relacionan con varias ==='

\echo '1a. La oferta del tensiómetro en Mercado Libre la siguen varias personas (muchos a muchos):'
SELECT o.titulo, u.nombres || ' ' || u.apellidos AS persona, s.precio_objetivo
FROM seguimiento_oferta s
JOIN oferta o ON o.id = s.oferta_id
JOIN usuario u ON u.id = s.usuario_id
WHERE o.id_externo = 'demo-ml-001'
ORDER BY persona;

\echo '1b. Rosa sigue varias ofertas (el otro lado de la misma relación):'
SELECT u.nombres, o.titulo, m.nombre AS tienda
FROM seguimiento_oferta s
JOIN usuario u ON u.id = s.usuario_id
JOIN oferta o ON o.id = s.oferta_id
JOIN marketplace m ON m.id = o.marketplace_id
WHERE u.correo = 'rosa.demo@marketradar.co'
ORDER BY o.titulo;

\echo '1c. Cada producto con varias ofertas en distintas tiendas:'
SELECT p.nombre_normalizado AS producto, COUNT(o.id) AS ofertas, STRING_AGG(DISTINCT m.nombre, ', ') AS tiendas
FROM producto p
JOIN oferta o ON o.producto_id = p.id
JOIN marketplace m ON m.id = o.marketplace_id
GROUP BY p.nombre_normalizado
HAVING COUNT(o.id) > 1
ORDER BY ofertas DESC, producto;

\echo ''
\echo '=== Caso 2: filas que no se relacionan con ninguna ==='

\echo '2a. Personas que no siguen ninguna oferta:'
SELECT u.nombres, u.apellidos, u.correo
FROM usuario u
LEFT JOIN seguimiento_oferta s ON s.usuario_id = u.id
WHERE u.rol = 'USUARIO' AND s.usuario_id IS NULL;

\echo '2b. Productos sin ninguna oferta:'
SELECT p.nombre_normalizado
FROM producto p
WHERE NOT EXISTS (SELECT 1 FROM oferta o WHERE o.producto_id = p.id);

\echo '2c. Tiendas sin ninguna oferta:'
SELECT m.codigo, m.nombre
FROM marketplace m
LEFT JOIN oferta o ON o.marketplace_id = m.id
GROUP BY m.id, m.codigo, m.nombre
HAVING COUNT(o.id) = 0;

\echo ''
\echo '=== Caso 3: el servidor rechaza datos que rompen una regla ==='

\echo '3a. Precio negativo (ck_oferta_precio_no_negativo):'
BEGIN;
UPDATE oferta SET precio_actual = -1000 WHERE id_externo = 'demo-ml-001';
ROLLBACK;

\echo '3b. PQR con asunto de menos de 5 letras (ck_pqr_asunto_longitud):'
BEGIN;
INSERT INTO pqr (usuario_id, tipo, asunto, descripcion)
SELECT id, 'QUEJA', 'Mal', 'La descripción tiene más de veinte caracteres.' FROM usuario
WHERE correo = 'beatriz.demo@marketradar.co';
ROLLBACK;

\echo '3c. Celular que no es colombiano (ck_usuario_celular_colombiano):'
BEGIN;
UPDATE usuario SET celular = '6055712345' WHERE correo = 'beatriz.demo@marketradar.co';
ROLLBACK;

\echo '3d. Correo repetido (uq_usuario_correo):'
BEGIN;
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos)
VALUES ('rosa.demo@marketradar.co', 'x', 'USUARIO', 'Otra', 'Rosa');
ROLLBACK;

\echo '3e. Seguir dos veces la misma oferta (pk_seguimiento_oferta):'
BEGIN;
INSERT INTO seguimiento_oferta (usuario_id, oferta_id)
SELECT s.usuario_id, s.oferta_id FROM seguimiento_oferta s LIMIT 1;
ROLLBACK;

\echo '3f. Seguir una oferta que no existe (fk_seguimiento_oferta):'
BEGIN;
INSERT INTO seguimiento_oferta (usuario_id, oferta_id)
SELECT id, 999999 FROM usuario WHERE correo = 'beatriz.demo@marketradar.co';
ROLLBACK;

\echo '3g. Borrar una tienda que tiene ofertas (fk_oferta_marketplace, ON DELETE RESTRICT):'
BEGIN;
DELETE FROM marketplace WHERE codigo = 'mercadolibre';
ROLLBACK;

\echo '3h. Borrar una persona que radicó PQR (fk_pqr_usuario, ON DELETE RESTRICT):'
BEGIN;
DELETE FROM usuario WHERE correo = 'rosa.demo@marketradar.co';
ROLLBACK;

\echo '3i. Marcar como respondida una PQR sin respuesta (ck_pqr_respuesta_completa):'
BEGIN;
UPDATE pqr SET estado = 'RESPONDIDA' WHERE estado = 'RADICADA';
ROLLBACK;

\echo ''
\echo '=== Acción en cascada: al borrar una oferta se borran su historial y sus seguimientos ==='
BEGIN;
SELECT (SELECT COUNT(*) FROM historial_precio h JOIN oferta o ON o.id = h.oferta_id WHERE o.id_externo = 'demo-ml-001') AS historial_antes,
       (SELECT COUNT(*) FROM seguimiento_oferta s JOIN oferta o ON o.id = s.oferta_id WHERE o.id_externo = 'demo-ml-001') AS seguimientos_antes;
DELETE FROM oferta WHERE id_externo = 'demo-ml-001';
SELECT (SELECT COUNT(*) FROM historial_precio h WHERE NOT EXISTS (SELECT 1 FROM oferta o WHERE o.id = h.oferta_id)) AS historial_huerfano,
       (SELECT COUNT(*) FROM seguimiento_oferta s WHERE NOT EXISTS (SELECT 1 FROM oferta o WHERE o.id = s.oferta_id)) AS seguimientos_huerfanos;
ROLLBACK;
\echo 'Se deshizo el borrado: los datos de prueba quedan como estaban.'
