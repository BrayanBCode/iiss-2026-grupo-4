#!/bin/bash
# Si se pasa un argumento ($1), usa ese valor; si no, usa "22.5"
TEMP=${1:-"22.5"}

echo "Enviando temperatura: $TEMP °C al tópico ingSoft/informa..."
curl -d "Temperatura actual: $TEMP °C" "mqtt://localhost:1883/ingSoft/informa"