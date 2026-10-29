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
```
