# Corrida contra el simulador (tarjeta T13)

Corridas registradas: [2026-10-28](2026-10-28/README.md) (final de la iteración 5: escenarios del simulador, problemas encontrados y respuestas a las preguntas abiertas) [2026-11-06](2026-11-06-ecowarm-run/README.md) (simulador oficial `ecowarm-run`: 9 de 9 escenarios) y [2026-11-08](2026-11-08-ecowarm-run-t37/README.md) (regresión de T37, timeouts escalados y switches sanos primero: 9 de 9). Las pruebas más duras que el simulador (switches colgados, factores ×600 y ×3600, 50 habitaciones, fallas combinadas) están en `EstresTest`.

Guía para la prueba con el simulador real. Cada escenario se captura con
`scripts/captura-simulador.sh`, que guarda en `docs/tests/corrida-simulador/<fecha>-<escenario>/`:

- `capturas.jsonl`: una línea por captura con la hora real, `GET /control` y `GET /diagnostico`.
- `engine.log`: el log del contenedor al terminar.

## Antes de empezar

1. Levantar el simulador y el controlador en su red:
   `docker compose -f docker/docker-compose.yml -f docker/docker-compose.lab.yml up -d --build`
2. Confirmar la configuración del controlador:
   - zona `America/Montevideo` (`CONTROL_ZONA_HORARIA`, `TZ`);
   - el `ts` llega en hora real y siempre se convierte a la hora del control (no hay propiedad que configurar).
3. Confirmar que se registró (§6), que recibió el sitio (`GET /sitio`) y que está en ejecución (`GET /control`).

## Qué mirar en cada captura

- **Hora del control** (`control.fechaHora`): tiene que avanzar a razón de `factorTiempo` por segundo real.
- **`ultimaLectura` de cada habitación** (`diagnostico`): tiene que quedar cerca de la hora del control.
  Si se separa cada vez más, el `ts` no se está convirtiendo o el factor no llegó por INICIAR.
- **Potencia comprometida**: la suma de `potenciaKW` (de `GET /sitio`) de las habitaciones con
  `decision` `ON` nunca supera `potenciaContratadaKW`.
- **`mqttConectado`** (`diagnostico`): `false` mientras el broker está caído.
- **Motivos** (`motivo` en `diagnostico`): explican cada decisión (`CALEFACCIONANDO`, `TARIFA_PUNTA`,
  `SIN_POTENCIA_DISPONIBLE`, `LECTURA_VENCIDA`, `SIN_LECTURA`, etc.).

## Escenarios

| # | Escenario (`<escenario>`) | Qué provoca el simulador | Resultado esperado | ¿Pasó? |
|---|---|---|---|---|
| 1 | `carga-libre` | La contratada alcanza para todas | Toda habitación con déficit en `ON` (`CALEFACCIONANDO`); se apaga al llegar a la esperada | |
| 2 | `carga-limitada` | La contratada no alcanza | Se respeta la contratada; las que no entran quedan `SIN_POTENCIA_DISPONIBLE`; prioridad al mayor déficit | |
| 3 | `cruce-punta` | Modo acelerado que cruza la franja punta | Al entrar a la punta todas a `OFF` (`TARIFA_PUNTA`); al salir se restituyen. El corte se atrasa como máximo un tick de tiempo de control: `max(1 s, 10 ms × factor)` | |
| 4 | `broker-caido` | El broker MQTT cae y vuelve | Mientras está caído, las lecturas vencen y las habitaciones pasan a `LECTURA_VENCIDA` (apagadas); al volver, se re-suscribe y retoma | |
| 5 | `switch-caido` | Un switch no responde | Los demás siguen comandándose; el caído se reintenta; nunca se supera la contratada por un `OFF` no confirmado | |
| 6 | `lecturas-atrasadas` | Lecturas con `ts` viejo o desincronizado | Una lectura con `ts` anterior a la última se descarta (`FUERA_DE_ORDEN`); una con `ts` más viejo que la vigencia también (`TS_VENCIDO`, D8). Las dos se cuentan en `/diagnostico` | |
| 7 | `termostato-caido` | Un termostato deja de publicar | A los 600 s de tiempo de control sin lecturas la habitación pasa a `LECTURA_VENCIDA` y se apaga | |
| 8 | `reinicio` | INICIAR repetido (cambio de factor) o DETENER | INICIAR re-sincroniza la hora sin error; DETENER deja de comandar switches y deja todo como está | |

### Tests de integración por escenario (T25)

Cada escenario tiene al menos un test de integración (engine completo, broker MQTT embebido y
switches HTTP falsos) que verifica el resultado esperado sin el simulador. `EscenariosSimuladorTest`
reproduce además la forma en que corre el simulador: `PUT /sitio`, INICIAR con factor 60 y
`fechaHora` en UTC, DETENER, y switches reiniciados entre escenarios.

| # | Escenario | Tests |
|---|---|---|
| 1 | `carga-libre` | `ControlCargaTest.cargaNoLimitante`, `habitacionCalienteNoSeEnciende` |
| 2 | `carga-limitada` | `ControlCargaTest.cargaLimitante`, `cargaLimitante_redistribuye`, `habitacionQueExcedeLaContratada`; `EscenariosSimuladorTest.secuenciaDeEscenarios` |
| 3 | `cruce-punta` | `TiempoAceleradoTest.cortaAlEntrarEnPunta`, `restituyeAlSalirDePunta` (×600); `ControlCargaTest.franjaPunta`; `ArranqueParadaTest.iniciarEnFranjaPunta` |
| 4 | `broker-caido` | `FallaBrokerMqttTest` (4 tests); `EscenariosSimuladorTest.brokerCaidoMasQueLaVigencia` (vence, se apaga y retoma al volver) |
| 5 | `switch-caido` | `FallaSwitchTest` (5 tests: 503, colgado, inalcanzable, caído al INICIAR, OFF fallido) |
| 6 | `lecturas-atrasadas` | `PerdidaMensajesTest.tsDelTermostato` (`FUERA_DE_ORDEN`, `TS_VENCIDO`); `EscenariosSimuladorTest.tsEnElFuturo` (reloj del termostato adelantado) |
| 7 | `termostato-caido` | `TermostatoCaidoTest` (7 tests, también con tiempo acelerado) |
| 8 | `reinicio` | `ArranqueParadaTest.detenerDejaDeComandar`, `reiniciarRetomaElControl`; `TermostatoCaidoTest.reiniciarDespuesDeLaVigencia_noReviveLaLectura`; `EscenariosSimuladorTest.secuenciaDeEscenarios` (INICIAR con la misma `fechaHora`, otro sitio, switches `DESCONOCIDO`). Re-INICIAR con otro factor: tarjeta T17, en revisión |

## Después de la corrida

- Completar la columna **¿Pasó?** y anotar abajo lo observado que no coincide con lo esperado.
- Resumir los resultados en `docs/tests/resultados.md` y pasar T13 de "Diferido" a la tabla de observaciones del registro [`revision-docente.md`](../../planificacion/revision-docente.md), con el commit de la corrida.
- Verificar lo pendiente de confirmar con el simulador:
  - si el paso a modo acelerado llega como un INICIAR nuevo con `factorTiempo`;
  - si hay desfase entre el reloj real del simulador y el del controlador.

## Observaciones

<!-- Una línea por hallazgo: escenario, qué se vio, captura o línea de log que lo muestra. -->
