# Prueba de aceptación en el laboratorio — 2026-11-09

La prueba de aceptación de la iteración 6, con el simulador de la cátedra y los escenarios nuevos del
docente, en la red del laboratorio. **Resultado informado por el simulador: 10 de 10.**

Se agregó **después del tag `version6`** (rama `post-entrega/doc-aceptacion`). El tag se creó antes
de la aceptación, así que su documentación todavía decía que esta corrida no se había hecho (L19,
T13, T28).

**Qué hay acá:**

- Este resumen: las 35 corridas del log, una por una.
- [`engine-extracto.log`](engine-extracto.log): el log del contenedor desde que arrancó la versión 6
  (19:13) hasta el final (20:15). Se quitaron las líneas `Anticipación de la punta` (2462) y las de
  suscripción MQTT (174); están resumidas abajo.

**El reporte del simulador no se guardó.** El 10/10 es lo que informó la cátedra. Por eso este
resumen sale del log del controlador, y no se puede decir qué corridas forman cada uno de los 10
escenarios.

## Cómo se corrió

- **Simulador** en `192.168.1.105:9094`, broker en `192.168.1.105:1894`.
- **Controlador** en Docker, anunciado como `http://192.168.1.149:8080`. El registro funcionó a la
  primera: `Registrado ante el entorno http://192.168.1.105:9094 como 'ecowarm-grupo4'`.
- Todas las corridas fueron a ×60. La punta era 17:00–23:00 en días hábiles, hora de Montevideo.
- **Cómo se configuró:** las direcciones del laboratorio y la clave se escribieron en
  `docker/docker-compose.yml` en lugar de pasarlas como variables, como dice el traspaso. Ese cambio
  no se commiteó.

## Las dos tandas

| Tramo | Imagen | Corridas | Qué pasó |
|---|---|---|---|
| 18:46–19:12 | **Anterior a la versión 6** (sin T24b, T35 ni T39) | 1–8 | **7032 lecturas descartadas como `TS_VENCIDO` y ninguna decisión ON.** No se calefaccionó en ningún escenario. |
| 19:13–20:15 | Versión 6 | 9–35 | El control funcionó: decide, comanda y nunca pasa la contratada (detalle abajo). |

**Qué pasó en la primera tanda.** El contenedor corría una imagen construida antes de la versión 6.
Se reconoce por dos cosas:

- El identificador largo del cliente MQTT (`ecowarm-grupo4-b384b542-…`), anterior al límite de 23
  caracteres de T35.
- Que no aparece ninguna línea de anticipación, anterior a T39.

Esa imagen convertía siempre el `ts` como hora real. El simulador lo manda en hora del control, así
que cada lectura caía en octubre de 2025 y se descartaba. Es el problema que T24b había corregido.
Se notó por los descartes, se reconstruyó la imagen con `--build` (el traspaso ya advierte que
`--force-recreate` no recompila) y desde las 19:13 corrió la versión 6.

**Nada en el log prueba qué versión corre.** El arranque no informa el commit: eso es L22, y R16 en el
roadmap.

## Las 35 corridas

Columnas:

- **ON**: decisiones ON registradas en la corrida. Solo se loguean los cambios: una habitación que
  ya estaba en ON de la corrida anterior no suma.
- **Potencia**: la mayor potencia comprometida de los resúmenes de cada 30 s, frente a la
  contratada.
- **Punta**: decisiones OFF por `TARIFA_PUNTA`.
- **L20**: switches que quedaron fuera del sitio con ON. Se cuentan en la corrida durante la cual
  llegó el sitio nuevo.

| # | Hora | Sitio | Hab. | Hora del control al iniciar | ON | Potencia (kW) | Punta | ERROR | L20 | Otros |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 18:56 | `gen-0-holgado-s1` | 0 | mar 10:00 | 0 | 0 / 3,7 | | | | Imagen vieja |
| 2 | 18:56 | `gen-1-holgado-s1` | 1 | mar 10:00 | 0 | 0 / 1,7 | | | | Imagen vieja: 121 `TS_VENCIDO` |
| 3 | 18:58 | `gen-2-holgado-s1` | 2 | mar 10:00 | 0 | 0 / 2,7 | | | | Imagen vieja: 240 |
| 4 | 19:00 | `gen-2-limitante-s1` | 2 | mar 10:00 | 0 | 0 / 1,2 | | | | Imagen vieja: 240 |
| 5 | 19:02 | `gen-5-holgado-s1` | 5 | mar 10:00 | 0 | 0 / 6,0 | | | | Imagen vieja: 600 |
| 6 | 19:04 | `gen-5-limitante-s1` | 5 | mar 10:00 | 0 | 0 / 2,4 | | | | Imagen vieja: 900 |
| 7 | 19:07 | `gen-20-holgado-s1` | 20 | mar 10:00 | 0 | 0 / 31,7 | | | | Imagen vieja: 2411 |
| 8 | 19:09 | `gen-20-limitante-s1` | 20 | mar 10:00 | 0 | 0 / 12,7 | | | | Imagen vieja: 2520 |
| 9 | 19:14 | `gen-1-holgado-s1` | 1 | mar 10:00 | 1 | 1,2 / 1,7 | | 1 | | |
| 10 | 19:16 | `gen-2-holgado-s1` | 2 | mar 10:00 | 2 | 2,1 / 2,7 | | 2 | | |
| 11 | 19:18 | `gen-2-limitante-s1` | 2 | mar 10:00 | 2 | 1,2 / 1,2 | | | | |
| 12 | 19:20 | `gen-5-holgado-s1` | 5 | mar 10:00 | 4 | 4,8 / 6,0 | | 1 | | 5 `TS_VENCIDO` (lecturas atrasadas a propósito) |
| 13 | 19:22 | `gen-5-limitante-s1` | 5 | mar 10:00 | 5 | 2,1 / 2,4 | | 1 | | |
| 14 | 19:25 | `gen-20-holgado-s1` | 20 | mar 10:00 | 17 | 25,3 / 31,7 | | 9 | | |
| 15 | 19:27 | `gen-20-limitante-s1` | 20 | mar 10:00 | 20 | 12,7 / 12,7 | | | | 20 `TS_VENCIDO` (lecturas atrasadas) |
| 16 | 19:30 | `gen-50-holgado-s1` | 50 | mar 10:00 | 44 | 64,4 / 80,5 | | | | Anticipación hasta 1249 s |
| 17 | 19:32 | `gen-50-limitante-s1` | 50 | mar 10:00 | 49 | 32,2 / 32,2 | | | 18 | Anticipación hasta 1800 s (el tope); L20 después del DETENER |
| 18 | 19:35 | `gen-5-holgado-s1` | 5 | mar **16:00** | 5 | 4,8 / 6,0 | 5 | | | Corte de la punta a las 16:58:53, unos 67 s antes |
| 19 | 19:37 | `gen-5-holgado-s1` | 5 | mar **22:00** | 5 | 4,8 / 6,0 | 5 | | | Restitución a las 23:00:00 (60 s reales después del INICIAR) |
| 20 | 19:39 | `gen-5-holgado-s1` | 5 | **sáb** 16:00 | 0 | 4,8 / 6,0 | 0 | | | Sábado: no es punta (días hábiles) |
| 21 | 19:41 | `gen-5-holgado-s1` | 5 | mar 10:00 | 5 | 4,8 / 6,0 | | | | |
| 22 | 19:44 | `gen-5-explicita-s1` | 5 | mar 10:00 | 18 | 5,7 / 6,0 | | 1 | | 3 ON retenidos (switch sin confirmar OFF) |
| 23 | 19:47 | `gen-20-explicita-s1` | 20 | mar 10:00 | 7 | 12,4 / 12,7 | | | 9 | Sitio de 20 a 5 **en ejecución** (L20) |
| 24 | 19:49 | `gen-5-holgado-s1` | 5 | mar 06:00 | 0 | 4,8 / 6,0 | | 5 | | |
| 25 | 19:55 | `gen-5-explicita-s1` | 5 | mar 10:00 | 7 | 4,8 / 6,0 | | 2 | | |
| 26 | 19:57 | `e19-switch-encendido` | 5 | mar 10:00 | 2 | 4,0 / 4,5 | | 5 | 1 | L20 después del DETENER |
| 27 | 19:58 | `gen-3-holgado-s1` | 3 | mar 10:00 | 4 | 3,0 / 3,8 | | 2 | | |
| 28 | 20:00 | `gen-3-holgado-s1` | 3 | mar 10:00 | 3 | 3,0 / 3,8 | | 6 | | |
| 29 | 20:02 | `gen-4-holgado-s1` | 4 | mar 10:00 | 2 | 4,0 / 5,0 | | 1 | | |
| 30 | 20:04 | `gen-4-holgado-s1` | 4 | mar 10:00 | 6 | 2,8 / 5,0 | | 40 | | 80 payloads inválidos inyectados (40 en ERROR) |
| 31 | 20:06 | `gen-4-limitante-s1` | 4 | mar 10:00 | 3 | 1,8 / 2,0 | | 4 | | |
| 32 | 20:08 | `gen-4-limitante-s1` | 4 | mar 10:00 | 2 | 1,8 / 2,0 | | 2 | | |
| 33 | 20:10 | `gen-4-holgado-s1` | 4 | mar 10:00 | 3 | 4,0 / 5,0 | | 4 | | |
| 34 | 20:12 | `f08-switch-sin-consulta` | 5 | mar 10:00 | 2 | 4,0 / 4,5 | | 1 | | 1 ON retenido |
| 35 | 20:13 | `gen-4-limitante-s1` | 4 | mar 10:00 | 1 | 1,8 / 2,0 | | 2 | | 1 ON retenido |

En las corridas 20 y 24 no hubo cambios de decisión que loguear. La potencia de los resúmenes es la
que quedaba comprometida.

## Lo que muestra

- **La contratada nunca se pasó** en ningún resumen de las 27 corridas con la versión 6. En los
  escenarios limitantes llegó justo al tope (12,7 / 12,7 y 32,2 / 32,2). Cuando un switch no
  confirmó el OFF, el ON se retuvo (5 veces).
- **La punta, bien en los dos sentidos.** Se cortó un minuto antes de las 17:00 y se restituyó a las
  23:00 en punto. El sábado no hubo punta.
- **89 ERROR, todos de fallas inyectadas:**
  - 40 payloads inválidos de un termostato (corrida 30).
  - 41 consultas a switches que no respondían.
  - 8 del broker: 6 conexiones fallidas y 2 pérdidas de conexión.

  Todas se manejaron como estaba previsto. Que una falla esperada salga como ERROR hace ruido en una
  corrida sin problemas.

## Lo que el 10/10 no mide

- **Calefactores que quedan prendidos (L1, L20): 28 avisos.**
  - 19 vinieron justo después de un DETENER y antes de un sitio más chico: 18 a las 19:35 (de 50
    habitaciones a 5) y 1 a las 19:58. Con D9 esos switches ya habían quedado como estaban.
  - 9 fueron con el control en ejecución, a las 19:48, cuando llegó un sitio de 5 habitaciones en
    lugar de 20 (A20).

  El simulador no lo penaliza porque reinicia los switches entre escenarios. En una casa serían
  calefactores sin supervisión. Los resuelven R1 (y R1 + E16 para el segundo caso).
- **La anticipación de la punta (A21, L21).** Con las latencias de la red del laboratorio (200 a 430
  ms por llamada), la anticipación fue de 68 s a 1800 s de control, y llegó al tope 2 veces con 50
  switches. Además oscila: con 5 habitaciones pasó por 136, 76, 149, 82, 157 y 96 s en 26 s. El
  escenario de 50 habitaciones empezó a las 10:00 y no cruzó la punta. El que la cruzó (corrida 18)
  cortó 67 s antes. Esto corrige la corrida en Docker, que decía que A21 "no cambia nada en una red
  normal": en esa red, la del mismo equipo, los switches responden en milisegundos. Ajustarla es R17.
