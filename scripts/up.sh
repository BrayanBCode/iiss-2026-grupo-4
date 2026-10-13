#!/bin/bash
# Compila y levanta el controlador. Uso:
#   ./scripts/up.sh         -> contra el entorno en el anfitrión (ecowarm-check o simulador en :9090)
#   ./scripts/up.sh lab     -> en la red del simulador (iiss-lab-net), ver docker/docker-compose.lab.yml
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

ARCHIVOS="-f docker/docker-compose.yml"
if [ "${1:-}" = "lab" ]; then
    ARCHIVOS="$ARCHIVOS -f docker/docker-compose.lab.yml"
fi

./scripts/build.sh
echo "Levantando el controlador..."
docker compose $ARCHIVOS up -d
echo "Controlador en http://localhost:8080 (logs: docker logs -f ecowarm-grupo4)"
