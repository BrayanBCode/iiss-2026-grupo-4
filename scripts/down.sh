#!/bin/bash
# Baja y elimina el contenedor y la red. El inventario persistido se conserva
# en el volumen "inventario" (para borrarlo también: docker compose ... down -v).
echo "Deteniendo y limpiando contenedores..."
docker compose -f docker/docker-compose.yml down
