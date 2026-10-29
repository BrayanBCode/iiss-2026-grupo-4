# Grupo 4 — Iteración 5: cambios para llegar a un proyecto excelente (versión 2)

Este documento reemplaza a `cambios-para-excelencia.md` y parte de la versión actualizada del 2026-10-28. Cada cambio dice **qué** hacer, **por qué** y **cuándo se considera hecho**. Los bloques van por prioridad:

- **A:** lo que falta para que la entrega sea coherente.
- **B:** lo que la hace sólida.
- **C:** lo que la hace destacar.

La tarjeta entre paréntesis es la de `revision-docente.md`, cuando ya existe.

**Dónde está parado el proyecto.** El diseño ya es bueno: CI completa, cobertura exigida, core puro. Lo que falta es **terminar lo que el propio equipo diagnosticó** (zona, `ts`, vigencia, tick, re-sincronización) y **volver a alinear toda la evidencia escrita con el código**. Los cambios del bloque A son chicos en código; el riesgo está en entregar sin hacerlos.

---

## Estado del plan anterior

| Cambio | Estado | Comentario |
|---|---|---|
| A1 CI con `mvn verify` | ✅ Hecho (T1, T1b) | Falta solo el enlace a la corrida verde del tag (A6) |
| A2 Cobertura exigida | ✅ Hecho | Líneas y ramas ≥ 85 % en el core |
| A3 Core sin estado | ✅ Hecho (T4, T5) | Queda la cobertura de lo que se mudó al engine (B4) |
| A4 Una bandera y camino corto | ✅ Hecho (T7, T7b) | — |
| A5 Usar el `ts` | 🟡 Parcial (T8) | Parseo exacto hecho; la conversión está apagada por defecto (A2 de este plan) |
| A6 Zona horaria | ❌ Pendiente (T9) | A1 de este plan |
| A7 `revision-docente.md` como registro | 🟡 Parcial | Tiene estados, pero mezcla plan, registro y notas internas (A7 de este plan) |
| B1 Tiempo acelerado | ❌ Diagnosticado, no corregido | A3–A5 de este plan |
| B2 Corrida contra el simulador | ❌ Pendiente (T13) | B1 de este plan |
| B3–B9, C1–C3 | ❌ Pendientes | Se mantienen abajo, actualizados |

---

## A. Para que la entrega sea coherente

### A1. Zona horaria del sitio, sin `systemDefault()` (T9)
- **Qué:**
  - `control.zona-horaria=${CONTROL_ZONA_HORARIA:America/Montevideo}` en `application.properties`, y `CONTROL_ZONA_HORARIA=America/Montevideo` explícita en `docker-compose.yml`;
  - en `ControlService`, la zona pasa a ser un `ZoneId` obligatorio, no un `Optional`. Desaparecen el parámetro `zonaDeFechaHora` de `iniciar` y la rama `zonaFija.orElse(ZoneId.systemDefault())` de `aplicarInventario`;
  - en `ControlController`, una `fechaHora` sin offset se interpreta en la zona del sitio.
- **Por qué:** es una observación del docente y él ya confirmó la respuesta (Uruguay).
- **Hecho cuando:**
  - `grep -rn systemDefault modules/` no devuelve nada;
  - hay un test de integración: INICIAR con `2026-10-01T21:00:00Z` (jueves 18:00 en Montevideo) → `TARIFA_PUNTA`; y otro con `fechaHora` sin offset que se interpreta en Montevideo.

### A2. `ts` en hora real, convertido siempre (T8b)
- **Qué:**
  - eliminar `termostato.ts-tiempo-real` (y `TERMOSTATO_TS_TIEMPO_REAL`) junto con la rama que toma el `ts` sin convertir. El instante de la lectura pasa a ser `reloj.aTiempoDeControl(ts)`;
  - las reglas quedan así:
    - `ts` ausente, o futuro ya convertido → `ahora`;
    - `ts` anterior a la última lectura → se descarta;
    - `ts` convertido más viejo que la vigencia → **se descarta** (D8) y se cuenta en `/diagnostico` (un contador por habitación y motivo).
- **Cuidado con la interacción de reglas:** una lectura guardada con `ahora` (por un desfase de reloj) no puede bloquear las siguientes. Hay dos formas de resolverlo; elegir una y documentarla:
  - **(a)** comparar el orden por `ts` **original** (convertido), no por el instante con que se guardó;
  - **(b)** guardar con `ahora` solo para la vigencia, pero el orden igual por `ts`.
- **Tests:**
  - factor 2: INICIAR 13:00 con `fechaHora = 13:00`; `ts` 13:05 real ⇒ 13:10;
  - con `fechaHora = 08:00` ⇒ 08:10;
  - `ts` futuro;
  - `ts` viejo dentro de la vigencia y fuera de ella;
  - `ts` anterior a la última;
  - **desfase de reloj de +2 s a ×30** con varias lecturas seguidas, verificando que ninguna se descarta por error.
- **Hecho cuando:** con el simulador, `/diagnostico` muestra `ultimaLectura` igual al `ts` convertido de los mensajes (smoke de A9 en `revision-docente.md`).

### A3. Vigencia en tiempo simulado (T8c)
- **Qué:** `Core.vencida` compara `ahora − ultimaLectura` contra la vigencia **sin multiplicar** por el factor. `factorTiempo` sale de la firma de `decidir` y de `EstadoHabitacion`, si estaba ahí. Se corrigen los javadocs de `ParametrosControl` y de `RelojControl.instanteDeLectura` (este último dice "se escala con el factor, igual que en el core").
- **Por qué:** a ×30, hoy ninguna lectura vence en 5 h simuladas, así que el caso "termostato que deja de funcionar" no se detecta en la prueba acelerada.
- **Elegir y justificar la vigencia en minutos simulados.** Por ejemplo, 3 veces el período de publicación esperado del simulador. Si el simulador publica cada 5 min simulados, 15 min.
- **Hecho cuando:** existe un test en `DecidirTest`: "a ×30, una lectura de hace 601 s de tiempo de control está vencida". `TermostatoCaidoTest.tiempoAcelerado` se reescribe con la regla nueva (hoy verifica lo contrario).

### A4. Re-sincronización sin revivir lecturas (D7)
- **Qué:** en `CoreAdapter.sincronizarReloj`, conservar solo las lecturas que **no estaban vencidas** con el reloj anterior, y re-sellarlas con la hora nueva. Las vencidas se borran: la habitación queda `SIN_LECTURA` hasta que llegue una nueva.
- **Por qué:** si el paso a acelerado llega como un nuevo INICIAR, hoy los datos viejos reviven justo al empezar la prueba final.
- **Hecho cuando:** existe un test en el que una lectura vencida antes del INICIAR queda `SIN_LECTURA` después del INICIAR, y una vigente sigue contando.

### A5. Tick escalado con el factor (T12)
- **Qué:** período del tick = `max(10 ms, tickMs / factor)`, reprogramado en cada INICIAR. La variante superior es agendar un evento en el **próximo borde de franja**, calculado en tiempo real.
- **Hecho cuando:**
  - un test de integración a ×600 arranca 5 min simulados antes de la punta y verifica el corte en menos de 1 s real, y después la restitución;
  - el error máximo del corte, en tiempo simulado, está escrito en el 4+1.

### A6. Evidencia escrita alineada con el código
Es el cambio más importante en relación con lo que cuesta. Hay que regenerar **desde una corrida real** (no a mano):
- **`criterio-tests.md`:**
  - título "Iteración 5";
  - explicar el criterio del diseño actual: `DecidirTest` como TDD de la función de decisión, `CoreAdapterEstimulosTest` como TDD de la reacción a estímulos y por qué vive en el engine;
  - la matriz **requisito → test**, con las 6 preguntas del objetivo de la letra de la iteración 5 y los 3 casos mínimos de integración;
  - sacar las referencias a `Core.procesar`, `CoreTest` y 226/227.
- **`resultados.md`:** las tablas por clase con los conteos de la última `mvn verify`; sacar `CoreTest` y `CoreClientTest`.
- **README:** la tabla de tests, la cobertura (100 % / 94 %) y la sección "Cómo decide el core" (el descarte de lecturas ahora lo hace el engine).
- **`spotless-check.txt`:** una corrida nueva sobre el código del tag.
- **`docker-compose.yml`:** el encabezado "Iteración 5" y la zona explícita (A1).
- **Corrida de la CI:** el enlace a la corrida verde sobre el commit del tag, en el README y en `docs/calidad/README.md`. Si se quiere, un badge de estado.
- **Hecho cuando:** `grep -rn "CoreTest\b\|CoreClient\|procesar\b.*Core\|215\|116" README.md docs/` solo devuelve menciones históricas marcadas como tales.

### A7. Sacar los documentos internos del entregable
- **Qué:**
  - `docs/cambios-excelencia-iter5.md`: sacarlo del repositorio (pasarlo a Jira o a una wiki), o reemplazarlo por un `CHANGELOG.md` que diga qué se cambió y por qué, en primera persona del equipo;
  - `revision-docente.md`: separar
    - **(a)** un `docs/revision-docente.md` corto, que sea **registro**: observación → tarjeta → commit o PR → test que lo demuestra → estado;
    - **(b)** la sección 5 (relojes y decisiones), que se mueve al 4+1 (T14);
    - **(c)** el tablero, las políticas y las notas de proceso, que van a Jira.
  - sacar del documento técnico los nombres de usuario y los "se incumplió".
- **Por qué:** un documento que dice que "la entrega afirma más de lo que demuestra" se va a leer como autoevaluación de esta versión.

---

## B. Para que la iteración 5 quede sólida

### B1. Corrida contra el simulador nuevo, con evidencia (T13)
- Un script `scripts/integracion-simulador.sh` que:
  - levanta el compose `lab`, espera el registro, carga el sitio y hace INICIAR (o espera el del simulador);
  - guarda `/diagnostico` y `/control` cada N segundos en `docs/tests/corrida-simulador/`.
- Escenarios mínimos:
  1. carga no limitante;
  2. carga limitante;
  3. cruce de franja **acelerado**;
  4. broker caído y recuperado;
  5. switch caído;
  6. termostato caído con factor alto (valida A3).
- En `resultados.md`, una tabla esperado/observado por escenario.
- **Cerrar la pregunta abierta:** cómo llega el paso a acelerado. Si llega sin INICIAR, hay que detectarlo (por ejemplo, por el registro §6 o por un campo nuevo del contrato) y documentarlo.

### B2. Arranque, parada y reinicio, decididos y probados
- **DETENER:**
  - **(a)** una opción `control.al-detener=APAGAR|DEJAR` con default `APAGAR`, y sus tests;
  - **(b)** mantener `DEJAR` con un análisis de riesgos escrito: calefacción sin control, consumo en punta, potencia.
- **Reinicio del contenedor:** persistir el estado del control (en marcha, `fechaHora` de referencia, `iniciadoEn`, factor) junto al inventario y retomar solo, consultando antes los switches (§4). O decidir arrancar DETENIDO y documentar el riesgo. En los dos casos, un test.
- **Apagado ordenado (`@PreDestroy`)** coherente con lo que se elija.

### B3. Dividir `CoreAdapter`
- **Qué:** separar sus responsabilidades sin cambiar el comportamiento:
  - `SituacionControl`: temperaturas, instantes, sitio, `ahora`, la regla del `ts` y el procesamiento de estímulos. Es testeable sin red;
  - `ComandoSwitches`: pasadas, OFF antes que ON, retención del ON, esperas de reintento y reconciliación;
  - `CoreAdapter` queda como fachada fina que orquesta `SituacionControl → core.decidir → ComandoSwitches` y guarda el estado de ejecución.
- **Por qué:** con 561 líneas y cinco responsabilidades, es donde terminó el sobrediseño que marcó el docente.
- **Hecho cuando:** ninguna clase pasa de unas 250 líneas, los tests siguen en verde y el diagrama de clases del engine en el 4+1 está actualizado.

### B4. Cobertura y calidad de los tests más allá del core
- Medir con JaCoCo también el engine y exigir un umbral razonable (por ejemplo, ≥ 80 %) **al menos para el paquete `adaptador`**, donde ahora vive la lógica de estímulos.
- Agregar mutation testing con PIT sobre `core` y sobre el paquete `adaptador`, y reportar el *mutation score*. Es la respuesta a "¿100 % de líneas prueba que los tests son buenos?".
- Revisar los tests de integración con esperas: helpers con condición y timeouts configurables. Correr la suite 10 veces seguidas y dejar constancia de 0 fallas intermitentes.

### B5. MQTT y switches: decisiones explícitas
- Documentar `cleanSession=true` con QoS 1 (las lecturas son "último valor").
- Un test que haga PUT /sitio con el broker caído y verifique que, al volver, se suscribe a los topics nuevos.
- Documentar o medir el tiempo de una pasada con N switches lentos (son secuenciales), y agregar un test de switch **lento** (responde a los 4 s), no solo caído.

### B6. Estándar de codificación verificable (T2, T10)
- Checkstyle (Google, ajustado a AOSP) en `mvn verify` para lo que Spotless no cubre: llaves, nombres, comodines, largo de métodos. Evidencia en `docs/calidad/`.
- Terminar T2 (sección "Java que usamos" + `guia-java.md`) y T10 en el resto del engine, no solo en `CoreAdapter`.
- En el estándar, cada regla con la herramienta que la verifica, o "se revisa en el PR".

### B7. 4+1 completo
- **Vista física:** contenedor del controlador, simulador (broker, switches, inventario), redes (`iiss-lab-net` frente al anfitrión), puertos, volumen `inventario`, y quién inicia cada conexión.
- **Vista de desarrollo:** un diagrama de paquetes con las dependencias permitidas, idealmente verificado con ArchUnit (core sin dependencias; solo `control` usa `adaptador`).
- **Relojes y tiempo (T14):** mover acá la sección 5 de `revision-docente.md`, actualizada después de A2–A5.
- **Escenarios nuevos:** INICIAR acelerado con cruce de franja; re-sincronización; DETENER y reinicio.
- **Revisión final (T11)** contra el código del tag, diagrama por diagrama.

### B8. Cerrar el tablero
- Marcar T3 como hecha: el test de OFF antes que ON sigue en `CoreAdapterTest:414`.
- Desde ahora, una rama por tarjeta (D5), y el tablero se actualiza en el mismo PR.

---

## C. Para destacar

### C1. Servidor MCP (opcional de la letra)
Con el core puro, es el momento de demostrar la modularidad que pide la letra:
- un módulo `modules/mcp` con Spring AI (`spring-ai-starter-mcp-server-webmvc`, Streamable HTTP), desplegado como otro servicio del compose;
- es un adaptador delgado sobre el API REST existente, sin tocar el core ni `CoreAdapter`;
- herramientas: `listar_habitaciones`, `ver_habitacion`, `configurar_habitacion`, `accionar_switch`, `estado_controlador`, `iniciar_control`, `detener_control`;
- entregables: `docs/mcp/herramientas.md`, `docs/mcp/rest-vs-mcp.md` y una demo con MCP Inspector.

### C2. Observabilidad
- Actuator con métricas: lecturas descartadas por motivo (útil para D8), comandos OK o fallidos, potencia comprometida frente a la contratada, estado de MQTT.
- `/actuator/health` como `healthcheck` del compose.

### C3. Documentación de entrega
- `CHANGELOG.md` por iteración: qué cambió, qué observación atendió y qué test lo demuestra.
- En el README, la sección "Cómo verificar esta entrega en 5 minutos".

---

## Orden sugerido

| Orden | Cambios | Motivo |
|---|---|---|
| 1 | A1 | Chico, y es una observación del docente ya respondida |
| 2 | A2 → A3 → A4 → A5 | En serie: tocan `RelojControl`, `ControlService`, `CoreAdapter` y `Core` |
| 3 | B1 | Corrida contra el simulador con el tiempo ya corregido |
| 4 | A6, A7 | Regenerar la evidencia desde la última corrida, y limpiar el repositorio |
| 5 | B2, B5, B8 | Decisiones de arranque y parada, endurecimiento, tablero |
| En paralelo | B6, B7 | Sin conflicto con el código |
| Si hay tiempo | B3, B4, C1–C3 | B3 conviene antes de C1 |
| Al final | Tag `version5` | El zip de Moodle idéntico al tag, con la CI verde sobre ese commit |

## Checklist final antes del tag

- [ ] Sin `systemDefault()`; `America/Montevideo` en properties y compose; test de punta con `...Z`.
- [ ] `ts` convertido siempre; sin la propiedad `termostato.ts-tiempo-real`; test de desfase de reloj.
- [ ] Vigencia en tiempo simulado; `factorTiempo` fuera de `decidir`; termostato caído detectado a ×30.
- [ ] INICIAR no revive lecturas vencidas.
- [ ] Tick (o borde de franja) escalado; corte de punta probado a ×600.
- [ ] Corrida contra el simulador con evidencia, y la pregunta del paso a acelerado cerrada.
- [ ] README, `criterio-tests.md`, `resultados.md` y `spotless-check.txt` regenerados desde la última `mvn verify`; enlace a la CI verde.
- [ ] Sin `cambios-excelencia-iter5.md` en `docs/`; `revision-docente.md` como registro.
- [ ] 4+1 con un diagrama por vista, incluida la física, y la sección de tiempo.
- [ ] DETENER y reinicio decididos, documentados y testeados.
