#!/bin/bash
# Ejemplo de uso completo del API de habitaciones (Iteracion 3), via curl,
# incluida la credencial de autenticacion (header X-API-KEY).
#
# Requiere que el sistema este levantado: ./scripts/up.sh
#
# La API_KEY tiene que ser la misma que la de docker/docker-compose.yml
# (variable de entorno API_KEY del servicio "api"). Si se la cambia ahi,
# hay que pasarla aca como argumento: ./scripts/api-demo.sh otra-clave
set -euo pipefail

API_URL="http://localhost:8080"
API_KEY=${1:-"ecowarm-grupo4-2026"}
AUTH_HEADER="X-API-KEY: $API_KEY"
JSON="Content-Type: application/json"

# Sufijo unico por corrida: para que el script se pueda ejecutar las veces
# que haga falta sin chocar con el UNIQUE de nombre/idTermostato/idSwitch
# si una corrida anterior no llego a borrar la habitacion de prueba.
SUFIJO=$(date +%s)
NOMBRE="altillo-demo-$SUFIJO"
ID_TERMOSTATO="shellyhtg3-demo-$SUFIJO"
ID_SWITCH="shellypro1pm-demo-$SUFIJO"

separador() { echo; echo "----- $1 -----"; }

separador "POST /habitaciones (crear)"
HTTP_STATUS=$(curl -s -o /tmp/api-demo-resp.json -w "%{http_code}" -X POST "$API_URL/habitaciones" -H "$AUTH_HEADER" -H "$JSON" -d "{\"nombre\":\"$NOMBRE\",\"idTermostato\":\"$ID_TERMOSTATO\",\"idSwitch\":\"$ID_SWITCH\",\"temperaturaEsperada\":22.5}")
RESPUESTA=$(cat /tmp/api-demo-resp.json)
echo "$RESPUESTA"
if [ "$HTTP_STATUS" != "201" ]; then
    echo "No se pudo crear la habitacion de prueba (status $HTTP_STATUS). Abortando."
    exit 1
fi
ID=$(echo "$RESPUESTA" | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "id creado: $ID"

separador "GET /habitaciones (listar)"
curl -s "$API_URL/habitaciones" -H "$AUTH_HEADER"
echo

separador "GET /habitaciones/$ID (consultar por id)"
curl -s "$API_URL/habitaciones/$ID" -H "$AUTH_HEADER"
echo

separador "PATCH /habitaciones/$ID (modificacion parcial)"
curl -s -X PATCH "$API_URL/habitaciones/$ID" -H "$AUTH_HEADER" -H "$JSON" -d '{"temperaturaEsperada":23.0}'
echo

separador "POST /habitaciones/$ID/switch (accionar switch a mano)"
curl -s -X POST "$API_URL/habitaciones/$ID/switch" -H "$AUTH_HEADER" -H "$JSON" -d '{"accion":"ON"}'
echo

separador "GET /habitaciones/validar (comando: validar consistencia)"
curl -s "$API_URL/habitaciones/validar" -H "$AUTH_HEADER"
echo

separador "POST /controlador/iniciar"
curl -s -X POST "$API_URL/controlador/iniciar" -H "$AUTH_HEADER"
echo

separador "GET /controlador/estado"
curl -s "$API_URL/controlador/estado" -H "$AUTH_HEADER"
echo

separador "POST /controlador/parar"
curl -s -X POST "$API_URL/controlador/parar" -H "$AUTH_HEADER"
echo

separador "DELETE /habitaciones/$ID (eliminar)"
curl -s -o /dev/null -w "status: %{http_code}\n" -X DELETE "$API_URL/habitaciones/$ID" -H "$AUTH_HEADER"

separador "Sin credencial (para mostrar que la autenticacion esta aplicada) -> 401 esperado"
curl -s -o /dev/null -w "status: %{http_code}\n" "$API_URL/habitaciones"