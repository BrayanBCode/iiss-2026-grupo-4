# Corrida contra el simulador (tarjeta T13)

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

## Después de la corrida

- Completar la columna **¿Pasó?** y anotar abajo lo observado que no coincide con lo esperado.
- Resumir los resultados en `docs/tests/resultados.md` y pasar T13 de "Diferido" a la tabla de observaciones del registro [`revision-docente.md`](../../planificacion/revision-docente.md), con el commit de la corrida.
- Verificar lo pendiente de confirmar con el simulador:
  - si el paso a modo acelerado llega como un INICIAR nuevo con `factorTiempo`;
  - si hay desfase entre el reloj real del simulador y el del controlador.

## Observaciones

<!-- Una línea por hallazgo: escenario, qué se vio, captura o línea de log que lo muestra. -->
