# Corrida con el simulador oficial `ecowarm-run` — 2026-11-08 (T37)

Regresión de T37 ([decisión A19](../../../arquitectura/decisiones.md#a19--un-switch-colgado-no-traba-a-los-demás)):
los timeouts de los switches pasan a medirse en tiempo de control (a ×60, 0,5 s reales) y cada
pasada atiende primero los switches sanos. Hacía falta confirmar que un timeout de medio segundo no
toma como caídos a los switches del simulador, y que los escenarios de fallas siguen pasando.

**Resultado: 9 de 9 pasan** ([`reporte.md`](reporte.md); el log del controlador está en
[`engine.log`](engine.log)). La serie y las interacciones de cada escenario (el detalle crudo de
`ecowarm-run`) no se guardan en el repo: se regeneran corriendo la herramienta.

## Cómo se corrió

```bash
# controlador: rama iter6/t37-switch-colgado (T24b–T24e, T31, T34, T36 y T37)
ENTORNO_URL=http://localhost:9090 ENTORNO_API_KEY=iiss-2026 CONTROLADOR_BASE_URL=http://localhost:8085 API_KEY=clave-grupo4 java -jar engine.jar --server.port=8085
# simulador (el 1883 está ocupado por un Mosquitto local)
java --sun-misc-unsafe-memory-access=allow -jar ecowarm-run.jar todos --mqtt 1884
```

En cada INICIAR el log muestra `Timeouts de los switches con factor 60.0: 500 ms de conexión, 500 ms
de respuesta.`

## Resultados, comparados con la corrida anterior

| Escenario | Qué prueba | 2026-11-06 (T24e) | 2026-11-08 (T37) |
|---|---|---|---|
| E02 | 1 habitación, entra todo | pasa | pasa |
| E04 | 2 habitaciones que no entran juntas | pasa | pasa |
| E11 | Entrar en punta (17:00) | pasa | pasa |
| E14 | Subir la temperatura esperada en caliente | pasa | pasa |
| E18 | Bajar la contratada en caliente | pasa · mejora: aprovechamiento 78,8 % | pasa · igual (78,8 %) |
| E19 | Un switch que arranca encendido | pasa | pasa |
| F01 | El broker cae y vuelve | pasa | pasa |
| F03 | Un termostato deja de publicar | pasa | pasa |
| F05 | Se pierden los OFF | pasa · mejora: aprovechamiento 76,4 % | pasa · igual (76,4 %) |

Ningún switch del simulador se dio por caído por el timeout de 0,5 s fuera del escenario de fallas:
las únicas fallas de switch del log son las 4 consultas que no responden en F05 (16:55:33, una cada
0,5 s, sin trabar más que eso), y entre 16:55:50 y 16:55:52 los cuatro `volvió a responder` cuando
el simulador los restituye. Lo que no cubren los escenarios del simulador (varios
switches colgados cruzando la punta, factores ×600 y ×3600, 50 habitaciones, fallas combinadas) lo
cubre `EstresTest`.
