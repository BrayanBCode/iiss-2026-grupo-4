#!/bin/bash
# Publica una lectura de prueba (estándar v2, §2) en el broker del entorno.
# Uso: ./scripts/send-temp.sh [temperatura] [topic] [host-broker]
TEMP=${1:-"22.5"}
TOPIC=${2:-"ht-sim-room1/status/temperature:0"}
BROKER=${3:-"localhost"}
TS=$(date +%s.%3N)

echo "Publicando $TEMP °C en $TOPIC (broker $BROKER)..."
curl -s -d "{\"id\":0,\"tC\":$TEMP,\"ts\":$TS}" "mqtt://$BROKER:1883/$TOPIC"
