#!/usr/bin/env bash
# Marketradar: restaura un respaldo creado con respaldar.sh usando pg_restore.
#
# Uso:  ./restaurar.sh <archivo.dump> [base_destino]
#
# Por seguridad, la base destino por defecto es marketradar_restaurada y no la base real.
# El script crea la base destino si no existe y la deja con el mismo contenido del respaldo.
# Si la base destino ya tiene datos, las tablas se borran y se vuelven a crear (--clean).
#
# La conexión se toma de PGHOST, PGPORT y PGUSER (por defecto localhost, 5432 y marketradar).
# El usuario debe poder crear bases, o la base destino ya debe existir.

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Uso: $0 <archivo.dump> [base_destino]" >&2
    exit 1
fi

archivo="$1"
destino="${2:-marketradar_restaurada}"

if [ ! -f "$archivo" ]; then
    echo "No existe el archivo $archivo" >&2
    exit 1
fi

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-marketradar}"

if [ "$destino" = "marketradar" ]; then
    read -r -p "Va a reemplazar la base REAL marketradar. Escriba SI para continuar: " respuesta
    [ "$respuesta" = "SI" ] || { echo "Cancelado."; exit 1; }
fi

existe=$(psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$destino'")
if [ "$existe" != "1" ]; then
    echo "Creando la base $destino..."
    createdb "$destino"
fi

echo "Restaurando $archivo en la base $destino..."
pg_restore --clean --if-exists --no-owner --no-privileges --exit-on-error --dbname="$destino" "$archivo"

echo "Restauración terminada. Filas por tabla:"
psql -d "$destino" -c "
SELECT table_name AS tabla,
       (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I', table_name), false, true, '')))[1]::text::int AS filas
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;"
