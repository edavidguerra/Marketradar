-- ============================================================================================
-- Marketradar: INSERT con datos erróneos para comprobar que la base de datos los rechaza.
-- Requisitos: migraciones V1 a V5 y datos_demo.sql cargados.
--
-- Cada INSERT DEBE fallar. El comentario de arriba dice qué regla lo rechaza.
-- Todos fueron probados y fallan, así que no guardan nada. La parte 2 además termina con ROLLBACK.
--
-- PARTE 1 (pgAdmin): seleccione UNA línea INSERT con el mouse y pulse F5. Abajo, en "Messages",
--          aparece el error con el nombre de la restricción. Así se muestra cada caso al profesor.
-- PARTE 2 (al final): un bloque que prueba los 45 casos de una vez y muestra el resumen.
-- ============================================================================================
-- PARTE 1: un INSERT por caso

-- ---------- usuario ----------
-- 1. Correo sin arroba. Debe fallar por: ck_usuario_correo_formato
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos) VALUES ('correo-sin-arroba.com', 'hash', 'USUARIO', 'Ana', 'Pérez');
-- 2. Correo repetido. Debe fallar por: uq_usuario_correo
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos) VALUES ('rosa.demo@marketradar.co', 'hash', 'USUARIO', 'Rosa', 'Copia');
-- 3. Rol que no existe. Debe fallar por: ck_usuario_rol_valido
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos) VALUES ('ana.rol@prueba.co', 'hash', 'SUPERUSUARIO', 'Ana', 'Pérez');
-- 4. Celular que no empieza por 3. Debe fallar por: ck_usuario_celular_colombiano
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, celular) VALUES ('ana.cel@prueba.co', 'hash', 'USUARIO', 'Ana', 'Pérez', '6015551234');
-- 5. Celular con letras. Debe fallar por: ck_usuario_celular_colombiano
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, celular) VALUES ('ana.cel2@prueba.co', 'hash', 'USUARIO', 'Ana', 'Pérez', '30012345AB');
-- 6. Tipo de documento sin número. Debe fallar por: ck_usuario_documento_completo
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, tipo_documento) VALUES ('ana.doc@prueba.co', 'hash', 'USUARIO', 'Ana', 'Pérez', 'CC');
-- 7. Tipo de documento inválido (TI). Debe fallar por: ck_usuario_numero_documento_formato (solo acepta CC, CE o PA)
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, tipo_documento, numero_documento) VALUES ('ana.ti@prueba.co', 'hash', 'USUARIO', 'Ana', 'Pérez', 'TI', '1065123456');
-- 8. Cédula con letras. Debe fallar por: ck_usuario_numero_documento_formato
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, tipo_documento, numero_documento) VALUES ('ana.cc@prueba.co', 'hash', 'USUARIO', 'Ana', 'Pérez', 'CC', '10AB3456');
-- 9. Documento repetido. Debe fallar por: uq_usuario_documento
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, tipo_documento, numero_documento) VALUES ('ana.dup@prueba.co', 'hash', 'USUARIO', 'Ana', 'Pérez', 'CC', '26754321');
-- 10. Nacimiento antes de 1900. Debe fallar por: ck_usuario_nacimiento_razonable
INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, fecha_nacimiento) VALUES ('ana.fecha@prueba.co', 'hash', 'USUARIO', 'Ana', 'Pérez', '1850-05-10');
-- 11. Sin nombres (campo obligatorio). Debe fallar por: NOT NULL en nombres
INSERT INTO usuario (correo, clave_hash, rol, apellidos) VALUES ('ana.nombre@prueba.co', 'hash', 'USUARIO', 'Pérez');

-- ---------- marketplace ----------
-- 12. Código con mayúsculas y espacios. Debe fallar por: ck_marketplace_codigo_formato
INSERT INTO marketplace (codigo, nombre, sitio_web) VALUES ('Tienda Nueva', 'Tienda Nueva', 'https://tienda.co');
-- 13. Sitio web sin https. Debe fallar por: ck_marketplace_sitio_web_https
INSERT INTO marketplace (codigo, nombre, sitio_web) VALUES ('tienda_http', 'Tienda HTTP', 'http://tienda.co');
-- 14. Código repetido. Debe fallar por: uq_marketplace_codigo
INSERT INTO marketplace (codigo, nombre, sitio_web) VALUES ('mercadolibre', 'Otra Mercado Libre', 'https://otra.co');

-- ---------- oferta ----------
-- 15. Precio negativo. Debe fallar por: ck_oferta_precio_no_negativo
INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual) VALUES ((SELECT id FROM marketplace WHERE codigo = 'mercadolibre'), 'err-001', 'Olla', 'https://tienda.co/olla', -5000);
-- 16. Moneda que no es código ISO. Debe fallar por: ck_oferta_moneda_iso
INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual, moneda) VALUES ((SELECT id FROM marketplace WHERE codigo = 'mercadolibre'), 'err-002', 'Olla', 'https://tienda.co/olla', 5000, 'cop');
-- 17. Enlace que no es una dirección web. Debe fallar por: ck_oferta_url_destino_web
INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual) VALUES ((SELECT id FROM marketplace WHERE codigo = 'mercadolibre'), 'err-003', 'Olla', 'www.tienda.co/olla', 5000);
-- 18. Calificación de 7 estrellas. Debe fallar por: ck_oferta_calificacion_0_a_5
INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual, calificacion) VALUES ((SELECT id FROM marketplace WHERE codigo = 'mercadolibre'), 'err-004', 'Olla', 'https://tienda.co/olla', 5000, 7);
-- 19. Reseñas negativas. Debe fallar por: ck_oferta_resenas_no_negativas
INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual, total_resenas) VALUES ((SELECT id FROM marketplace WHERE codigo = 'mercadolibre'), 'err-005', 'Olla', 'https://tienda.co/olla', 5000, -3);
-- 20. Envío negativo. Debe fallar por: ck_oferta_envio_no_negativo
INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual, costo_envio) VALUES ((SELECT id FROM marketplace WHERE codigo = 'mercadolibre'), 'err-006', 'Olla', 'https://tienda.co/olla', 5000, -100);
-- 21. Tienda que no existe. Debe fallar por: fk_oferta_marketplace
INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual) VALUES (999, 'err-007', 'Olla', 'https://tienda.co/olla', 5000);
-- 22. Misma publicación dos veces en la misma tienda. Debe fallar por: uq_oferta_publicacion_por_tienda
INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual) VALUES ((SELECT id FROM marketplace WHERE codigo = 'mercadolibre'), 'demo-ml-001', 'Copia', 'https://tienda.co/copia', 5000);

-- ---------- historial_precio ----------
-- 23. Oferta que no existe. Debe fallar por: fk_historial_oferta
INSERT INTO historial_precio (oferta_id, precio, moneda) VALUES (999999, 5000, 'COP');
-- 24. Precio negativo. Debe fallar por: ck_historial_precio_no_negativo
INSERT INTO historial_precio (oferta_id, precio, moneda) VALUES ((SELECT id FROM oferta WHERE id_externo = 'demo-ml-001'), -1, 'COP');
-- 25. Moneda inválida. Debe fallar por: ck_historial_moneda_iso
INSERT INTO historial_precio (oferta_id, precio, moneda) VALUES ((SELECT id FROM oferta WHERE id_externo = 'demo-ml-001'), 5000, '12$');

-- ---------- busqueda ----------
-- 26. Término de una letra. Debe fallar por: ck_busqueda_termino_longitud
INSERT INTO busqueda (termino, total_resultados, duracion_ms) VALUES ('a', 0, 120);
-- 27. Resultados negativos. Debe fallar por: ck_busqueda_resultados_no_negativos
INSERT INTO busqueda (termino, total_resultados, duracion_ms) VALUES ('olla', -2, 120);
-- 28. Duración negativa. Debe fallar por: ck_busqueda_duracion_no_negativa
INSERT INTO busqueda (termino, total_resultados, duracion_ms) VALUES ('olla', 3, -50);
-- 29. Usuario que no existe. Debe fallar por: fk_busqueda_usuario
INSERT INTO busqueda (termino, total_resultados, duracion_ms, usuario_id) VALUES ('olla', 3, 120, 999999);

-- ---------- consumo_api ----------
-- 30. Código HTTP 999. Debe fallar por: ck_consumo_codigo_http_valido
INSERT INTO consumo_api (marketplace_id, exitoso, codigo_http, duracion_ms) VALUES ((SELECT id FROM marketplace WHERE codigo = 'mercadolibre'), false, 999, 300);
-- 31. Duración negativa. Debe fallar por: ck_consumo_duracion_no_negativa
INSERT INTO consumo_api (marketplace_id, exitoso, codigo_http, duracion_ms) VALUES ((SELECT id FROM marketplace WHERE codigo = 'mercadolibre'), true, 200, -1);

-- ---------- pqr ----------
-- 32. Tipo que no existe. Debe fallar por: ck_pqr_tipo_valido
INSERT INTO pqr (usuario_id, tipo, asunto, descripcion) VALUES ((SELECT id FROM usuario WHERE correo = 'rosa.demo@marketradar.co'), 'FELICITACION', 'Muy buena', 'La aplicación me ayudó a comparar precios.');
-- 33. Asunto muy corto. Debe fallar por: ck_pqr_asunto_longitud
INSERT INTO pqr (usuario_id, tipo, asunto, descripcion) VALUES ((SELECT id FROM usuario WHERE correo = 'rosa.demo@marketradar.co'), 'QUEJA', 'Mal', 'La búsqueda tardó mucho en responder hoy.');
-- 34. Descripción muy corta. Debe fallar por: ck_pqr_descripcion_longitud
INSERT INTO pqr (usuario_id, tipo, asunto, descripcion) VALUES ((SELECT id FROM usuario WHERE correo = 'rosa.demo@marketradar.co'), 'QUEJA', 'Búsqueda lenta', 'Muy lenta.');
-- 35. Estado que no existe. Debe fallar por: ck_pqr_estado_valido
INSERT INTO pqr (usuario_id, tipo, asunto, descripcion, estado) VALUES ((SELECT id FROM usuario WHERE correo = 'rosa.demo@marketradar.co'), 'QUEJA', 'Búsqueda lenta', 'La búsqueda tardó mucho en responder hoy.', 'CERRADA');
-- 36. Respondida sin respuesta. Debe fallar por: ck_pqr_respuesta_completa
INSERT INTO pqr (usuario_id, tipo, asunto, descripcion, estado) VALUES ((SELECT id FROM usuario WHERE correo = 'rosa.demo@marketradar.co'), 'QUEJA', 'Búsqueda lenta', 'La búsqueda tardó mucho en responder hoy.', 'RESPONDIDA');
-- 37. Respondida antes de crearse. Debe fallar por: ck_pqr_respuesta_posterior
INSERT INTO pqr (usuario_id, tipo, asunto, descripcion, estado, respuesta, creada_en, respondida_en) VALUES ((SELECT id FROM usuario WHERE correo = 'rosa.demo@marketradar.co'), 'QUEJA', 'Búsqueda lenta', 'La búsqueda tardó mucho en responder hoy.', 'RESPONDIDA', 'Ya quedó revisado.', '2026-10-30', '2026-10-01');
-- 38. Usuario que no existe. Debe fallar por: fk_pqr_usuario
INSERT INTO pqr (usuario_id, tipo, asunto, descripcion) VALUES (999999, 'QUEJA', 'Búsqueda lenta', 'La búsqueda tardó mucho en responder hoy.');

-- ---------- tasa_cambio ----------
-- 39. Valor cero. Debe fallar por: ck_tasa_cambio_valor_positivo
INSERT INTO tasa_cambio (moneda_origen, moneda_destino, valor, vigente_desde, fuente) VALUES ('USD', 'COP', 0, '2026-10-30', 'prueba');
-- 40. Misma moneda de origen y destino. Debe fallar por: ck_tasa_cambio_monedas_distintas
INSERT INTO tasa_cambio (moneda_origen, moneda_destino, valor, vigente_desde, fuente) VALUES ('COP', 'COP', 1, '2026-10-30', 'prueba');
-- 41. Moneda en minúsculas. Debe fallar por: ck_tasa_cambio_monedas_iso
INSERT INTO tasa_cambio (moneda_origen, moneda_destino, valor, vigente_desde, fuente) VALUES ('usd', 'COP', 4000, '2026-10-30', 'prueba');
-- 42. Misma tasa el mismo día. Debe fallar por: uq_tasa_cambio_moneda_fecha
INSERT INTO tasa_cambio (moneda_origen, moneda_destino, valor, vigente_desde, fuente) VALUES ('USD', 'COP', 4100, '2026-10-28', 'prueba');

-- ---------- seguimiento_oferta ----------
-- 43. Precio objetivo negativo. Debe fallar por: ck_seguimiento_precio_objetivo_positivo
INSERT INTO seguimiento_oferta (usuario_id, oferta_id, precio_objetivo) VALUES ((SELECT id FROM usuario WHERE correo = 'rosa.demo@marketradar.co'), (SELECT id FROM oferta WHERE id_externo = 'demo-ml-001'), -10);
-- 44. Seguir la misma oferta dos veces. Debe fallar por: pk_seguimiento_oferta
INSERT INTO seguimiento_oferta (usuario_id, oferta_id) SELECT usuario_id, oferta_id FROM seguimiento_oferta LIMIT 1;
-- 45. Oferta que no existe. Debe fallar por: fk_seguimiento_oferta
INSERT INTO seguimiento_oferta (usuario_id, oferta_id) VALUES ((SELECT id FROM usuario WHERE correo = 'rosa.demo@marketradar.co'), 999999);


-- ============================================================================================
-- PARTE 2: todos los casos de una vez. Seleccione desde BEGIN hasta ROLLBACK y pulse F5.
-- En "Messages" sale una línea por caso: RECHAZADO (bien) o ACEPTADO (la regla falta).
-- ============================================================================================
BEGIN;
DO $$
DECLARE
    casos text[][] := ARRAY[
        ARRAY['1. usuario: Correo sin arroba', 'INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos) VALUES (''correo-sin-arroba.com'', ''hash'', ''USUARIO'', ''Ana'', ''Pérez'');'],
        ARRAY['2. usuario: Correo repetido', 'INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos) VALUES (''rosa.demo@marketradar.co'', ''hash'', ''USUARIO'', ''Rosa'', ''Copia'');'],
        ARRAY['3. usuario: Rol que no existe', 'INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos) VALUES (''ana.rol@prueba.co'', ''hash'', ''SUPERUSUARIO'', ''Ana'', ''Pérez'');'],
        ARRAY['4. usuario: Celular que no empieza por 3', 'INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, celular) VALUES (''ana.cel@prueba.co'', ''hash'', ''USUARIO'', ''Ana'', ''Pérez'', ''6015551234'');'],
        ARRAY['5. usuario: Celular con letras', 'INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, celular) VALUES (''ana.cel2@prueba.co'', ''hash'', ''USUARIO'', ''Ana'', ''Pérez'', ''30012345AB'');'],
        ARRAY['6. usuario: Tipo de documento sin número', 'INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, tipo_documento) VALUES (''ana.doc@prueba.co'', ''hash'', ''USUARIO'', ''Ana'', ''Pérez'', ''CC'');'],
        ARRAY['7. usuario: Tipo de documento inválido', 'INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, tipo_documento, numero_documento) VALUES (''ana.ti@prueba.co'', ''hash'', ''USUARIO'', ''Ana'', ''Pérez'', ''TI'', ''1065123456'');'],
        ARRAY['8. usuario: Cédula con letras', 'INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, tipo_documento, numero_documento) VALUES (''ana.cc@prueba.co'', ''hash'', ''USUARIO'', ''Ana'', ''Pérez'', ''CC'', ''10AB3456'');'],
        ARRAY['9. usuario: Documento repetido', 'INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, tipo_documento, numero_documento) VALUES (''ana.dup@prueba.co'', ''hash'', ''USUARIO'', ''Ana'', ''Pérez'', ''CC'', ''26754321'');'],
        ARRAY['10. usuario: Nacimiento antes de 1900', 'INSERT INTO usuario (correo, clave_hash, rol, nombres, apellidos, fecha_nacimiento) VALUES (''ana.fecha@prueba.co'', ''hash'', ''USUARIO'', ''Ana'', ''Pérez'', ''1850-05-10'');'],
        ARRAY['11. usuario: Sin nombres (campo obligatorio)', 'INSERT INTO usuario (correo, clave_hash, rol, apellidos) VALUES (''ana.nombre@prueba.co'', ''hash'', ''USUARIO'', ''Pérez'');'],
        ARRAY['12. marketplace: Código con mayúsculas y espacios', 'INSERT INTO marketplace (codigo, nombre, sitio_web) VALUES (''Tienda Nueva'', ''Tienda Nueva'', ''https://tienda.co'');'],
        ARRAY['13. marketplace: Sitio web sin https', 'INSERT INTO marketplace (codigo, nombre, sitio_web) VALUES (''tienda_http'', ''Tienda HTTP'', ''http://tienda.co'');'],
        ARRAY['14. marketplace: Código repetido', 'INSERT INTO marketplace (codigo, nombre, sitio_web) VALUES (''mercadolibre'', ''Otra Mercado Libre'', ''https://otra.co'');'],
        ARRAY['15. oferta: Precio negativo', 'INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual) VALUES ((SELECT id FROM marketplace WHERE codigo = ''mercadolibre''), ''err-001'', ''Olla'', ''https://tienda.co/olla'', -5000);'],
        ARRAY['16. oferta: Moneda que no es código ISO', 'INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual, moneda) VALUES ((SELECT id FROM marketplace WHERE codigo = ''mercadolibre''), ''err-002'', ''Olla'', ''https://tienda.co/olla'', 5000, ''cop'');'],
        ARRAY['17. oferta: Enlace que no es una dirección web', 'INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual) VALUES ((SELECT id FROM marketplace WHERE codigo = ''mercadolibre''), ''err-003'', ''Olla'', ''www.tienda.co/olla'', 5000);'],
        ARRAY['18. oferta: Calificación de 7 estrellas', 'INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual, calificacion) VALUES ((SELECT id FROM marketplace WHERE codigo = ''mercadolibre''), ''err-004'', ''Olla'', ''https://tienda.co/olla'', 5000, 7);'],
        ARRAY['19. oferta: Reseñas negativas', 'INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual, total_resenas) VALUES ((SELECT id FROM marketplace WHERE codigo = ''mercadolibre''), ''err-005'', ''Olla'', ''https://tienda.co/olla'', 5000, -3);'],
        ARRAY['20. oferta: Envío negativo', 'INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual, costo_envio) VALUES ((SELECT id FROM marketplace WHERE codigo = ''mercadolibre''), ''err-006'', ''Olla'', ''https://tienda.co/olla'', 5000, -100);'],
        ARRAY['21. oferta: Tienda que no existe', 'INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual) VALUES (999, ''err-007'', ''Olla'', ''https://tienda.co/olla'', 5000);'],
        ARRAY['22. oferta: Misma publicación dos veces en la misma tienda', 'INSERT INTO oferta (marketplace_id, id_externo, titulo, url_destino, precio_actual) VALUES ((SELECT id FROM marketplace WHERE codigo = ''mercadolibre''), ''demo-ml-001'', ''Copia'', ''https://tienda.co/copia'', 5000);'],
        ARRAY['23. historial_precio: Oferta que no existe', 'INSERT INTO historial_precio (oferta_id, precio, moneda) VALUES (999999, 5000, ''COP'');'],
        ARRAY['24. historial_precio: Precio negativo', 'INSERT INTO historial_precio (oferta_id, precio, moneda) VALUES ((SELECT id FROM oferta WHERE id_externo = ''demo-ml-001''), -1, ''COP'');'],
        ARRAY['25. historial_precio: Moneda inválida', 'INSERT INTO historial_precio (oferta_id, precio, moneda) VALUES ((SELECT id FROM oferta WHERE id_externo = ''demo-ml-001''), 5000, ''12$'');'],
        ARRAY['26. busqueda: Término de una letra', 'INSERT INTO busqueda (termino, total_resultados, duracion_ms) VALUES (''a'', 0, 120);'],
        ARRAY['27. busqueda: Resultados negativos', 'INSERT INTO busqueda (termino, total_resultados, duracion_ms) VALUES (''olla'', -2, 120);'],
        ARRAY['28. busqueda: Duración negativa', 'INSERT INTO busqueda (termino, total_resultados, duracion_ms) VALUES (''olla'', 3, -50);'],
        ARRAY['29. busqueda: Usuario que no existe', 'INSERT INTO busqueda (termino, total_resultados, duracion_ms, usuario_id) VALUES (''olla'', 3, 120, 999999);'],
        ARRAY['30. consumo_api: Código HTTP 999', 'INSERT INTO consumo_api (marketplace_id, exitoso, codigo_http, duracion_ms) VALUES ((SELECT id FROM marketplace WHERE codigo = ''mercadolibre''), false, 999, 300);'],
        ARRAY['31. consumo_api: Duración negativa', 'INSERT INTO consumo_api (marketplace_id, exitoso, codigo_http, duracion_ms) VALUES ((SELECT id FROM marketplace WHERE codigo = ''mercadolibre''), true, 200, -1);'],
        ARRAY['32. pqr: Tipo que no existe', 'INSERT INTO pqr (usuario_id, tipo, asunto, descripcion) VALUES ((SELECT id FROM usuario WHERE correo = ''rosa.demo@marketradar.co''), ''FELICITACION'', ''Muy buena'', ''La aplicación me ayudó a comparar precios.'');'],
        ARRAY['33. pqr: Asunto muy corto', 'INSERT INTO pqr (usuario_id, tipo, asunto, descripcion) VALUES ((SELECT id FROM usuario WHERE correo = ''rosa.demo@marketradar.co''), ''QUEJA'', ''Mal'', ''La búsqueda tardó mucho en responder hoy.'');'],
        ARRAY['34. pqr: Descripción muy corta', 'INSERT INTO pqr (usuario_id, tipo, asunto, descripcion) VALUES ((SELECT id FROM usuario WHERE correo = ''rosa.demo@marketradar.co''), ''QUEJA'', ''Búsqueda lenta'', ''Muy lenta.'');'],
        ARRAY['35. pqr: Estado que no existe', 'INSERT INTO pqr (usuario_id, tipo, asunto, descripcion, estado) VALUES ((SELECT id FROM usuario WHERE correo = ''rosa.demo@marketradar.co''), ''QUEJA'', ''Búsqueda lenta'', ''La búsqueda tardó mucho en responder hoy.'', ''CERRADA'');'],
        ARRAY['36. pqr: Respondida sin respuesta', 'INSERT INTO pqr (usuario_id, tipo, asunto, descripcion, estado) VALUES ((SELECT id FROM usuario WHERE correo = ''rosa.demo@marketradar.co''), ''QUEJA'', ''Búsqueda lenta'', ''La búsqueda tardó mucho en responder hoy.'', ''RESPONDIDA'');'],
        ARRAY['37. pqr: Respondida antes de crearse', 'INSERT INTO pqr (usuario_id, tipo, asunto, descripcion, estado, respuesta, creada_en, respondida_en) VALUES ((SELECT id FROM usuario WHERE correo = ''rosa.demo@marketradar.co''), ''QUEJA'', ''Búsqueda lenta'', ''La búsqueda tardó mucho en responder hoy.'', ''RESPONDIDA'', ''Ya quedó revisado.'', ''2026-10-30'', ''2026-10-01'');'],
        ARRAY['38. pqr: Usuario que no existe', 'INSERT INTO pqr (usuario_id, tipo, asunto, descripcion) VALUES (999999, ''QUEJA'', ''Búsqueda lenta'', ''La búsqueda tardó mucho en responder hoy.'');'],
        ARRAY['39. tasa_cambio: Valor cero', 'INSERT INTO tasa_cambio (moneda_origen, moneda_destino, valor, vigente_desde, fuente) VALUES (''USD'', ''COP'', 0, ''2026-10-30'', ''prueba'');'],
        ARRAY['40. tasa_cambio: Misma moneda de origen y destino', 'INSERT INTO tasa_cambio (moneda_origen, moneda_destino, valor, vigente_desde, fuente) VALUES (''COP'', ''COP'', 1, ''2026-10-30'', ''prueba'');'],
        ARRAY['41. tasa_cambio: Moneda en minúsculas', 'INSERT INTO tasa_cambio (moneda_origen, moneda_destino, valor, vigente_desde, fuente) VALUES (''usd'', ''COP'', 4000, ''2026-10-30'', ''prueba'');'],
        ARRAY['42. tasa_cambio: Misma tasa el mismo día', 'INSERT INTO tasa_cambio (moneda_origen, moneda_destino, valor, vigente_desde, fuente) VALUES (''USD'', ''COP'', 4100, ''2026-10-28'', ''prueba'');'],
        ARRAY['43. seguimiento_oferta: Precio objetivo negativo', 'INSERT INTO seguimiento_oferta (usuario_id, oferta_id, precio_objetivo) VALUES ((SELECT id FROM usuario WHERE correo = ''rosa.demo@marketradar.co''), (SELECT id FROM oferta WHERE id_externo = ''demo-ml-001''), -10);'],
        ARRAY['44. seguimiento_oferta: Seguir la misma oferta dos veces', 'INSERT INTO seguimiento_oferta (usuario_id, oferta_id) SELECT usuario_id, oferta_id FROM seguimiento_oferta LIMIT 1;'],
        ARRAY['45. seguimiento_oferta: Oferta que no existe', 'INSERT INTO seguimiento_oferta (usuario_id, oferta_id) VALUES ((SELECT id FROM usuario WHERE correo = ''rosa.demo@marketradar.co''), 999999);']
    ];
    rechazados int := 0;
    restriccion text;
BEGIN
    FOR i IN 1 .. array_length(casos, 1) LOOP
        BEGIN
            EXECUTE casos[i][2];
            RAISE NOTICE 'ACEPTADO (revisar): %', casos[i][1];
        EXCEPTION WHEN others THEN
            GET STACKED DIAGNOSTICS restriccion = CONSTRAINT_NAME;
            rechazados := rechazados + 1;
            RAISE NOTICE 'RECHAZADO: % -> %', casos[i][1], coalesce(nullif(restriccion, ''), SQLERRM);
        END;
    END LOOP;
    RAISE NOTICE '======== % de % casos rechazados ========', rechazados, array_length(casos, 1);
END $$;
ROLLBACK;
