#!/usr/bin/env bash
# Marketradar: crea un respaldo de la base de datos con pg_dump en formato personalizado (-Fc).
#
# Uso:  ./respaldar.sh [carpeta_destino]
#
# La conexión se toma de las variables de entorno de PostgreSQL (con estos valores por defecto):
#   PGHOST=localhost  PGPORT=5432  PGDATABASE=marketradar  PGUSER=marketradar
# La clave se pide al ejecutar, o se toma de PGPASSWORD o del archivo ~/.pgpass.
#
# Con Docker Compose, sin instalar PostgreSQL:
#   docker compose exec -T db pg_dump -U marketradar -Fc marketradar > respaldos/marketradar.dump

set -euo pipefail

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-marketradar}"
export PGUSER="${PGUSER:-marketradar}"

carpeta="${1:-respaldos}"
mkdir -p "$carpeta"
archivo="$carpeta/${PGDATABASE}_$(date +%Y%m%d_%H%M%S).dump"

echo "Respaldando la base $PGDATABASE de $PGHOST:$PGPORT con el usuario $PGUSER..."
pg_dump --format=custom --no-owner --no-privileges --file="$archivo"

echo "Respaldo creado: $archivo ($(du -h "$archivo" | cut -f1))"
echo "Contenido:"
pg_restore --list "$archivo" | grep -E "TABLE DATA|SEQUENCE SET" | sed 's/^/  /'
