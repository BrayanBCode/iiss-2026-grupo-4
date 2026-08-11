#!/bin/bash
echo "Levantando servicios con Docker Compose..."
docker compose -f docker/docker-compose.yml up -d --build