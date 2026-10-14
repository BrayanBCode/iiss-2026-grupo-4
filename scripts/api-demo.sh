#!/bin/bash
# Recorre el API del controlador (estándar v2, §5) con curl, incluida la
# credencial (header X-API-Key). Requiere el controlador levantado.
#
# Uso: ./scripts/api-demo.sh [api-key] [url]
# La API key tiene que ser la del controlador (API_KEY en docker-compose.yml).
set -euo pipefail

API_KEY=${1:-"ecowarm-grupo4-2026"}
API_URL=${2:-"http://localhost:8080"}
AUTH="X-API-Key: $API_KEY"
JSON="Content-Type: application/json"

separador() { echo; echo "----- $1 -----"; }

separador "PUT /sitio (carga el inventario de ejemplo del §1)"
curl -s -X PUT "$API_URL/sitio" -H "$AUTH" -H "$JSON" -d '{
  "schemaVersion": "2.0",
  "sitio": { "id": "casa-rodriguez", "nombre": "Casa Rodríguez", "potenciaContratadaKW": 3.7,
             "tarifa": { "punta": { "desde": "17:00", "hasta": "23:00", "dias": "HABILES" } } },
  "habitaciones": [
    { "id": "room1", "nombre": "Living", "temperaturaEsperada": 21.5, "potenciaKW": 1.2,
      "idTermostato": "ht-sim-room1", "topicTermostato": "ht-sim-room1/status/temperature:0",
      "idSwitch": "sw-sim-room1", "urlSwitch": "http://localhost:9090" },
    { "id": "room2", "nombre": "Dormitorio principal", "temperaturaEsperada": 20.0, "potenciaKW": 0.8,
      "idTermostato": "ht-sim-room2", "topicTermostato": "ht-sim-room2/status/temperature:0",
      "idSwitch": "sw-sim-room2", "urlSwitch": "http://localhost:9090" } ] }'
echo

separador "GET /sitio (lo persistido y en uso)"
curl -s "$API_URL/sitio" -H "$AUTH"; echo

separador "PUT /sitio inválido (idSwitch duplicado) -> 400 y queda el anterior"
curl -s -X PUT "$API_URL/sitio" -H "$AUTH" -H "$JSON" -d '{"schemaVersion":"2.0","sitio":{"id":"x","nombre":"x","potenciaContratadaKW":1,"tarifa":{"punta":{"desde":"17:00","hasta":"23:00","dias":"HABILES"}}},"habitaciones":[{"id":"a","nombre":"a","temperaturaEsperada":20,"potenciaKW":1,"idTermostato":"t1","topicTermostato":"t1/x","idSwitch":"s","urlSwitch":"http://h"},{"id":"b","nombre":"b","temperaturaEsperada":20,"potenciaKW":1,"idTermostato":"t2","topicTermostato":"t2/x","idSwitch":"s","urlSwitch":"http://h"}]}'
echo

separador "GET /control"
curl -s "$API_URL/control" -H "$AUTH"; echo

separador "POST /control/comandos INICIAR (acelerado x10)"
curl -s -X POST "$API_URL/control/comandos" -H "$AUTH" -H "$JSON" \
     -d "{\"accion\":\"INICIAR\",\"fechaHora\":\"$(date -u +%Y-%m-%dT%H:%M:%S.000Z)\",\"factorTiempo\":10}"
echo

separador "GET /diagnostico (decisión y motivo por habitación; fuera del estándar)"
sleep 2
curl -s "$API_URL/diagnostico" -H "$AUTH"; echo

separador "GET /habitaciones (ABM sobre el inventario; fuera del estándar)"
curl -s "$API_URL/habitaciones" -H "$AUTH"; echo

separador "POST /control/comandos DETENER"
curl -s -X POST "$API_URL/control/comandos" -H "$AUTH" -H "$JSON" -d '{"accion":"DETENER"}'
echo

separador "Sin credencial -> 401 esperado"
curl -s -o /dev/null -w "status: %{http_code}\n" "$API_URL/control"
