-- Tienda de demostración: productos de ejemplo que trae el backend (demostracion/catalogo.json) para
-- probar y mostrar Marketradar mientras las tiendas reales no tienen claves. Se prende con
-- DEMOSTRACION_HABILITADO=true y sus ofertas quedan guardadas como las de cualquier otra tienda.
INSERT INTO marketplace (codigo, nombre, sitio_web, activo) VALUES
    ('demostracion', 'Tienda de demostración', 'https://marketradar.onrender.com/tienda-demostracion.html', TRUE)
ON CONFLICT (codigo) DO NOTHING;
