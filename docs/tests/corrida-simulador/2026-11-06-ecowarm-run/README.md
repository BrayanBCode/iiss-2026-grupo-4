# Corrida con el simulador oficial `ecowarm-run` — 2026-11-06

`ecowarm-run` (versión `c95d037`, interop-v2) es el simulador de la cátedra por comando: trae el
simulador, el broker MQTT y los switches, corre escenarios firmados contra el controlador y deja un
reporte. Se corrieron sus 9 escenarios (`todos`) dos veces: antes y después del corte anticipado de
la punta (T24e).

**Resultado final: 9 de 9 pasan** ([`despues/reporte.md`](despues/reporte.md), con la serie y las
interacciones de cada escenario en `despues/20261106-170040-1/`).

## Cómo se corrió

```bash
# controlador (jar con T24b, T24c, T24d, T24e y T29 juntos)
ENTORNO_URL=http://localhost:9090 ENTORNO_API_KEY=iiss-2026 CONTROLADOR_BASE_URL=http://localhost:8085 API_KEY=clave-grupo4 java -jar engine.jar --server.port=8085
# simulador (el 1883 estaba ocupado por un Mosquitto local)
java --sun-misc-unsafe-memory-access=allow -jar ecowarm-run.jar todos --mqtt 1884
```

## Resultados

| Escenario | Qué prueba | Antes | Después |
|---|---|---|---|
| E02 | 1 habitación, entra todo | pasa | pasa |
| E04 | 2 habitaciones que no entran juntas: alternar | pasa (aprovechamiento 97,5 %) | pasa |
| E11 | Entrar en punta (17:00) | **no pasa**: calefactores encendidos 2 s dentro de la punta | **pasa** |
| E14 | Subir la temperatura esperada en caliente | pasa (reacciona en 3 s) | pasa |
| E18 | Bajar la contratada en caliente | pasa · mejora: aprovechamiento 78,8 % | pasa · igual |
| E19 | Un switch que arranca encendido | pasa (nunca se pasa de la contratada) | pasa |
| F01 | El broker cae 10 min y vuelve | pasa (se re-suscribe en 5,3 s) | pasa |
| F03 | Un termostato deja de publicar | pasa (la apaga a los 15 min, las demás siguen) | pasa |
| F05 | Los OFF no llegan durante 15 min | pasa · mejora: aprovechamiento 76,4 % | pasa · igual |

## El problema de E11 y su arreglo

A las 17:00:00 de control la pasada consultó y apagó los 5 switches, y los OFF llegaron entre las
17:00:01 y las 17:00:02: a ×60 cada milisegundo real son 60 ms simulados. La expectativa
`consumoEnPunta` (nivel riesgo: "en punta no se enciende nada") falló por esos segundos. Con T24e el
core corta `control.anticipacion-punta-segundos` (60 s de control) antes del comienzo de la franja, y
la salida no se adelanta (4+1 §2.3).

## Lo que queda como mejora (no hace fallar)

- **Aprovechamiento en E18 y F05 (78,8 % y 76,4 %, mínimo 80 %).** En F05 es buscado: mientras un OFF
  no se confirma, el controlador retiene el ON de otra habitación para no pasarse de la contratada.
  En E18 viene de la histéresis: una habitación apagada dentro de la banda de 0,3 °C bajo la
  esperada todavía no se enciende, y el simulador la cuenta como potencia que se podría usar.
