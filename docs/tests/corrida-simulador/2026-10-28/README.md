# Corrida contra el simulador de la cátedra — 2026-10-28 (tarjeta T23)

Relevamiento de la corrida del final de la iteración 5: qué escenarios corrió el simulador, cómo
respondió el controlador y qué problemas aparecieron. La evidencia es el log del contenedor:
[`engine-extracto.log`](engine-extracto.log) es el tramo con el simulador, sin las lecturas
`[TEMPERATURA]` (eran 25 643 de las 26 830 líneas del log completo).

**Versión del controlador:** `implementacion5` del 2026-10-28, antes de T16 (logging de pruebas) y de
"detenido no decide" (`c8519fb`). **Simulador:** `192.168.1.116:9094`, broker `192.168.1.116:1894`.

## Resumen

- **El control se comportó bien en los 7 escenarios observados**: carga holgada y limitante, 1, 2 y 5
  habitaciones, y cruce de la franja punta a ×60.
- **Los problemas fueron de configuración** (clave del entorno, `baseUrl` inalcanzable) y de
  observabilidad (log). Dos ya estaban corregidos antes de esta iteración; uno se corrige en T24a.
- **Respuestas para las preguntas abiertas:** el modo acelerado llega como `factorTiempo` en el INICIAR
  (L4 del roadmap), y el simulador lee la franja punta en hora de Montevideo (D4).

## Cómo trabaja el simulador

Cada escenario es la misma secuencia: `PUT /sitio` con un inventario generado
(`gen-<habitaciones>-<holgado|limitante>-s1`), `INICIAR` con `factorTiempo = 60` y una `fechaHora`
en UTC, unos minutos de lecturas y `DETENER`. Los termostatos publican en `ht-sim-roomN/...` y los
switches los sirve el propio simulador. Ejemplo de inventario (`gen-5-holgado-s1`): contratada
6,0 kW, punta 17:00–23:00 en días hábiles, 5 habitaciones de 0,8 a 1,2 kW (4,8 kW en total).

## Escenarios observados

Hora real del log (UTC−3). A ×60, 1 s real son 1 min de control.

| Hora real | Inventario | `fechaHora` (hora de Montevideo) | Qué pasó | ¿Correcto? |
|---|---|---|---|---|
| 18:07–18:09 | — | — | El broker se cayó 2 min 30 s; reintentos cada 5 s; al volver, reconexión y re-suscripción | ✅ |
| 18:23 | `gen-0-holgado-s1` (0 hab.) | martes 3/11 10:00 | INICIAR y DETENER sin habitaciones: no se comanda nada | ✅ |
| 18:25 | `gen-1-holgado-s1` | martes 10:00 | Con la primera lectura, ON (`CALEFACCIONANDO`); 84 s reales después, OFF (`SATISFECHA`) | ✅ |
| 18:36 | `gen-2-limitante-s1` | martes 10:00 | Solo una ON; cuando room1 se satisface, se apaga y prende room2 **en la misma pasada** (OFF antes que ON) | ✅ |
| 18:44 | `gen-5-holgado-s1` | martes 10:00 | Las 5 ON (4,8 ≤ 6,0 kW); se apagan una a una al satisfacerse | ✅ |
| 18:52 | `gen-2-holgado-s1` | martes 10:00 | Las 2 ON; se apagan al satisfacerse | ✅ |
| 19:12 | `gen-5-limitante-s1` | martes 10:00 | Dos ON a la vez; al satisfacerse una, entra la siguiente (room3, después room4) | ✅ (\*) |
| 19:27 | `gen-5-holgado-s1` | **martes 22:00** (`01:00Z`) | Todas OFF durante 60 s reales (= 1 h de control en punta); a las 19:28:23, exactamente las **23:00** de control, las 5 ON | ✅ |

(\*) El log de esa versión no tenía la potencia de cada habitación del escenario limitante, así que
no se puede verificar la suma contra la contratada. Desde T16 el log deja el sitio aplicado y un
resumen con la potencia comprometida (4+1 §3.2).

En cada INICIAR, el controlador consultó los switches antes de comandar (§4): los vio
`DESCONOCIDO` (el simulador los reinicia entre escenarios), los llevó a la decisión vigente y
siguió. Es el comportamiento esperado.

## Problemas encontrados

| # | Problema | Evidencia | Causa | Qué se hace |
|---|---|---|---|---|
| P1 | El registro falló 6 minutos con `401 Unauthorized` | 17:55–18:01 | `ENTORNO_API_KEY` vacía (el compose la deja vacía por defecto) | Configuración. Se documenta en el README de traspaso (T27) |
| P2 | El controlador se registró con una `baseUrl` que el simulador no podía alcanzar: `http://localhost:8080` (18:06) y `http://192.168.148:8080`, con un error de tipeo (18:20). No llegó ningún `PUT /sitio` hasta corregirla (18:21) | 18:06–18:21 | `CONTROLADOR_BASE_URL` con su valor por defecto y después mal escrita. El controlador no avisaba nada: el registro "salía bien" | **T24a**: avisar en el log al registrarse con una `baseUrl` local ante un entorno remoto, o con un host que no es un nombre ni una IP válida |
| P3 | El 96 % del log eran lecturas `[TEMPERATURA]`, lo que hacía difícil revisar la corrida | todo el log | Lecturas en INFO | ✅ Ya corregido en T16: las lecturas van en DEBUG y hay un resumen cada 30 s |
| P4 | Con el control **detenido**, aplicar un inventario decidía ON (19:27:23, room5) | 19:27:23 | La decisión no miraba el estado de ejecución | ✅ Ya corregido en `c8519fb` ("detenido no decide"); no se comandaba ningún switch, solo era la decisión |
| P5 | El log no tenía la potencia ni la franja del sitio aplicado | escenario 19:12 | No se registraba | ✅ Ya corregido en T16 (sitio aplicado en cada `PUT /sitio` e INICIAR) |

## Respuestas a preguntas abiertas

- **¿Cómo llega el modo acelerado? (roadmap L4, 4+1 §4.7).** Como `factorTiempo = 60` en cada
  INICIAR. Es el canal que supusimos, así que la pregunta queda cerrada.
- **¿En qué zona lee el simulador la franja punta? (D4).** En hora de Montevideo: para probar el
  cruce de punta mandó `fechaHora = 2026-11-04T01:00:00Z`, que es 22:00 en Uruguay, una hora antes
  del fin de la punta (23:00). El controlador restituyó exactamente a las 23:00 de control.
- **Escenarios que no se vieron en esta corrida:** switch caído, lecturas atrasadas o con `ts`
  desincronizado y termostato caído. Quedan para la corrida T13 y para los tests de T25.
