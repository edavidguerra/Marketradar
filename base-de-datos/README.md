# Base de datos de Marketradar

Esta carpeta reúne todo lo que corresponde a la asignatura **Bases de Datos**. Solo contiene SQL de
PostgreSQL 16 y no depende del código Java: los scripts se pueden leer y ejecutar con `psql` o pgAdmin.

El backend no guarda copia de estos archivos. Al compilar, Maven toma la carpeta `migraciones/` y Flyway
aplica los scripts en orden cuando arranca la aplicación.

## Migraciones

| Archivo | Qué crea |
| --- | --- |
| `V1__esquema_inicial.sql` | Tablas principales (marketplace, producto, oferta, historial_precio, busqueda y consumo_api) con sus llaves, restricciones e índices. |
| `V2__marketplaces_iniciales.sql` | Datos iniciales: las tiendas que consulta Marketradar. |
| `V3__usuarios_pqr_y_resenas.sql` | Usuarios con rol, PQR, búsquedas por usuario, calificación y reseñas de las ofertas, y tasas de cambio. |
| `V4__vistas_reportes.sql` | Vistas de solo lectura para reportes: ofertas vigentes en pesos, bajadas de precio, resumen de PQR y disponibilidad de cada API. |

## Vistas y consultas de reportes

Las vistas de `V4` no cambian datos ni el backend; solo facilitan las consultas:

| Vista | Qué muestra |
| --- | --- |
| `vista_ofertas_vigentes` | Cada oferta de una tienda activa con su tienda, el precio total con envío y ese total en pesos, usando la tasa de cambio más reciente. |
| `vista_bajadas_precio` | Ofertas cuyo último precio registrado es menor que el anterior, con el ahorro y el porcentaje. |
| `vista_resumen_pqr` | Cantidad de PQR por tipo y estado, y las horas promedio que tardó la respuesta. |
| `vista_disponibilidad_api` | Consultas a la API de cada tienda, porcentaje de éxito y tiempo promedio de respuesta. |

`consultas/reportes.sql` tiene once consultas que responden preguntas del negocio (lo más buscado, la tienda más
barata por producto, ofertas bajo el promedio, registros por mes, PQR sin respuesta, entre otras) con `JOIN`,
`GROUP BY`, `HAVING`, subconsultas y `NOT EXISTS`. Se ejecutan sobre una base que ya tenga V1 a V4:

```bash
psql -U marketradar -d marketradar -f consultas/reportes.sql
```

## Reglas para cambiar el esquema

- Cada cambio es un archivo nuevo `V<número>__descripcion.sql`. Una migración que ya está en `main` no se edita,
  porque Flyway guarda su firma y rechazaría el cambio.
- Tablas y columnas en español, en minúscula y con guion bajo (`historial_precio`, `creada_en`).
- Toda tabla tiene llave primaria, y las relaciones se declaran con `REFERENCES` y la regla de borrado explícita.
- Las reglas que protegen los datos (rangos, valores permitidos, únicos) van como `CHECK`, `UNIQUE` o `NOT NULL`
  en la base, no solo en el código.

## Revisar el esquema sin el backend

La base `marketradar` la administra Flyway. Para revisar el esquema a mano, cree una base aparte y ejecute los
scripts en orden desde esta carpeta:

```bash
createdb -U postgres marketradar_revision
psql -U postgres -d marketradar_revision -f migraciones/V1__esquema_inicial.sql
psql -U postgres -d marketradar_revision -f migraciones/V2__marketplaces_iniciales.sql
psql -U postgres -d marketradar_revision -f migraciones/V3__usuarios_pqr_y_resenas.sql
psql -U postgres -d marketradar_revision -f migraciones/V4__vistas_reportes.sql
```

## Scripts de administración

La carpeta `scripts/` tiene lo necesario para preparar la base en un servidor nuevo y para sacar y recuperar
respaldos. Con Docker Compose no hace falta el primero, porque el contenedor ya crea la base y el usuario.

| Archivo | Para qué sirve |
| --- | --- |
| `01_crear_base_y_roles.sql` | Crea el usuario `marketradar` (dueño de la base, el que usa la aplicación), el rol `marketradar_lectura` (solo consulta) y la base `marketradar`, con sus permisos. Se puede ejecutar varias veces sin error. |
| `respaldar.sh` | Saca un respaldo completo con `pg_dump` en formato personalizado y lo guarda con fecha y hora. |
| `restaurar.sh` | Restaura un respaldo con `pg_restore` en otra base (por defecto `marketradar_restaurada`) y muestra cuántas filas quedaron en cada tabla. |

En Windows, los archivos `.sh` se ejecutan desde **Git Bash**, con las herramientas de PostgreSQL 16 en el `PATH`.

### Crear la base y los roles

Se ejecuta una sola vez con el superusuario `postgres`. Las claves se pasan como variables y no quedan escritas en
el repositorio:

```bash
psql -U postgres -v clave_app='ClaveDeLaApp' -v clave_lectura='ClaveDeConsulta' -f scripts/01_crear_base_y_roles.sql
```

Después, al arrancar el backend con `DB_USUARIO=marketradar` y `DB_CLAVE` igual a `clave_app`, Flyway crea las
tablas. El rol `marketradar_lectura` puede consultar todas las tablas, incluso las que creen migraciones nuevas, pero no
puede insertar, modificar, borrar ni crear tablas:

```bash
psql -U marketradar_lectura -d marketradar -c "SELECT nombre FROM marketplace;"
```

### Sacar un respaldo

```bash
PGPASSWORD='ClaveDeLaApp' ./scripts/respaldar.sh            # guarda en respaldos/
PGPASSWORD='ClaveDeLaApp' ./scripts/respaldar.sh /ruta/otra  # guarda en otra carpeta
```

Con Docker Compose:

```bash
docker compose exec -T db pg_dump -U marketradar -Fc marketradar > respaldos/marketradar.dump
```

Los respaldos tienen datos de usuarios, así que la carpeta `respaldos/` no se sube al repositorio.

### Restaurar un respaldo

```bash
PGUSER=postgres ./scripts/restaurar.sh respaldos/marketradar_20261030_101500.dump
```

Por defecto restaura en `marketradar_restaurada`, para comprobar el respaldo sin tocar la base en uso. Para
reemplazar la base `marketradar` se pasa su nombre como segundo argumento y el script pide escribir `SI` antes de
continuar.
