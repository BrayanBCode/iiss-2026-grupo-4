# Revisión del código según el docente (iteración 5)

Planificación en **Scrumban**, como pide la iteración 4: se planifica al inicio de la iteración, se trabaja con un tablero de flujo continuo y se revisa cada semana con el Product Owner. La primera parte es para la reunión del equipo y el tablero. El [anexo](#anexo-detalle-técnico) tiene el detalle técnico de cada tarjeta.

## 1. Planificación

### Objetivo
Corregir lo que marcó el docente sin romper nada de la iteración 5 (215 tests en verde y cobertura del core ≥ 85 %), y dejar el código más fácil de leer para el equipo **antes** de cerrar la documentación de la entrega.

### Lo que marcó el docente
| Observación | Tarjeta |
|---|---|
| Apagar antes de encender | T3 (ya está; se verifica) |
| El `ts` se redondea o se pierde | T8 |
| `-DskipTests` en GitHub Actions | T1 |
| Sobrediseño: estado cacheado en core y engine | T4, T5 |
| Core con estado | T4, T5 |
| Zona horaria | T9 |
| Saltos de más para que una lectura MQTT llegue al core (detectado por el equipo) | T5, T7 |
| Código difícil de leer para el equipo (detectado por el equipo) | T2, T10 |

### Decisiones para la reunión
Hay que tomarlas antes de pasar las tarjetas a "Listo":

| # | Decisión | Propuesta |
|---|---|---|
| D1 | ¿El core queda sin estado? | **Sí**: solo `decidir` y el modelo (sugerencia del docente) |
| D2 | ¿`Estimulo` se borra (A) o queda como modelo, procesado en el engine (B)? | **B**: es lo que pide la letra de la iteración 4 ("modelo de los estímulos") |
| D3 | ¿El `ts` del termostato es el instante de la lectura? | **Sí**. Si falta o viene en el futuro, se usa el reloj del control |
| D4 | ¿Zona horaria fija del sitio? | **Sí**, `America/Montevideo`, configurable. Hay que confirmarlo con el contrato del simulador nuevo |
| D5 | ¿Una rama por tarjeta, con PR a `implementacion5`? | **Sí**, así cada tarjeta se revisa por separado |

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

## 3. Tarjetas

Tamaño: S (menos de medio día), M (alrededor de un día), L (más de un día).

| ID | Tarjeta | Carril | Depende de | Tamaño | Criterio de aceptación |
|---|---|---|---|---|---|
| T1 | CI con `mvn verify`, sin `-DskipTests` | B | — | S | El workflow corre un solo `mvn -B verify` y pasa en el PR |
| T2 | Reglas "Java que usamos" + `docs/calidad/guia-java.md` | B | D2 | S | El estándar tiene la sección y la guía explica cada técnica que se mantiene con un ejemplo del proyecto |
| T4 | El estado pasa a `CoreAdapter`, que llama a `core.decidir` | A | D1, D2 | L | `CoreAdapter` guarda temperaturas, instantes, sitio y reloj; ya no usa `procesar` ni `estadoById`. Los 215 tests en verde |
| T5 | `Core` queda solo con `decidir`; se migran los tests de `CoreTest` | A | T4 | L | Ya no existen `procesar`, `CoreClient` ni `ICoreClient`. Los tests de decisión están en `DecidirTest` y los de estímulos en `CoreAdapterTest`. Cobertura del core ≥ 85 % |
| T3 | Verificar OFF antes que ON después del refactor | B | T5 | S | El test de `CoreAdapterTest` (OFF antes que ON) sigue en verde y no cambió |
| T7 | Una sola bandera de "en marcha" y un solo try/catch en la lectura MQTT | A | T5 | S | `ControlService` y `CoreAdapter` no duplican el estado de marcha. Una lectura recorre 5 pasos |
| T8 | `ts` del termostato como instante de la lectura | A | T7 | M | El `ts` se parsea exacto. Hay tests de `ts` exacto, `ts` viejo (descartada) y `ts` futuro (se usa el reloj) |
| T9 | Zona horaria fija del sitio | A | T8, D4 | M | No se usa `ZoneId.systemDefault()`. Un test de INICIAR `...Z` a las 21:00 UTC aplica la punta de las 18:00 de Montevideo |
| T10 | Simplificar streams, ternarios y `::` en el engine | A | T9, T2 | M | Se cumplen las reglas de T2. Los tests no cambian y siguen en verde |
| T11 | Documentación: 4+1, cobertura y resultados | B | T10 | M | `4mas1.md` sin `CoreClient` y con el camino nuevo. `docs/calidad/cobertura/` regenerado. `resultados.md` con los conteos nuevos |

### Orden y paralelismo
```
Carril A (código, de a uno):  T4 → T5 → T7 → T8 → T9 → T10
Carril B (en paralelo):       T1, T2 (cuando está D2)
                              T3 (después de T5) · T11 (al final)
```
Con 3 o 4 integrantes: una persona lleva el carril A, otra toma T1 y T2 y después revisa los PR del carril A, y el resto avanza con la vista física del 4+1 (fuera de esta lista).

## 4. Revisión semanal con el Product Owner
- **Qué mostrar:** el tablero, el diff del core (antes y después) y el camino de una lectura reducido de 10 a 5 pasos.
- **Qué validar con el docente:** que la opción de `Estimulo` (D2) cumple con el "modelo de los estímulos" que pide la letra, y la zona horaria del simulador (D4).
- **Qué ajustar:** si el carril A se atrasa, T10 pasa a la iteración 6. Las tarjetas T4, T5, T8 y T9 son las que corrigen lo que marcó el docente.

---

# Anexo: detalle técnico

Las secciones siguen la numeración del análisis original. Cada una dice a qué tarjeta corresponde.

## A1. OFF antes que ON — T3 (ya está, solo se verifica)
`CoreAdapter.enviarPendientes()` manda primero los OFF y después los ON, y retiene un ON si un OFF no se confirmó. Lo cubre `CoreAdapterTest` (línea ~358, `sw-dorm:OFF` antes de `sw-living:ON`). Se entró con el commit `c454323`, probablemente después del comentario del docente.

No hay cambios de código. Al terminar T5 se revisa que siga valiendo, porque el refactor toca `CoreAdapter`.

## A2. CI: sacar `-DskipTests` — T1
En `.github/workflows/maven.yml` los dos pasos (`mvn clean test` y `mvn package -DskipTests`) se reemplazan por uno solo: `mvn -B verify`. Así quedan incluidos los tests, Spotless y la cobertura.

## A3. Timestamp del termostato — T8
- **`PayloadTermostato`** (`engine/mqtt`):
  - parsear `ts` exacto con `json.path("ts").decimalValue()` → `Instant.ofEpochSecond(seg, nanos)`, sin pasar por `double × 1000`;
  - el campo pasa a ser `Instant ts` (null si falta);
  - actualizar el javadoc, que hoy dice que no se usa.
- **`LecturaMqttListener.procesarMensaje`**: pasar `p.ts()` a `controlService.alRecibirLectura(id, tC, ts)`.
- **`ControlService.alRecibirLectura`**: el instante de la lectura es `ts`. Si `ts` es null o está en el futuro respecto de `reloj.ahora()`, se usa `reloj.ahora()`.
  - **La temperatura nunca se descarta por esto**: solo se reemplaza el instante con que se guarda.
  - **Por qué**: un `ts` futuro solo puede salir de relojes desfasados entre el termostato o el simulador y el control. Si se guardara tal cual, pasarían dos cosas:
    - la lectura no vencería hasta que el reloj del control alcance ese `ts`, y con un reloj muy adelantado no vencería nunca: se calefacciona con un dato viejo;
    - las lecturas siguientes, con su `ts` correcto, parecerían "más viejas" y se descartarían como fuera de orden: el sensor queda bloqueado.
  - **En el caso normal** el `ts` llega unos milisegundos antes que `ahora` (la latencia de la red) y se usa tal cual.
- **Lecturas fuera de orden**: el engine descarta las que tienen un `ts` anterior a la última (A4).
- **Tests**:
  - `PayloadTermostatoTest`: el ts exacto, por ejemplo `.123` → 123 ms sin error de redondeo, y el caso sin ts;
  - un test de lectura con `ts` viejo (descartada) y con `ts` futuro (se usa el reloj).

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
- **Dos banderas de "en marcha"**: `ControlService.reloj != null` y `CoreAdapter.habilitado`. Pueden quedar desincronizadas. Queda una sola.

**Lo que se mantiene, y por qué:**
- **1 y 2 separados:** 2 se prueba sin broker (`procesarMensaje` es package-private).
- **3:** el parseo del payload está aislado y tiene sus tests.
- **4:** `ControlService` es el dueño del reloj del control y de la marcha y parada.
- **El evento de Spring `InventarioAplicado`** (`InventarioService` → `ControlService`/`LecturaMqttListener`). Es menos directo de leer, pero una llamada directa crearía una dependencia circular (`ControlService` ya usa `InventarioService`). Se documenta en el código.

**Resultado:** queda `messageArrived → procesarMensaje → ControlService.alRecibirLectura → CoreAdapter.procesar(lectura) → core.decidir`, 5 pasos con una responsabilidad clara cada uno.

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
| T2 | `revision/t2-guia-java` | `docs: reglas de Java del proyecto y guía para el equipo` |
| T4 | `revision/t4-estado-engine` | `engine: el adaptador guarda la situación y llama a decidir` |
| T5 | `revision/t5-core-sin-estado` | `core: core sin estado, solo decidir y el modelo` |
| T7 | `revision/t7-camino-lectura` | `engine: una sola bandera de marcha y un solo try/catch en la lectura MQTT` |
| T8 | `revision/t8-ts` | `engine: usar el ts del termostato como instante de la lectura` |
| T9 | `revision/t9-zona` | `engine: zona horaria fija del sitio` |
| T10 | `revision/t10-legibilidad` | `estilo: loops e if en vez de streams y ternarios` |
| T11 | `revision/t11-docs` | `docs: 4+1, cobertura y resultados tras la revisión` |

T3 no genera commit, salvo que la verificación encuentre algo.

## A9. Verificación final
- `mvn verify` en la raíz: Spotless, todos los tests en verde y cobertura del core ≥ 85 % (`modules/core/target/site/jacoco/index.html`).
- `grep -r "Estimulo\|ICoreClient\|CoreClient\|systemDefault\|skipTests"` sobre `modules` y `.github` no devuelve nada.
- Smoke opcional: `docker compose up` con el simulador, `INICIAR` con `factorTiempo` > 1 y `GET /diagnostico`, para ver que `ultimaLectura` coincide con el `ts` de los mensajes.
