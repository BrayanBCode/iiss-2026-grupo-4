#!/bin/bash
# Si se pasa un argumento ($1), usa ese valor; si no, usa "22.5"
TEMP=${1:-"22.5"}

echo "Enviando temperatura: $TEMP °C al tópico ingSoft/informa..."
docker compose -f docker/docker-compose.yml exec -T mosquitto mosquitto_pub -h localhost -p 1883 -t "ingSoft/informa" -m "Temperatura actual: $TEMP °C"