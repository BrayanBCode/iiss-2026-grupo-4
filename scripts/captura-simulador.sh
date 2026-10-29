#!/bin/bash
# Guarda la evidencia de una corrida contra el simulador (tarjeta T13): cada N segundos
# consulta GET /control y GET /diagnostico del controlador, y al terminar copia el log del
# contenedor. Todo queda en docs/tests/corrida-simulador/<fecha>-<escenario>/.
#
# Uso: ./scripts/captura-simulador.sh <escenario> [segundos-entre-capturas] [duracion-segundos]
#   ./scripts/captura-simulador.sh cruce-punta 5 600
# Variables opcionales: API_KEY, API_URL, CONTENEDOR.
# Ctrl+C corta antes de tiempo y guarda igual el log.
set -euo pipefail

ESCENARIO=${1:?"Falta el nombre del escenario (ver docs/tests/corrida-simulador/README.md)"}
INTERVALO=${2:-5}
DURACION=${3:-600}
API_KEY=${API_KEY:-"ecowarm-grupo4-2026"}
API_URL=${API_URL:-"http://localhost:8080"}
CONTENEDOR=${CONTENEDOR:-"ecowarm-grupo4"}

DIR="docs/tests/corrida-simulador/$(date +%Y%m%d-%H%M%S)-$ESCENARIO"
mkdir -p "$DIR"
INICIO=$(date +%s)

guardar_log() {
    docker logs "$CONTENEDOR" > "$DIR/engine.log" 2>&1 || echo "No se pudo leer el log de $CONTENEDOR"
    echo "Evidencia en $DIR"
}
trap guardar_log EXIT

echo "Capturando '$ESCENARIO' cada ${INTERVALO}s durante ${DURACION}s en $DIR"
while [ $(( $(date +%s) - INICIO )) -lt "$DURACION" ]; do
    real=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    control=$(curl -s -H "X-API-Key: $API_KEY" "$API_URL/control" || echo '{"error":"sin respuesta"}')
    diagnostico=$(curl -s -H "X-API-Key: $API_KEY" "$API_URL/diagnostico" || echo '{"error":"sin respuesta"}')
    # Una línea JSON por captura: hora real de la captura, estado del control y diagnóstico.
    echo "{\"horaReal\":\"$real\",\"control\":$control,\"diagnostico\":$diagnostico}" >> "$DIR/capturas.jsonl"
    sleep "$INTERVALO"
done
