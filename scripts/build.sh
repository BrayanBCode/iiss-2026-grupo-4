#!/bin/bash
# Compila todo el sistema (incluido el subscriber Java) usando ÚNICAMENTE Docker.
# No requiere tener Java, Maven ni IntelliJ instalados/configurados en el host:
# toda la compilación ocurre dentro del contenedor, en la etapa "build" de
# docker/Dockerfile (ver ese archivo para el detalle del compilado con javac).
set -euo pipefail

echo "Compilando el sistema vía Docker (sin herramientas locales)..."
docker compose -f docker/docker-compose.yml build
echo "Build completo."
