#!/bin/bash
echo "Deteniendo los contenedores de Docker..."
docker compose -f docker/docker-compose.yml stop