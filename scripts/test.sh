#!/bin/bash
# Corre los tests unitarios (mvn test) usando ÚNICAMENTE Docker, igual que
# build.sh hace con la compilación. No hace falta tener Maven ni el JDK 25
# instalados/configurados en el host: todo corre dentro de la imagen oficial
# de Maven que ya usan los Dockerfile de eventGenerator y subscriber.
#
# Uso:
#   ./scripts/test.sh              -> corre los tests de TODOS los módulos
#   ./scripts/test.sh subscriber    -> corre solo los tests de subscriber
#   ./scripts/test.sh eventGenerator -> corre solo los tests de eventGenerator
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

MODULO="${1:-}"

if [ -n "$MODULO" ]; then
    MVN_GOAL="-pl src/$MODULO -am test"
else
    MVN_GOAL="test"
fi

echo "Corriendo tests vía Docker (mvn $MVN_GOAL)..."

# MSYS_NO_PATHCONV evita que Git Bash (MSYS) en Windows "traduzca" las rutas
# tipo /build a rutas de Windows (ej: C:/Program Files/Git/build), que es lo
# que rompe el -w /build al correr este script desde Git Bash.
export MSYS_NO_PATHCONV=1

# Se monta el proyecto completo dentro del contenedor y se usa un volumen
# aparte para el repositorio local de Maven (~/.m2), así las dependencias
# quedan cacheadas entre corridas y no se re-descargan cada vez.
docker run --rm \
    -v "$PWD":/build \
    -v grupo4-maven-repo:/root/.m2 \
    -w /build \
    maven:3.9-eclipse-temurin-25-alpine \
    mvn -B $MVN_GOAL

echo "Tests terminados."
