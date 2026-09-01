#!/bin/bash
# Muestra en vivo el log del subscriber, leyéndolo directo del host (montado
# como volumen en docker-compose.yml) -- no depende de "docker compose exec"
# ni de que el contenedor siga vivo en el momento de leerlo.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/../logs/subscriber.log"

if [ ! -f "$LOG_FILE" ]; then
    echo "⚠️ Todavía no existe $LOG_FILE — levantá el subscriber primero con ./scripts/up.sh"
    exit 1
fi

echo "Mostrando $LOG_FILE en vivo (Ctrl+C para salir)..."
tail -f "$LOG_FILE"