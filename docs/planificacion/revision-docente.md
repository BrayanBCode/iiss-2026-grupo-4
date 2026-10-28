# Revisión del código según el docente (iteración 5)

Planificación en **Scrumban**, como pide la iteración 4: se planifica al inicio de la iteración, se trabaja con un tablero de flujo continuo y se revisa cada semana con el Product Owner. La primera parte es para la reunión del equipo y el tablero. El [anexo](#anexo-detalle-técnico) tiene el detalle técnico de cada tarjeta.

## 1. Planificación

### Objetivo
Corregir lo que marcó el docente sin romper nada de la iteración 5 (215 tests en verde y cobertura del core ≥ 85 %), y dejar el código más fácil de leer para el equipo **antes** de cerrar la documentación de la entrega.

### Lo que marcó el docente
| Observación | Tarjeta |
|---|---|
| Apagar antes de encender | T3 (ya está; se verifica) |
| El `ts` se redondea o se pierde | T8, T8b, T8c |
| `-DskipTests` en GitHub Actions | T1 |
| Sobrediseño: estado cacheado en core y engine | T4, T5 |
| Core con estado | T4, T5 |
| Zona horaria | T9 |
| Saltos de más para que una lectura MQTT llegue al core (detectado por el equipo) | T5, T7 |
| Código difícil de leer para el equipo (detectado por el equipo) | T2, T10 |

### Decisiones para la reunión
Hay que tomarlas antes de pasar las tarjetas a "Listo":

| # | Decisión | Propuesta | Resultado |
|---|---|---|---|
| D1 | ¿El core queda sin estado? | **Sí**: solo `decidir` y el modelo (sugerencia del docente) | **Sí** (aplicada en `67fe9a9`) |
| D2 | ¿`Estimulo` se borra (A) o queda como modelo, procesado en el engine (B)? | **B**: es lo que pide la letra de la iteración 4 ("modelo de los estímulos") | **B** (aplicada en `67fe9a9`) |
| D3 | ¿El `ts` del termostato es el instante de la lectura? | **Sí**. Llega en hora real y se convierte a la hora del control con `fechaHora + (ts − iniciadoEn) × factorTiempo`: la multiplicación es sobre el tiempo transcurrido desde el INICIAR, no sobre la hora entera (ver la [sección 5](#5-tiempo-del-sistema-letra-y-decisiones)). Si falta o, ya convertido, queda en el futuro respecto de `ahora`, se usa `ahora` | **Sí**, con la regla nueva (actualizada: `ts` en hora real, se convierte a la hora del control) |
| D4 | ¿Zona horaria fija del sitio? | **Sí**, `America/Montevideo`, configurable. Hay que confirmarlo con el contrato del simulador nuevo | **Sí**. Uruguay es obligatorio para el equipo |
| D5 | ¿Una rama por tarjeta, con PR a `implementacion5`? | **Sí**, así cada tarjeta se revisa por separado | **Sí**. Recordatorio: se incumplió en `67fe9a9` (un solo commit); se aplica desde ahora |
| D6 | ¿Qué clase administra el estado de ejecución (en ejecución / detenido)? | `CoreAdapter` administra el estado de ejecución (`iniciar()` / `detener()` / `enEjecucion()`, protegida por su propio `synchronized`); `ControlService` le delega y `enEjecucion()` devuelve `adaptador.enEjecucion()`. Se eliminan el `ObjectProvider` y el `BooleanSupplier` de `CoreConfig`: eran un parche para esquivar el ciclo y no son intuitivos | Decidido (Sí), implementado en T7b (rama `revision/t7b-estado-ejecucion`) |
| D7 | ¿Qué hacer con las lecturas guardadas al re-sincronizar (INICIAR)? | Conservar solo las que no estaban vencidas. Hoy todas se sellan con la nueva hora y un dato viejo vuelve a contar como reciente | **Pendiente** (decisión del equipo) |
| D8 | ¿Una lectura con `ts` más viejo que la vigencia se descarta? | **Sí** (se implementa en T8b); la lectura se cuenta en `/diagnostico` | **Implementada en T8b** (rama `revision/t8b-ts-hora-real`); falta que el equipo la confirme |

### Fuera de alcance de esta planificación
La vista física del 4+1, el tag `version5` y el MCP opcional. Siguen en el backlog de la entrega.

## 2. Tablero

| Backlog | Listo | En curso | Revisión | Hecho |
|---|---|---|---|---|
| Tarjetas sin responsable o con dependencias abiertas | Dependencias cerradas y decisiones tomadas | Con responsable | PR abierto, `mvn verify` en verde, esperando a otro integrante | Mergeado en `implementacion5` |

### Políticas
- **WIP:** como máximo **2** tarjetas en curso en todo el equipo, y **solo 1** de las que tocan `CoreAdapter` o `ControlService` (carril A). Esas tarjetas editan los mismos archivos y chocarían.
- **Listo** (Definition of Ready): sus dependencias están en "Hecho" y la decisión que la afecta está tomada.
- **Hecho** (Definition of Done):
  - `mvn verify` en verde (Spotless, tests y cobertura del core ≥ 85 %);
  - otro integrante revisó el PR;
  - commit con formato `módulo: descripción`;
  - los comentarios afectados están actualizados.
- **Pull:** quien termina toma la próxima tarjeta de "Listo" en el orden del carril; nadie asigna tarjetas.
- **Tablero al día:** la columna Estado del tablero (y Jira) se actualiza en el mismo PR de cada tarjeta.

## 3. Tarjetas

Tamaño: S (menos de medio día), M (alrededor de un día), L (más de un día).

**Estado al 2026-10-28 (commit `67fe9a9`).** Ese commit (lucas-811, "Implementacion codigo, test y 4+1", 68 archivos) cerró varias tarjetas de una vez, sin rama por tarjeta (incumple D5) y sin actualizar el tablero. Lo de la tabla está verificado contra el código; `mvn -B verify` sobre `67fe9a9` todavía no lo corrimos nosotros.

| ID | Tarjeta | Estado | Carril | Depende de | Tamaño | Criterio de aceptación |
|---|---|---|---|---|---|---|
| T1 | CI con `mvn verify`, sin `-DskipTests` | **Hecho** | B | — | S | El workflow corre un solo `mvn -B verify` y pasa en el PR. Hecho: un solo `verify`, artefactos, dispara en `implementacion*` y tags `version*` (cubre también A1 y A2 de excelencia: JaCoCo `check` ≥ 0.85 en líneas y ramas) |
| T1b | CI: ignorar cambios de documentación y verificar PR apilados | **Hecho** | B | T1 | S | `paths-ignore` (`docs/**`, `**/*.md`, `LICENSE`) en `push` y `pull_request`: un cambio que solo toca documentación no dispara el build; los tags `version*` corren siempre. `pull_request` también hacia `revision/**`, así los PR apilados entre tarjetas tienen CI. Si `build` pasa a ser check obligatorio, revisar que los PR solo de documentación no queden bloqueados |
| T2 | Reglas "Java que usamos" + `docs/calidad/guia-java.md` | **Pendiente** | B | D2 | S | El estándar tiene la sección y la guía explica cada técnica que se mantiene con un ejemplo del proyecto |
| T4 | El estado pasa a `CoreAdapter`, que llama a `core.decidir` | **Hecho** | A | D1, D2 | L | `CoreAdapter` guarda temperaturas, instantes, sitio y reloj; ya no usa `procesar` ni `estadoById`. Los 215 tests en verde |
| T5 | `Core` queda solo con `decidir`; se migran los tests de `CoreTest` | **Hecho** | A | T4 | L | Ya no existen `procesar`, `CoreClient` ni `ICoreClient`. Los tests de decisión están en `DecidirTest` y los de estímulos en `CoreAdapterEstimulosTest`. Cobertura del core ≥ 85 % |
| T3 | Verificar OFF antes que ON después del refactor | **Pendiente** | B | T5 | S | El test de `CoreAdapterTest` (OFF antes que ON) sigue en verde y no cambió |
| T7 | Una sola bandera de estado de ejecución y un solo try/catch en la lectura MQTT | **Hecho** | A | T5 | S | `ControlService` y `CoreAdapter` no duplican el estado de ejecución. Una lectura recorre 5 pasos. Hecho con `BooleanSupplier` vía `ObjectProvider<ControlService>` y `alRecibirLectura` `synchronized`; ese parche se reemplaza en T7b (ver D6) |
| T7b | CoreAdapter administra el estado de ejecución | **Hecho** | A | T7 | S | (a) `grep -rE "ObjectProvider\|BooleanSupplier" modules/engine/src/main` no devuelve nada; (b) `CoreAdapterTest` sin `AtomicBoolean`, con un test nuevo: después de `adaptador.detener()` una lectura que provocaría ON no comanda ningún switch, y después de `iniciar()` sí; (c) siguen en verde `ArranqueParadaTest` y el test asíncrono de `CoreAdapterTest`; (d) `mvn -B verify` en verde; (e) comentario en `ControlService` que diga que el `reloj` solo da la hora y NO indica si está detenido (para no reintroducir dos banderas) |
| T8 | `ts` del termostato como instante de la lectura | **Parcial** | A | T7 | M | El `ts` se parsea exacto. Hay tests de `ts` exacto, `ts` viejo (descartada) y `ts` futuro (se usa el reloj). Hecho: parseo exacto con `BigDecimal`, `RelojControl.instanteDeLectura`/`aTiempoDeControl`, descarte de `ts` anterior al último. Falta: ver T8b y T8c |
| T8b | `ts` en hora real convertido a la hora del control y regla del `ts` viejo | **Hecho** en la rama `revision/t8b-ts-hora-real` (sin PR todavía): conversión siempre activa, propiedad eliminada, `ts` vencido descartado y contado por motivo en `/diagnostico` (`TS_VENCIDO`, `FUERA_DE_ORDEN`), tests de los cinco casos más desfase de ±2 s a ×30 | A | T7b | S | La conversión del `ts` se aplica siempre: se elimina la propiedad `termostato.ts-tiempo-real` (y `TERMOSTATO_TS_TIEMPO_REAL`) y la rama que toma el `ts` sin convertir. `ts` ausente o, ya convertido, en el futuro → `ahora`; `ts` anterior a la última lectura o más viejo que la vigencia → se descarta y se cuenta en `/diagnostico` (D8). Tests: conversión con factor 2 (INICIAR 13:00, ts 13:05 ⇒ 13:10), `ts` futuro, `ts` viejo dentro de la vigencia, `ts` viejo fuera de la vigencia (descartada) y `ts` anterior a la última. Actualizar `4mas1.md` (§2.2 párrafo del `ts`). |
| T8c | Vigencia en tiempo simulado | **Hecho** (rama `revision/preparacion-simulador`) | A | T8b | S | `Core.vencida` deja de multiplicar la vigencia por el factor y `factorTiempo` sale de `Core.decidir`; javadoc de `ParametrosControl` corregido. Test: con factor 30, una lectura de hace 601 s de tiempo de control está vencida Actualizar `4mas1.md` (§1.3, §2.2 y §3.1, vigencia). |
| T9 | Zona horaria fija del sitio | **Hecho** en la copia local (sobre `revision/t8b-ts-hora-real`, sin commitear; rama prevista `revision/t9-zona`): sin `systemDefault()` ni `zonaDeFechaHora`; zona fija del sitio (`Sitio.ZONA_URUGUAY` si la propiedad viene vacía); `fechaHora` sin offset en hora de Montevideo; los tests de integración corren en Montevideo; tests de 21:00Z (punta), 18:00Z (no punta) y sin offset | A | T8, D4 | M | No se usa `ZoneId.systemDefault()` (hoy en `ControlService.java:175` y `ControlController.java:66`). `CONTROL_ZONA_HORARIA` con valor en `application.properties` y `docker-compose.yml`; sin parámetro `zonaDeFechaHora`; `TZ` en el contenedor; `Sitio.ZONA_URUGUAY` como default. Un test de INICIAR `...Z` a las 21:00 UTC aplica la punta (`TARIFA_PUNTA`) de las 18:00 de Montevideo |
| T10 | Simplificar streams, ternarios y `::` en el engine | **Parcial** | A | T9, T2 | M | Se cumplen las reglas de T2. Los tests no cambian y siguen en verde. Hecho solo en `CoreAdapter` |
| T11a | 4+1 al día con el diseño actual (tras T4, T5, T7 y T7b) | **Hecho** | B | T7b | S | Vistas lógica, de escenarios, de procesos y de desarrollo sin `CoreClient`, `Core.procesar` ni `habilitar()`; el core contiene el modelo y la función pura `decidir`, y el estado lo guarda `CoreAdapter`. El párrafo del `ts` refleja que llega en hora real, que hoy la conversión depende de una propiedad en `false` y lo que queda pendiente para T8b y T8c |
| T11 | Documentación final: 4+1, cobertura y resultados | **Pendiente** | B | T10 | M | Revisión final del `4mas1.md` contra el código del tag `version5`, vista por vista y diagrama por diagrama, para que no quede ninguna discrepancia (clases, firmas, escenarios, hilos, paquetes, reglas del tiempo). Vista física agregada. `docs/calidad/cobertura/` regenerado. `resultados.md` con los conteos de la última corrida. Diagramas Mermaid sin `;` en los textos |
| T12 | Tick escalado con el factor | **Pendiente** | A | T9 | S | Período del tick = `max(10 ms, tickMs / factor)` (hoy `scheduleWithFixedDelay(tickMs)` en `ControlService.java:251`). Error máximo documentado en `4mas1.md`; error máximo del corte de la franja ≤ un tick en tiempo simulado. Test con factor alto que corte la punta en menos de 1 s real |
| T13 | Corrida contra el simulador con evidencia | **Parcial** (script `scripts/captura-simulador.sh` y checklist `docs/tests/corrida-simulador/README.md` listos; falta la corrida) | B | T9, T12 | M | Según B2 de `cambios-excelencia-iter5.md`: script de integración, corridas (carga no limitante y limitante, cruce de franja acelerado, broker caído, switch caído) y tabla esperado/observado en `resultados.md` |
| T14 | Documentar decisiones sobre el tiempo | **Pendiente** | B | — | S | Sección en `4mas1.md` con los 3 relojes y la tabla letra vs decisión (la de la [sección 5](#5-tiempo-del-sistema-letra-y-decisiones)) |

### Orden y paralelismo
```
Hecho (67fe9a9):              T1, T4, T5, T7
Hecho (T7b):                  T7b
Carril A restante (de a uno): T8b → T8c → T9 → T12 → T10 (resto)
Carril B (en paralelo):       T2, T3, T14 ya
                              T13 tras T12 · T11 al final (revisión completa del 4+1)
```
T8b, T8c, T9 y T12 van en serie porque tocan los mismos métodos de `ControlService`, `CoreAdapter` y `Core`.

Con 3 o 4 integrantes: una persona lleva el carril A, otra toma T1 y T2 y después revisa los PR del carril A, y el resto avanza con la vista física del 4+1 (fuera de esta lista).

## 4. Revisión semanal con el Product Owner
- **Qué mostrar:** el tablero, el diff del core (antes y después) y el camino de una lectura reducido de 10 a 5 pasos.
- **Qué validar con el docente:** que la opción de `Estimulo` (D2) cumple con el "modelo de los estímulos" que pide la letra, y la zona horaria del simulador (D4).
- **Qué ajustar:** si el carril A se atrasa, T10 pasa a la iteración 6. Las tarjetas T4, T5, T8 y T9 son las que corrigen lo que marcó el docente.

## 5. Tiempo del sistema: letra y decisiones

Hay tres relojes y cada uno tiene un único trabajo:

```
(a) reloj del sistema   Clock relojReal = Clock.systemUTC()   (CoreConfig)
        |  solo mide duraciones (y esperas de reintento)
        v
(b) reloj del control   RelojControl
        |  ahora = fechaHora + (tiempoRealTranscurrido × factorTiempo)
        |  ÚNICA fuente de "qué hora es" para decidir (tarifa, vencimiento, ticks)
        ^
(c) ts del termostato   solo fecha la medición; nunca mueve la hora del control
                        (hora real del simulador; se convierte a la escala de (b))
```

**Llegada en tiempo real, hora tomada del contexto.** El tiempo del sistema es simulado: el simulador genera las lecturas y, en algún momento, pasa a modo acelerado enviando el factor (por ejemplo, 10 min de ejecución emulan 5 h, factor 30). Los mensajes MQTT llegan y se procesan en tiempo real, pero los intervalos entre ellos son inconsistentes, porque el simulador provoca a propósito switches caídos, un shelly desincronizado, lecturas atrasadas y velocidad acelerada. Por eso el controlador **no** se basa en la hora de la máquina donde corre, sino en el contexto que recibe: el reloj del control (`fechaHora` + tiempo transcurrido × factor) y el `ts` de cada lectura, que llega en hora real y se convierte a la hora del control con esa misma fórmula (ver la regla del `ts`). El reloj del sistema solo se usa para medir cuánto tiempo pasó desde el INICIAR.

**Fórmula** (estándar v2 §5.2), válida desde el INICIAR:

```
ahora = fechaHora + (tiempoRealTranscurrido × factorTiempo)
tiempoRealTranscurrido = Duration.between(iniciadoEn, relojReal.instant())
```

`iniciadoEn` es la hora del sistema en el momento en que llega el INICIAR. Un INICIAR repetido re-sincroniza: se vuelve a fijar `fechaHora`, `factorTiempo` e `iniciadoEn`.

**Regla del `ts`.** El simulador envía el `ts` en **hora real** (epoch del reloj real de su máquina). El controlador lo lleva a la hora del control con la misma fórmula del §5.2:

```
tsControl = fechaHora + (ts − iniciadoEn) × factorTiempo
```

donde `iniciadoEn` es la hora real en que llegó el INICIAR. La multiplicación se aplica al **tiempo transcurrido**, no a la hora entera. Ejemplo: INICIAR a las 13:00 reales con `fechaHora = 13:00` y factor 2; una lectura con `ts = 13:05` reales son 5 min transcurridos × 2 = 10 min, así que su hora del control es 13:10. Si `fechaHora` fuera 08:00, daría 08:10.


- Si el `ts` falta o queda en el futuro respecto de `ahora`, se usa `ahora`. La temperatura nunca se descarta por eso.
- Un termostato adelantado no puede adelantar el sistema: el `ts` no toca el reloj del control.
- Las lecturas con `ts` anterior a la última guardada se descartan (T8).
- Una lectura con `ts` más viejo que la vigencia se descarta y se cuenta en `/diagnostico` (T8b, D8: implementado; falta que el equipo confirme D8).
- La vigencia se mide en tiempo de control (simulado), sin escalarla por el factor (T8c). El `ts` no se escala para esto: primero se convierte y luego se compara con `ahora`.
- Hoy el código ya tiene la conversión (`RelojControl.aTiempoDeControl`), pero está detrás de la propiedad `termostato.ts-tiempo-real`, que vale `false`: por defecto el `ts` se toma sin convertir, lo cual es incorrecto. T8b la deja siempre activa.

### Qué dice la letra y qué decidimos nosotros

La letra son los PDF de las iteraciones 1 a 5 y `docs/api/interop-v2.md`. Lo que ella no dice son **decisiones del equipo** y se documentan como tales.

| Tema | Qué dice la letra (fuente) | Decisión del equipo | Motivo |
|---|---|---|---|
| Sincronización | Sincronizar con `fechaHora` al INICIAR (interop v2 §5.2) | Igual que la letra | — |
| Fórmula del tiempo | `ahora = fechaHora + tiempoReal × factorTiempo` (§5.2) | Igual que la letra | — |
| `factorTiempo` | Mayor que 0; ausente = 1 (§5.2) | Igual que la letra | — |
| INICIAR repetido | Re-sincroniza (§5.2) | Igual que la letra | — |
| Tarifa punta | Por hora y día de semana, en "hora local" (it.1–5) | Igual que la letra | — |
| Corte y restitución | Cortar al entrar en punta y restituir al salir (it.1–5) | Igual que la letra | — |
| Formato del `ts` | Epoch en segundos con 3 decimales (§6) | Se parsea exacto con `BigDecimal` | Evitar el redondeo que marcó el docente |
| Escala del `ts` | No la define (§2) | Hora real del simulador; se convierte a la hora del control con la fórmula del §5.2 | Confirmado por el equipo con el docente; ejemplo 13:00 + 5 min × 2 = 13:10 |
| Hora local | "Hora local del sistema" | Uruguay (`America/Montevideo`), fija, sin `systemDefault()` | Confirmado por el docente |
| Vigencia de lecturas | No la define | 600 s simulados, sin escalar por el factor (T8c) | Un solo reloj; con escalado y factor 30 ninguna lectura vence en 5 h simuladas |
| Frecuencia de publicación | Depende del escenario del simulador (docente) | No se asume ninguna; la vigencia se valida en T13 | El simulador provoca atrasos y caídas a propósito |
| `ts` viejo | No la define | Se descarta (D8) | Un dato obsoleto no debe sostener la calefacción |
| Período del tick | No lo define | 1 s, escalado por el factor: `max(10 ms, tickMs / factor)` (T12) | Con factor alto un tick fijo atrasa el corte de la punta |
| Canal del factor en la it. 5 | "Parámetro al inicio" | `factorTiempo` del INICIAR; el paso a acelerado llega como un INICIAR que re-sincroniza | Único canal del contrato; el yaml nuevo no agrega otro |

### Preguntas abiertas al docente
- ~~Zona horaria~~: respondida (Uruguay, `America/Montevideo`).
- ~~Frecuencia de publicación~~: respondida (la define el simulador según el escenario).
- ~~Escala del `ts`~~: respondida (hora real; se convierte a la hora del control).
- **Abierta:** ¿el paso a modo acelerado llega como un nuevo INICIAR con `factorTiempo`?

### Riesgos y hallazgos (T13)
- Si el simulador acelera sin enviar un INICIAR, el reloj del control sigue con el factor anterior y la tarifa se calcula con un reloj equivocado.
- La conversión del `ts` supone que el reloj real del simulador y el del controlador coinciden (en Docker sobre el mismo host comparten el reloj). Un desfase entre máquinas se multiplica por el factor: 2 s de desfase con factor 30 son 1 min simulado. Se verifica en T13.
- Los hallazgos de hoy que corrigen T8b, T8c y D7: `Core.vencida` multiplica la vigencia por el factor (con factor 30 y 600 s son 5 h simuladas y ninguna lectura vence en toda la corrida); un `ts` más viejo que la vigencia se registra con `ahora` (una lectura de 19:00 que llega a las 21:00 sostiene la calefacción con un dato de 2 h); y cada INICIAR sella las lecturas guardadas con la hora nueva, así que un dato viejo revive.

---

# Anexo: detalle técnico

Las secciones siguen la numeración del análisis original. Cada una dice a qué tarjeta corresponde.

## A1. OFF antes que ON — T3 (ya está, solo se verifica)
`CoreAdapter.enviarPendientes()` manda primero los OFF y después los ON, y retiene un ON si un OFF no se confirmó. Lo cubre `CoreAdapterTest` (línea ~358, `sw-dorm:OFF` antes de `sw-living:ON`). Se entró con el commit `c454323`, probablemente después del comentario del docente.

No hay cambios de código. Al terminar T5 se revisa que siga valiendo, porque el refactor toca `CoreAdapter`.

## A2. CI: sacar `-DskipTests` — T1
En `.github/workflows/maven.yml` los dos pasos (`mvn clean test` y `mvn package -DskipTests`) se reemplazan por uno solo: `mvn -B verify`. Así quedan incluidos los tests, Spotless y la cobertura.

## A3. Timestamp del termostato — T8, T8b y T8c
**Estado:** el parseo exacto y el descarte de `ts` anterior al último están hechos en `67fe9a9`. `67fe9a9` también dejó la conversión del `ts` (`RelojControl.instanteDeLectura` y `aTiempoDeControl`), pero detrás de la propiedad `termostato.ts-tiempo-real`, hoy en `false`: por defecto el `ts` se toma sin convertir, y eso es incorrecto. T8b deja la conversión siempre activa, elimina la propiedad y agrega la regla del `ts` viejo; T8c corrige la vigencia.

- **`PayloadTermostato`** (`engine/mqtt`):
  - parsear `ts` exacto con `BigDecimal` → `Instant.ofEpochSecond(seg, nanos)`, sin pasar por `double × 1000`;
  - el campo es `Instant ts` (null si falta).
- **Escala del `ts`:** llega en **hora real** (epoch del reloj real del simulador) y se convierte a la hora del control con `fechaHora + (ts − iniciadoEn) × factorTiempo`, la misma fórmula del §5.2, donde `iniciadoEn` es la hora real en que llegó el INICIAR. La multiplicación es sobre el tiempo transcurrido, no sobre la hora entera. Ejemplo: INICIAR a las 13:00 reales con `fechaHora = 13:00` y factor 2; `ts = 13:05` reales ⇒ 5 min × 2 = 10 min ⇒ hora del control 13:10 (con `fechaHora = 08:00` daría 08:10). Los mensajes llegan en tiempo real con intervalos inconsistentes, pero la hora sale del contexto (reloj del control y `ts` convertido), nunca de la máquina. Riesgo: la conversión supone que el reloj real del simulador y el del controlador coinciden; un desfase se multiplica por el factor (se verifica en T13). Ver la [sección 5](#5-tiempo-del-sistema-letra-y-decisiones).
- **`ControlService.alRecibirLectura`**: el instante de la lectura es el `ts` convertido a la hora del control (`reloj.aTiempoDeControl(ts)`). Si `ts` es null o, ya convertido, queda en el futuro respecto de `reloj.ahora()`, se usa `reloj.ahora()`.
  - **La temperatura nunca se descarta por esto**: solo se reemplaza el instante con que se guarda.
  - **Por qué**: un `ts` futuro solo puede salir de relojes desfasados entre el termostato o el simulador y el control. Si se guardara tal cual, pasarían dos cosas:
    - la lectura no vencería hasta que el reloj del control alcance ese `ts`, y con un reloj muy adelantado no vencería nunca: se calefacciona con un dato viejo;
    - las lecturas siguientes, con su `ts` correcto, parecerían "más viejas" y se descartarían como fuera de orden: el sensor queda bloqueado.
  - **En el caso normal** el `ts` coincide con `ahora` o queda unos instantes antes (la latencia de la red) y se usa el valor convertido.
- **Lecturas fuera de orden**: `CoreAdapter.registrarLectura` descarta las que tienen un `ts` anterior a la última (A4).
- **`ts` más viejo que la vigencia (T8b, D8)**: antes se reemplazaba por `ahora` y la lectura entraba como fresca: una lectura de 19:00 que llega a las 21:00 sostendría la calefacción con un dato de 2 h. Con T8b se descarta y se cuenta en `/diagnostico` (`TS_VENCIDO`). Además evita que la próxima lectura buena parezca fuera de orden. Falta que el equipo confirme D8.
- **Vigencia (T8c)**: `Core.vencida` deja de multiplicar la vigencia por el factor. Con factor 30 y 600 s hoy son 5 h simuladas y ninguna lectura vence en toda la corrida; con la regla nueva, una lectura de hace 601 s de tiempo de control está vencida. `factorTiempo` sale de `Core.decidir`.
- **Re-sincronización (D7)**: hoy cada INICIAR sella todas las lecturas guardadas con la hora nueva (`CoreAdapter.sincronizarReloj` o equivalente) y un dato viejo revive. Propuesta: conservar solo las que no estaban vencidas.
- **Tests**:
  - `PayloadTermostatoTest`: el ts exacto, por ejemplo `.123` → 123 ms sin error de redondeo, y el caso sin ts;
  - conversión con factor 2: INICIAR 13:00 con `fechaHora = 13:00`, `ts` 13:05 reales ⇒ 13:10;
  - lectura con `ts` futuro (se usa `ahora`), `ts` viejo dentro de la vigencia (se acepta), `ts` viejo fuera de la vigencia (descartada y contada) y `ts` anterior a la última (descartada);
  - con factor 30, una lectura de hace 601 s de tiempo de control está vencida (T8c).

## A4. Core sin estado — T4 y T5
**Core** (`modules/core`):
- `Core` queda solo con la función pura `decidir(sitio, habitaciones, estados, ahora, factorTiempo)` más `criterio` y `parametros`.
- Se borran:
  - `procesar` y los estímulos (`registrarLectura`, `avanzarReloj`, `configurar`, `sincronizar`, `decidirConEstadoActual`);
  - los campos de estado y `estadoDe`/`ahora()`.
- `validar` también se va: ya lo cubre `ValidadorInventario` en el engine.
- Se borran `cliente/CoreClient.java` y `cliente/ICoreClient.java`.
- **`Estimulo`, a decidir en equipo:**
  - **A.** Borrarlo. Cada evento queda como un método de `CoreAdapter`.
  - **B. (recomendada).** Dejarlo en `core/model` como modelo de los estímulos, que es lo que pide la letra de la iteración 4. El `switch` exhaustivo se muda a `CoreAdapter.procesar(Estimulo)`.
- Se agrega a `Core` un helper puro, `boolean lecturaValida(double tC)`, con el rango de `ParametrosControl`. Así la regla de qué es un sensor defectuoso sigue en el core.
- Se mantienen `Sitio`, `ConfiguracionHabitacion`, `EstadoHabitacion`, `Decision`, `Motivo`, `Accion`, `FranjaHoraria`, `ParametrosControl`, `CriterioPrioridad` y `MayorDeficitPrimero`.
- **Tests del core**:
  - `CoreTest` se repasa grupo por grupo. Lo que es decisión (punta, tope, criterio, histéresis, vencidas, invariantes) se pasa a `DecidirTest`, llamando a `decidir` con la situación armada.
  - Lo que es estímulo (lecturas inválidas, fuera de orden, habitación desconocida, re-sincronizar, reloj que no retrocede) se mueve a `CoreAdapterTest`.
  - Se borran `CoreClientTest` y `CoreTestFixtures` si quedan sin uso.

**Engine** (`CoreAdapter` pasa a ser el único dueño del estado):
- Guarda por habitación la temperatura y el instante de la última lectura (además de lo que ya tiene: `habitaciones`, `deseado`, `estadoConocido`).
- Guarda `sitio`, `ahora` y `factorTiempo`.
- "Encendida" sale de `deseado` (la última decisión ON), así que desaparece el `encendidas` duplicado del core.
- Métodos:
  - `configurar`, `alLlegarLectura`, `alTick` y `sincronizar` actualizan su estado y llaman a un único `decidirYAplicar()`. Ese método arma el `Map<String, EstadoHabitacion>` y llama a `core.decidir(...)`.
  - `sincronizar` mantiene la regla actual: las lecturas conocidas se toman como medidas en el nuevo instante.
  - `estados()` (diagnóstico) lee del estado propio, sin `core.estadoById`.
- `CoreConfig` crea un bean `Core` en vez de `ICoreClient`.

**Riesgo**: es el cambio más grande. Hay que mantener en verde los 99 tests del engine, incluidos los de integración (`ArranqueParadaTest`, `CambioConfiguracionTest`, `FallaSwitchTest`, etc.).

## A5. Zona horaria — T9
- **`application.properties`**: `control.zona-horaria=${CONTROL_ZONA_HORARIA:America/Montevideo}`.
- **`ControlService`**:
  - la zona es siempre `zonaFija`, que pasa a ser obligatoria (`ZoneId`, no `Optional`);
  - se eliminan el parámetro `zonaDeFechaHora` de `iniciar` y el fallback a `ZoneId.systemDefault()`;
  - actualizar el javadoc de la clase.
- **`ControlController`**: la `fechaHora` sin offset se interpreta en la zona del sitio y no en la del sistema.
- **`RelojControl`**: sigue con `zona`, que llega desde `ControlService`.
- **docker-compose**: dejar `CONTROL_ZONA_HORARIA` explícita.
- **Tests**:
  - ajustar los que asumían "la zona del offset", buscando con grep `zona` en `ControlServiceTest`/`ApiEstandarTest`/`ArranqueParadaTest`;
  - agregar uno: un INICIAR con `...Z` a las 21:00 UTC (18:00 en Montevideo) aplica la punta de las 18:00.

## A6. Camino de una lectura MQTT: menos saltos — T5 y T7
Hoy una lectura del termostato pasa por **10 funciones** hasta llegar a `decidir`:

```
1  LecturaMqttListener.messageArrived      callback de Paho; try/catch para no cortar la conexión
2  LecturaMqttListener.procesarMensaje     topic → habitación, parseo
3  PayloadTermostato.parsear               JSON → temperatura (+ ts)
4  ControlService.alRecibirLectura         ¿está detenido? + hora del control; otro try/catch
5  CoreAdapter.alLlegarLectura             synchronized
6  CoreClient.nuevaTemperatura             solo envuelve en un Estimulo (no aporta nada)
7  Core.procesar                           switch del estímulo
8  Core.registrarLectura                   filtra y guarda (estado duplicado)
9  Core.decidirConEstadoActual             arma la situación con el estado del core
10 Core.decidir                            la decisión
```

Después de decidir siguen `CoreAdapter.aplicar` → `programarPasada` → (hilo de switches) `pasada` → `enviarPendientes` → `ClienteSwitchRest`. Ese tramo se justifica, porque la red corre en otro hilo para no bloquear MQTT.

**Diagnóstico.** Sí sobran saltos:
- **Del 6 al 9** son capas de paso o estado duplicado. Desaparecen con A4.
- **Dos try/catch para lo mismo** (1 y 4). Queda solo el de `messageArrived`, que es obligatorio por Paho.
- **Dos banderas de estado de ejecución**: `ControlService.reloj != null` y `CoreAdapter.habilitado`. Pueden quedar desincronizadas. Queda una sola.

**Lo que se mantiene, y por qué:**
- **1 y 2 separados:** 2 se prueba sin broker (`procesarMensaje` es package-private).
- **3:** el parseo del payload está aislado y tiene sus tests.
- **4:** `ControlService` es el dueño del reloj del control y de su inicio y parada.
- **El evento de Spring `InventarioAplicado`** (`InventarioService` → `ControlService`/`LecturaMqttListener`). Es menos directo de leer, pero una llamada directa crearía una dependencia circular (`ControlService` ya usa `InventarioService`). Se documenta en el código.

**Resultado:** queda `messageArrived → procesarMensaje → ControlService.alRecibirLectura → CoreAdapter.procesar(lectura) → core.decidir`, 5 pasos con una responsabilidad clara cada uno.

### T7b. CoreAdapter administra el estado de ejecución
**El problema.** Antes había dos banderas de estado de ejecución. `67fe9a9` las unió con inyección perezosa (`ObjectProvider<ControlService>` y un `BooleanSupplier` en `CoreConfig`), pero eso solo esconde un ciclo `ControlService` ↔ `CoreAdapter`: no se ve en el código y cuesta entenderlo.

**La solución.** El estado de ejecución vive en `CoreAdapter` y la dependencia va en un solo sentido:

```
ControlService ──usa──> CoreAdapter
```

```java
// CoreAdapter
private boolean enEjecucion; // solo se toca con el candado de esta clase
public synchronized void iniciar() { enEjecucion = true; noReintentarAntesDe.clear(); programarPasada(true); }
public synchronized void detener() { enEjecucion = false; }
public synchronized boolean enEjecucion() { return enEjecucion; }

// ControlService
public synchronized EstadoControl detener() { adaptador.detener(); pararTemporizador(); return estado(); }
public boolean enEjecucion() { return adaptador.enEjecucion(); }

// CoreConfig: new CoreAdapter(core, switches, relojReal, espera)  // sin ObjectProvider
```

**Deadlock.** Los candados se toman siempre en el mismo orden (`ControlService` y luego `CoreAdapter`), así que no hay deadlock.

**Alternativas descartadas:**
- clase compartida `EstadoEjecucion`: es una variable global con nombre y mezcla reloj y bandera;
- eventos de Spring: vuelve la copia del estado, o sea dos banderas otra vez;
- fusionar las dos clases: quedarían unas 800 líneas;
- pasar el estado de ejecución por parámetro: no sirve al hilo de switches.

## A7. Legibilidad para el equipo — T2 y T10
**Objetivo:** que alguien que no domina Java moderno pueda seguir el código. Se hace después de A4, porque `Core` y `CoreAdapter` se reescriben igual.

**Reglas** (se agregan a `docs/calidad/estandares-codificacion.md`, sección "Java que usamos"):
- **Loops antes que streams**: `for` en vez de `.stream().filter().map()`, salvo los de una línea obvia (hay 9 en `CoreAdapter`, `ControlService`, `InventarioService`, `DiagnosticoController` y `MayorDeficitPrimero`).
- **`if/else` antes que ternarios** anidados o largos (hay 18).
- **Lambdas o loops antes que `Clase::metodo`** cuando la referencia no se lee sola (hay 11).
- **`Optional` solo como retorno** de un método que puede no devolver nada (hay 22). Adentro se usa `if (x == null)`.
- **Tipos explícitos**, sin `var`.

**Se mantiene, y se explica en la guía:**
- `record`, para las clases de datos;
- `sealed` + `switch` con `->`, si se elige la opción B de A4 (D2);
- `synchronized` y el hilo de switches. Es la concurrencia real del controlador (MQTT, temporizador y switches corren en hilos distintos), así que no se puede sacar.

**Guía corta** `docs/calidad/guia-java.md`: cada técnica que se mantiene, con un ejemplo del propio proyecto y por qué está ahí. Sirve para el oral.

**Riesgo:** cambiar streams por loops no cambia el comportamiento, pero toca muchas líneas. Hay que coordinar para que nadie más esté editando esos archivos al mismo tiempo.

## A8. Ramas y commits por tarjeta
Rama `revision/<id>` desde `implementacion5` y PR de vuelta a `implementacion5` (D5). Formato `módulo: descripción`, sin coautoría.

| Tarjeta | Rama | Commit |
|---|---|---|
| T1 | `revision/t1-ci` | `ci: mvn verify sin -DskipTests` |
| T1b | `revision/t1b-ci-filtros` | `ci: no correr el build en cambios de documentación y verificar PR apilados` |
| T2 | `revision/t2-guia-java` | `docs: reglas de Java del proyecto y guía para el equipo` |
| T4 | `revision/t4-estado-engine` | `engine: el adaptador guarda la situación y llama a decidir` |
| T5 | `revision/t5-core-sin-estado` | `core: core sin estado, solo decidir y el modelo` |
| T7 | `revision/t7-camino-lectura` | `engine: una sola bandera de estado de ejecución y un solo try/catch en la lectura MQTT` |
| T7b | `revision/t7b-estado-ejecucion` | `engine: CoreAdapter administra el estado de ejecución y se elimina ObjectProvider` |
| T8 | `revision/t8-ts` | `engine: usar el ts del termostato como instante de la lectura` |
| T8b | `revision/t8b-ts-hora-real` | `engine: ts en hora real convertido a la hora del control y descarte de lecturas viejas` |
| T8c | `revision/t8c-vigencia-simulada` | `core: vigencia de las lecturas en tiempo simulado` |
| T9 | `revision/t9-zona` | `engine: zona horaria fija del sitio` |
| T10 | `revision/t10-legibilidad` | `estilo: loops e if en vez de streams y ternarios` |
| T11a | `revision/t11a-4mas1-diseno-actual` | `docs: 4+1 actualizado al diseño actual del core y del engine` |
| T11 | `revision/t11-docs` | `docs: 4+1, cobertura y resultados tras la revisión` |
| T12 | `revision/t12-tick` | `engine: tick escalado con el factor de tiempo` |
| T13 | `revision/t13-simulador` | `test: corrida contra el simulador con evidencia` |
| T14 | `revision/t14-doc-tiempo` | `docs: relojes y decisiones sobre el tiempo` |

T3 no genera commit, salvo que la verificación encuentre algo. T1, T4, T5 y T7 ya entraron juntas en `67fe9a9`, sin rama propia: desde ahora cada tarjeta va en su rama.

## A9. Verificación final
- `mvn verify` en la raíz: Spotless, todos los tests en verde y cobertura del core ≥ 85 % (`modules/core/target/site/jacoco/index.html`).
- `grep -r "Estimulo\|ICoreClient\|CoreClient\|systemDefault\|skipTests"` sobre `modules` y `.github` no devuelve nada.
- Smoke opcional: `docker compose up` con el simulador, `INICIAR` con `factorTiempo` > 1 y `GET /diagnostico`, para ver que `ultimaLectura` coincide con el `ts` de los mensajes.
