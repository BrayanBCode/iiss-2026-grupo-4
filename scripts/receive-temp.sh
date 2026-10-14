#!/bin/bash
# Muestra en vivo el log del controlador (lecturas MQTT, decisiones del core y
# comandos a los switches), leído del host (volumen ../logs).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/../logs/engine.log"

if [ ! -f "$LOG_FILE" ]; then
    echo "Todavía no existe $LOG_FILE — levantá el controlador primero con ./scripts/up.sh"
    exit 1
fi
echo "Mostrando $LOG_FILE en vivo (Ctrl+C para salir)..."
tail -f "$LOG_FILE"
