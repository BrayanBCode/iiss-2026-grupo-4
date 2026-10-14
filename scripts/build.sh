#!/bin/bash
# Compila el controlador usando ÚNICAMENTE Docker (no hace falta Java ni Maven
# en el host): la compilación ocurre en la etapa "build" de
# modules/engine/Dockerfile, que también compila el módulo core.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

echo "Compilando el controlador vía Docker..."
docker compose -f docker/docker-compose.yml build
echo "Build completo."
