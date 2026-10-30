-- Marketradar: crea la base de datos, el usuario de la aplicación y un rol de solo lectura.
--
-- Se ejecuta una sola vez, como superusuario (postgres), antes del primer arranque del backend.
-- Las claves no se escriben en este archivo: se pasan como variables de psql.
--
--   psql -U postgres -v clave_app='...' -v clave_lectura='...' -f scripts/01_crear_base_y_roles.sql
--
-- Se puede ejecutar varias veces: si la base o los roles ya existen, no los vuelve a crear
-- y solo actualiza las claves y los permisos.

\set ON_ERROR_STOP on

\if :{?clave_app}
\else
    \echo 'Falta la variable clave_app. Ejemplo: psql -U postgres -v clave_app=... -v clave_lectura=... -f scripts/01_crear_base_y_roles.sql'
    \quit
\endif
\if :{?clave_lectura}
\else
    \echo 'Falta la variable clave_lectura.'
    \quit
\endif

-- 1. Usuario dueño de las tablas. Es el que usa el backend (DB_USUARIO) y con el que Flyway crea el esquema.
SELECT format('CREATE ROLE marketradar LOGIN PASSWORD %L', :'clave_app')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'marketradar')
\gexec
ALTER ROLE marketradar WITH LOGIN PASSWORD :'clave_app' NOSUPERUSER NOCREATEDB NOCREATEROLE;

-- 2. Rol de solo lectura para reportes, pgAdmin y revisión del docente. Puede consultar, pero no modificar.
SELECT format('CREATE ROLE marketradar_lectura LOGIN PASSWORD %L', :'clave_lectura')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'marketradar_lectura')
\gexec
ALTER ROLE marketradar_lectura WITH LOGIN PASSWORD :'clave_lectura' NOSUPERUSER NOCREATEDB NOCREATEROLE;

-- 3. Base de datos, con el usuario de la aplicación como dueño.
SELECT 'CREATE DATABASE marketradar OWNER marketradar ENCODING ''UTF8'' TEMPLATE template0'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'marketradar')
\gexec

REVOKE ALL ON DATABASE marketradar FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE marketradar TO marketradar;
GRANT CONNECT ON DATABASE marketradar TO marketradar_lectura;

-- 4. Permisos dentro de la base.
\connect marketradar

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO marketradar;
GRANT USAGE ON SCHEMA public TO marketradar_lectura;

-- Tablas y secuencias que ya existan (si el backend ya arrancó alguna vez).
GRANT SELECT ON ALL TABLES IN SCHEMA public TO marketradar_lectura;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO marketradar_lectura;

-- Tablas, vistas y secuencias que Flyway cree después con el usuario marketradar.
ALTER DEFAULT PRIVILEGES FOR ROLE marketradar IN SCHEMA public
    GRANT SELECT ON TABLES TO marketradar_lectura;
ALTER DEFAULT PRIVILEGES FOR ROLE marketradar IN SCHEMA public
    GRANT SELECT ON SEQUENCES TO marketradar_lectura;

\echo 'Listo: base marketradar, usuario marketradar y rol marketradar_lectura.'
