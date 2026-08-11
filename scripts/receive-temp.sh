#!/bin/bash
echo "Mostrando logs del suscriptor Java en tiempo real (Ctrl+C para salir)..."
docker compose -f docker/docker-compose.yml logs -f subscriber