-- Datos de prueba para la demostración de Marketradar.
--
-- No es una migración: se carga a mano, una sola vez, sobre una base de desarrollo que ya tenga las migraciones
-- V1 a V5. Nunca se carga en la base real.
--
--   psql -U marketradar -d marketradar -f datos-prueba/datos_demo.sql
--
-- Deja cinco filas o más en cada tabla. Las seis cuentas de prueba (correo *.demo@marketradar.co) tienen la
-- contraseña Demo2026* guardada como hash BCrypt, igual que las cuentas creadas desde la aplicación, así que se
-- puede ingresar con ellas. La cuenta admin.demo@marketradar.co entra a la interfaz de administrador.
--
-- Las fechas se calculan desde el momento de la carga, para que las consultas de "últimos 30 días" tengan datos.
-- Después de cargarlo, casos_prueba.sql muestra los tres casos que pide la rúbrica.

\set ON_ERROR_STOP on

SELECT EXISTS (SELECT 1 FROM oferta WHERE id_externo LIKE 'demo-%') AS ya_cargado \gset
\if :ya_cargado
    \echo 'Los datos de prueba ya estaban cargados. No se hizo ningún cambio.'
    \quit
\endif

BEGIN;

-- Tiendas activas para que se vean en la demostración, y dos tiendas candidatas sin ofertas todavía.
UPDATE marketplace SET activo = TRUE WHERE codigo IN ('aliexpress', 'shopify', 'mercadolibre');
INSERT INTO marketplace (codigo, nombre, sitio_web, activo) VALUES
    ('falabella', 'Falabella', 'https://www.falabella.com.co', FALSE),
    ('exito',     'Éxito',     'https://www.exito.com',        FALSE)
ON CONFLICT (codigo) DO NOTHING;

-- Tasas de cambio de los últimos días (valores de ejemplo).
INSERT INTO tasa_cambio (moneda_origen, moneda_destino, valor, vigente_desde, fuente) VALUES
    ('USD', 'COP', 4185.30, CURRENT_DATE - 2, 'Datos de prueba'),
    ('USD', 'COP', 4172.85, CURRENT_DATE - 1, 'Datos de prueba'),
    ('USD', 'COP', 4160.10, CURRENT_DATE,     'Datos de prueba'),
    ('EUR', 'COP', 4842.60, CURRENT_DATE - 1, 'Datos de prueba'),
    ('EUR', 'COP', 4831.95, CURRENT_DATE,     'Datos de prueba')
ON CONFLICT (moneda_origen, moneda_destino, vigente_desde) DO NOTHING;

-- Productos que suelen buscar las personas mayores.
CREATE TEMP TABLE demo_producto (clave TEXT PRIMARY KEY, nombre TEXT, marca TEXT) ON COMMIT DROP;
INSERT INTO demo_producto VALUES
    ('tensiometro', 'tensiometro digital de brazo', 'Omron'),
    ('glucometro',  'glucometro con 50 tiras',      'Accu-Chek'),
    ('olla',        'olla arrocera 1.8 litros',     'Oster'),
    ('celular',     'celular con teclas grandes',   'Alcatel'),
    ('lupa',        'lupa con luz led',             NULL),
    ('audifonos',   'audifonos bluetooth',          'JBL'),
    ('baston',      'baston plegable de aluminio',  NULL);

INSERT INTO producto (nombre_normalizado, marca)
SELECT nombre, marca FROM demo_producto ORDER BY clave;

-- Ofertas: el mismo producto en varias tiendas, con precios en pesos o en dólares.
CREATE TEMP TABLE demo_oferta (
    id_externo TEXT, producto TEXT, tienda TEXT, titulo TEXT, precio NUMERIC, moneda CHAR(3),
    envio NUMERIC, condicion TEXT, vendedor TEXT, calificacion NUMERIC, resenas INTEGER
) ON COMMIT DROP;
INSERT INTO demo_oferta VALUES
    ('demo-ml-001',  'tensiometro', 'mercadolibre', 'Tensiómetro Digital De Brazo Omron HEM-7120',        189900, 'COP',     0, 'nuevo', 'Droguería Salud Total',   4.8, 1250),
    ('demo-ali-001', 'tensiometro', 'aliexpress',   'Tensiómetro digital de brazo con pantalla grande',     24.50, 'USD',  3.99, 'nuevo', 'Health Store Official',   4.6,  830),
    ('demo-sh-001',  'tensiometro', 'shopify',      'Tensiómetro Omron HEM-7120 original',                 199000, 'COP',  9900, 'nuevo', 'Tienda Bienestar',        4.9,   42),
    ('demo-ml-002',  'glucometro',  'mercadolibre', 'Glucómetro Accu-Chek Instant + 50 tiras',             145000, 'COP',  8500, 'nuevo', 'Farmacia Express',        4.7,  610),
    ('demo-sh-002',  'glucometro',  'shopify',      'Kit glucómetro Accu-Chek con 50 tiras',               139900, 'COP', 12000, 'nuevo', 'Tienda Bienestar',        4.5,   18),
    ('demo-ml-003',  'olla',        'mercadolibre', 'Olla Arrocera Oster 1.8 L 10 tazas',                  159900, 'COP',     0, 'nuevo', 'Electro Hogar',           4.6,  980),
    ('demo-ml-004',  'olla',        'mercadolibre', 'Olla Arrocera Oster 1.8 L (reacondicionada)',          99900, 'COP',     0, 'reacondicionado', 'Outlet Hogar', 4.1,  57),
    ('demo-ali-003', 'olla',        'aliexpress',   'Olla arrocera eléctrica 1.8 L antiadherente',          29.90, 'USD', 12.50, 'nuevo', 'Kitchen World',           4.3,  410),
    ('demo-ml-005',  'celular',     'mercadolibre', 'Celular Alcatel 2019G teclas grandes botón SOS',      219000, 'COP',     0, 'nuevo', 'Tecno Mayor',             4.4,  320),
    ('demo-ali-005', 'celular',     'aliexpress',   'Teléfono para adultos mayores con teclas grandes',     38.00, 'USD',  5.00, 'nuevo', 'Senior Phone Store',      4.2,  150),
    ('demo-ali-006', 'lupa',        'aliexpress',   'Lupa de mano 3X con 12 luces LED',                      6.80, 'USD',  1.20, 'nuevo', 'Optic Tools',             4.7, 2100),
    ('demo-ml-006',  'lupa',        'mercadolibre', 'Lupa Con Luz Led Para Leer 5x',                        35900, 'COP',  6900, 'nuevo', 'Papelería Central',       4.5,  240),
    ('demo-ml-007',  'audifonos',   'mercadolibre', 'Audífonos JBL Tune 520BT Bluetooth',                  229900, 'COP',     0, 'nuevo', 'JBL Tienda Oficial',      4.8, 3400),
    ('demo-sh-007',  'audifonos',   'shopify',      'Audífonos inalámbricos JBL Tune 520BT',               215000, 'COP', 15000, 'nuevo', 'Sonido Store',            4.6,   75),
    ('demo-ali-007', 'audifonos',   'aliexpress',   'Audífonos bluetooth plegables con micrófono',          18.90, 'USD',  0.00, 'usado', 'Audio Express',           3.9,   64);

INSERT INTO oferta (producto_id, marketplace_id, id_externo, titulo, url_destino, url_imagen, precio_actual, moneda,
                    costo_envio, condicion, vendedor, calificacion, total_resenas, consultado_en)
SELECT p.id, m.id, d.id_externo, d.titulo,
       m.sitio_web || '/demo/' || d.id_externo, NULL,
       d.precio, d.moneda, d.envio, d.condicion, d.vendedor, d.calificacion, d.resenas,
       now() - INTERVAL '10 minutes'
FROM demo_oferta d
JOIN demo_producto dp ON dp.clave = d.producto
JOIN producto p ON p.nombre_normalizado = dp.nombre
JOIN marketplace m ON m.codigo = d.tienda;

-- Historial de precios: cuatro registros por oferta en los últimos 30 días. El último es el precio actual.
-- Algunas ofertas bajan de precio al final, otras suben y otras se mantienen.
INSERT INTO historial_precio (oferta_id, precio, moneda, registrado_en)
SELECT o.id,
       ROUND(o.precio_actual * f.factor, CASE WHEN o.moneda = 'COP' THEN -2 ELSE 2 END),
       o.moneda,
       now() - f.hace
FROM oferta o
CROSS JOIN LATERAL (VALUES
    (CASE WHEN o.id % 3 = 0 THEN 1.12 WHEN o.id % 3 = 1 THEN 0.95 ELSE 1.00 END, INTERVAL '30 days'),
    (CASE WHEN o.id % 3 = 0 THEN 1.10 WHEN o.id % 3 = 1 THEN 0.97 ELSE 1.00 END, INTERVAL '20 days'),
    (CASE WHEN o.id % 3 = 0 THEN 1.08 WHEN o.id % 3 = 1 THEN 0.98 ELSE 1.00 END, INTERVAL '7 days'),
    (1.00, INTERVAL '10 minutes')
) AS f(factor, hace)
WHERE o.id_externo LIKE 'demo-%';

-- Búsquedas sin usuario (por ejemplo, antes de iniciar sesión o de una cuenta borrada).
INSERT INTO busqueda (termino, total_resultados, duracion_ms, creada_en, usuario_id)
SELECT t.termino, t.resultados, t.duracion, now() - t.hace, NULL
FROM (VALUES
    ('tensiometro',        3, 1840, INTERVAL '25 days'),
    ('olla arrocera',      3, 2100, INTERVAL '12 days'),
    ('lupa',               2,  950, INTERVAL '5 days'),
    ('silla de ruedas',    0, 1320, INTERVAL '3 days'),
    ('audifonos',          3, 1600, INTERVAL '1 day')
) AS t(termino, resultados, duracion, hace);

-- Consumo de las APIs de las tiendas durante la última semana.
INSERT INTO consumo_api (marketplace_id, exitoso, codigo_http, duracion_ms, registrado_en)
SELECT m.id,
       NOT (g % c.falla_cada = 0),
       CASE WHEN g % c.falla_cada = 0 THEN c.codigo_falla ELSE 200 END,
       c.base_ms + (g * 37) % 900,
       now() - (g * INTERVAL '4 hours')
FROM (VALUES
    ('mercadolibre', 12, 429,  350),
    ('aliexpress',    8, 503,  900),
    ('shopify',      20, 500,  600)
) AS c(codigo, falla_cada, codigo_falla, base_ms)
JOIN marketplace m ON m.codigo = c.codigo
CROSS JOIN generate_series(1, 40) AS g;

-- Cuentas de prueba. Contraseña de todas: Demo2026* (hash BCrypt generado con el mismo codificador del backend).
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, tipo_documento, numero_documento,
                     fecha_nacimiento, celular, ciudad, creado_en)
SELECT t.correo, '$2a$10$9IJhs1xuGKqoPW8RONVlDuLBY9JvtwYqZ.vImXyaVWslQAmFkWm8K', t.rol, t.nombres, t.apellidos,
       t.tipo, t.numero, t.nacimiento, t.celular, t.ciudad, now() - t.hace
FROM (VALUES
    ('admin.demo@marketradar.co',  'ADMINISTRADOR', 'Andrea',      'Gómez',    'CC', '1065123456', DATE '1990-07-21', '3012345678', 'Valledupar',   INTERVAL '40 days'),
    ('rosa.demo@marketradar.co',   'USUARIO',       'Rosa Elena',  'Díaz',     'CC', '26754321',   DATE '1952-04-18', '3001234567', 'Valledupar',   INTERVAL '35 days'),
    ('jorge.demo@marketradar.co',  'USUARIO',       'Jorge',       'Pinto',    'CC', '17123987',   DATE '1958-11-02', '3157654321', 'Barranquilla', INTERVAL '30 days'),
    ('carmen.demo@marketradar.co', 'USUARIO',       'Carmen',      'Ospino',   'CE', '456789',     DATE '1949-02-10', '3204567890', 'Santa Marta',  INTERVAL '21 days'),
    ('luis.demo@marketradar.co',   'USUARIO',       'Luis Alberto','Maestre',  'PA', 'AB123456',   DATE '1961-09-30', NULL,         'Bogotá',       INTERVAL '12 days'),
    ('beatriz.demo@marketradar.co','USUARIO',       'Beatriz',     'Fuentes',  NULL, NULL,         DATE '1955-12-05', NULL,         'Valledupar',   INTERVAL '2 days')
) AS t(correo, rol, nombres, apellidos, tipo, numero, nacimiento, celular, ciudad, hace)
ON CONFLICT (correo) DO NOTHING;

INSERT INTO busqueda (termino, total_resultados, duracion_ms, creada_en, usuario_id)
SELECT t.termino, t.resultados, t.duracion, now() - t.hace, u.id
FROM (VALUES
    ('rosa.demo@marketradar.co',   'tensiometro',            3, 1720, INTERVAL '20 days'),
    ('rosa.demo@marketradar.co',   'glucometro',             2, 1480, INTERVAL '9 days'),
    ('rosa.demo@marketradar.co',   'tensiometro',            3, 1390, INTERVAL '2 days'),
    ('jorge.demo@marketradar.co',  'celular teclas grandes', 2, 2050, INTERVAL '6 days'),
    ('jorge.demo@marketradar.co',  'olla arrocera',          3, 1930, INTERVAL '4 hours'),
    ('carmen.demo@marketradar.co', 'lupa con luz',           2, 1100, INTERVAL '8 days'),
    ('luis.demo@marketradar.co',   'audifonos',              3, 1540, INTERVAL '3 days')
) AS t(correo, termino, resultados, duracion, hace)
JOIN usuario u ON u.correo = t.correo;

-- PQR en los tres estados. Las respondidas las contesta la cuenta de administrador de prueba.
INSERT INTO pqr (usuario_id, tipo, asunto, descripcion, estado, respuesta, respondida_por, creada_en, respondida_en)
SELECT u.id, t.tipo, t.asunto, t.descripcion, t.estado, t.respuesta,
       CASE WHEN t.respuesta IS NULL THEN NULL ELSE a.id END,
       now() - t.hace,
       CASE WHEN t.estado = 'RESPONDIDA' THEN now() - t.hace + INTERVAL '26 hours' END
FROM (VALUES
    ('rosa.demo@marketradar.co', 'PETICION', 'Letra más grande en los resultados',
     'Me gustaría que el precio se viera todavía más grande en el celular.', 'RESPONDIDA',
     'Gracias. Puede agrandar la letra con el botón A+ en la parte de arriba.', INTERVAL '18 days'),
    ('rosa.demo@marketradar.co', 'QUEJA', 'El precio cambió en la tienda',
     'El tensiómetro salía más barato aquí, pero en la tienda tenía otro precio.', 'EN_PROCESO',
     NULL, INTERVAL '3 days'),
    ('jorge.demo@marketradar.co', 'SUGERENCIA', 'Agregar más tiendas',
     'Sería bueno poder comparar también con almacenes de cadena de Colombia.', 'RADICADA',
     NULL, INTERVAL '20 days'),
    ('carmen.demo@marketradar.co', 'RECLAMO', 'El enlace no abría la tienda',
     'Al tocar el botón para ir a comprar la lupa, la página quedó en blanco.', 'RESPONDIDA',
     'Ya corregimos el enlace. Gracias por avisarnos.', INTERVAL '10 days'),
    ('luis.demo@marketradar.co', 'PETICION', 'Avisos por correo',
     'Quisiera recibir un correo cuando baje el precio de los audífonos que sigo.', 'RADICADA',
     NULL, INTERVAL '1 day')
) AS t(correo, tipo, asunto, descripcion, estado, respuesta, hace)
JOIN usuario u ON u.correo = t.correo
CROSS JOIN (SELECT id FROM usuario WHERE correo = 'admin.demo@marketradar.co') a;

-- Ofertas que sigue cada persona (relación muchos a muchos). Rosa sigue varias ofertas, el tensiómetro de
-- Mercado Libre lo siguen varias personas y Beatriz no sigue ninguna.
INSERT INTO seguimiento_oferta (usuario_id, oferta_id, precio_objetivo, creado_en)
SELECT u.id, o.id, t.objetivo, now() - t.hace
FROM (VALUES
    ('rosa.demo@marketradar.co',   'demo-ml-001',  180000, INTERVAL '19 days'),
    ('rosa.demo@marketradar.co',   'demo-ml-002',    NULL, INTERVAL '9 days'),
    ('rosa.demo@marketradar.co',   'demo-sh-002',  130000, INTERVAL '9 days'),
    ('jorge.demo@marketradar.co',  'demo-ml-001',    NULL, INTERVAL '15 days'),
    ('jorge.demo@marketradar.co',  'demo-ml-005',  200000, INTERVAL '6 days'),
    ('carmen.demo@marketradar.co', 'demo-ml-001',  175000, INTERVAL '7 days'),
    ('carmen.demo@marketradar.co', 'demo-ali-006',   NULL, INTERVAL '8 days'),
    ('luis.demo@marketradar.co',   'demo-ml-007',  210000, INTERVAL '3 days')
) AS t(correo, id_externo, objetivo, hace)
JOIN usuario u ON u.correo = t.correo
JOIN oferta o ON o.id_externo = t.id_externo
ON CONFLICT (usuario_id, oferta_id) DO NOTHING;

COMMIT;

SELECT (SELECT COUNT(*) FROM marketplace)                                AS tiendas,
       (SELECT COUNT(*) FROM producto)                                   AS productos,
       (SELECT COUNT(*) FROM oferta WHERE id_externo LIKE 'demo-%')      AS ofertas,
       (SELECT COUNT(*) FROM historial_precio)                           AS precios_historicos,
       (SELECT COUNT(*) FROM busqueda)                                   AS busquedas,
       (SELECT COUNT(*) FROM usuario)                                    AS usuarios,
       (SELECT COUNT(*) FROM pqr)                                        AS pqr,
       (SELECT COUNT(*) FROM seguimiento_oferta)                         AS seguimientos,
       (SELECT COUNT(*) FROM tasa_cambio)                                AS tasas,
       (SELECT COUNT(*) FROM consumo_api)                                AS consultas_api;
\echo 'Datos de prueba cargados.'
