#!/bin/bash
# Si se pasa un argumento ($1), usa ese valor; si no, usa "22.5"
TEMP=${1:-"22.5"}

echo "Enviando temperatura: $TEMP °C al tópico shellyhtg3-a1b2c3d4e5f6/status/temperature:0..."
curl -d "{"id":0,"tC":$TEMP,"tF":71.3,"ts":1788297466.028}" "mqtt://localhost:1883/shellyhtg3-a1b2c3d4e5f6/status/temperature:0"
