#!/usr/bin/env bash
# Backup del índice (pg_dump custom-format) con retención de 7 días.
# Ver README para la línea de cron en el VPS.
set -euo pipefail

cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi

STAMP="$(date +%F_%H%M)"
DIR="backups"
mkdir -p "$DIR"

docker compose exec -T postgres \
    pg_dump -U "${POSTGRES_USER:-catalog_user}" -Fc "${POSTGRES_DB:-catalog_db}" \
    > "$DIR/catalog_$STAMP.dump"

find "$DIR" -name 'catalog_*.dump' -mtime +7 -delete
echo "backup listo: $DIR/catalog_$STAMP.dump"
