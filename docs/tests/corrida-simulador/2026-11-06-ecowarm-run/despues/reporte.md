# Reporte ecowarm-run

- **Controlador:** ecowarm-grupo4 (http://localhost:8085)
- **Fecha:** 2026-11-06T17:18:12.363347900-03:00
- **Herramienta:** ecowarm-run c95d037, interop-v2

| escenario | resultado | lo que falló |
|---|---|---|
| E02 1 habitación, entra todo | pasa |  |
| E04 2 habitaciones, no entran juntas | pasa |  |
| E11 Entrar en punta | pasa |  |
| E14 Subir la temperatura de 2 habitaciones | pasa |  |
| E18 Bajar la contratada | pasa | aprovechamiento (mejora) |
| E19 Un switch ya encendido | pasa |  |
| F01 Se cae el broker y vuelve | pasa |  |
| F03 Un termostato deja de publicar | pasa |  |
| F05 Se pierden los OFF | pasa | aprovechamiento (mejora) |

## E02 1 habitación, entra todo

`E02 (incluido)` · sha256 `9b0d459cd684d9da…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 1 switches (consulta o comando); el último en 0s |
| ✓ | enRango | mejora | 1 de 1 habitaciones a ±0,5 °C al menos el 80% del tiempo; la peor, room1, el 100,0% |
| ✓ | conmutaciones | mejora | la que más conmutó, room1, 1,0 veces por hora (máximo 6,0) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |

Detalle: `20261106-170040-1/20261106-170040-2-E02.json` (serie e interacciones al lado)

## E04 2 habitaciones, no entran juntas

`E04 (incluido)` · sha256 `73b851b2b13c3667…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 2 switches (consulta o comando); el último en 0s |
| ✓ | aprovechamiento | mejora | cuando no entraba todo, usó en promedio el 97,5% de lo que podía encender (mínimo 80%) |
| ✓ | conmutaciones | mejora | la que más conmutó, room1, 1,0 veces por hora (máximo 6,0) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |

Detalle: `20261106-170040-1/20261106-170040-3-E04.json` (serie e interacciones al lado)

## E11 Entrar en punta

`E11 (incluido)` · sha256 `5794aaf06a2a34d7…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 5 switches (consulta o comando); el último en 0s |
| ✓ | enRango | mejora | 5 de 5 habitaciones a ±0,5 °C al menos el 80% del tiempo; la peor, room1, el 100,0% |
| ✓ | conmutaciones | mejora | la que más conmutó, room1, 1,0 veces por hora (máximo 6,0) |
| ✓ | consumoEnPunta | riesgo | en punta no hubo nada encendido |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |

Detalle: `20261106-170040-1/20261106-170040-4-E11.json` (serie e interacciones al lado)

## E14 Subir la temperatura de 2 habitaciones

`E14 (incluido)` · sha256 `d2004265623adeb9…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 5 switches (consulta o comando); el último en 0s |
| ✓ | enRango | mejora | 5 de 5 habitaciones a ±0,5 °C al menos el 80% del tiempo; la peor, room1, el 100,0% |
| ✓ | conmutaciones | mejora | la que más conmutó, room3, 0,7 veces por hora (máximo 6,0) |
| ✓ | reacciona | riesgo | 2 reacciones dentro de 5m desde el ack (1h2s) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |

Detalle: `20261106-170040-1/20261106-170040-5-E14.json` (serie e interacciones al lado)

## E18 Bajar la contratada

`E18 (incluido)` · sha256 `58bb278b887820b8…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | tomaControl | mejora | conoció los 5 switches (consulta o comando); el último en 0s |
| ✓ | reacciona | riesgo | 1 reacción dentro de 2m desde el ack (1h2s) |
| ✗ | aprovechamiento | mejora | cuando no entraba todo, usó en promedio el 78,8% de lo que podía encender (mínimo 80%) |
| ✓ | conmutaciones | mejora | la que más conmutó, room1, 1,0 veces por hora (máximo 6,0) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |

Detalle: `20261106-170040-1/20261106-170040-6-E18.json` (serie e interacciones al lado)

## E19 Un switch ya encendido

`E19 (incluido)` · sha256 `a0b251bea03055af…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | aprovechamiento | mejora | cuando no entraba todo, usó en promedio el 99,2% de lo que podía encender (mínimo 80%) |
| ✓ | conmutaciones | mejora | la que más conmutó, room1, 1,0 veces por hora (máximo 6,0) |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 1m (desde 19s) |

Detalle: `20261106-170040-1/20261106-170040-7-E19.json` (serie e interacciones al lado)

## F01 Se cae el broker y vuelve

`F01 (incluido)` · sha256 `ec37491242314969…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | enRango | mejora | 3 de 3 habitaciones a ±0,5 °C al menos el 70% del tiempo; la peor, room1, el 100,0% |
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |
| ✓ | reconecta | riesgo | se volvió a suscribir una vez tras la vuelta del broker; lo más lento, 5,3 s reales |
| — | apagaSinLecturas | mejora | no aplica: ninguna habitación quedó sin lecturas más de 15m |

Detalle: `20261106-170040-1/20261106-170040-8-F01.json` (serie e interacciones al lado)

## F03 Un termostato deja de publicar

`F03 (incluido)` · sha256 `8922387da4d52292…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✓ | enRango | mejora | 3 de 3 habitaciones a ±0,5 °C al menos el 70% del tiempo; la peor, room1, el 100,0% |
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | nunca hubo una habitación fría y apagada que entrara en lo libre de la contratada |
| ✓ | apagaSinLecturas | mejora | 1 habitación sin lecturas: apagada pasados 15m |

Detalle: `20261106-170040-1/20261106-170040-9-F03.json` (serie e interacciones al lado)

## F05 Se pierden los OFF

`F05 (incluido)` · sha256 `e0190c3302f95ff6…` · firmado

**pasa**

| | expectativa | nivel | qué pasó |
|---|---|---|---|
| ✗ | aprovechamiento | mejora | cuando no entraba todo, usó en promedio el 76,4% de lo que podía encender (mínimo 80%) |
| ✓ | potenciaMaxima | riesgo | la potencia encendida nunca pasó la contratada + 0,10 kW |
| ✓ | soloSwitchesDelSitio | contrato | todos los comandos fueron a switches del sitio vigente |
| ✓ | noComandaDetenido | contrato | ningún comando de encender a switches entre el DETENER del setup y el INICIAR |
| ✓ | actua | riesgo | la potencia libre con habitaciones frías apagadas nunca duró más de 10m; lo más largo, 1m (desde 9s) |
| — | reintenta | riesgo | no aplica: ningún comando cayó en una falla que terminara antes del fin |

Detalle: `20261106-170040-1/20261106-170040-10-F05.json` (serie e interacciones al lado)
