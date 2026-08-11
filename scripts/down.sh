#!/bin/bash
echo "Deteniendo y limpiando contenedores..."
docker compose -f docker/docker-compose.yml down