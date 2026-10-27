# Revisión del código según el docente (rama `implementacion5`)

## Contexto
Antes de la documentación de la iteración 5, el docente marcó estos problemas:
- apagar antes de encender;
- el `ts` se redondea o se pierde;
- `-DskipTests` en GitHub Actions;
- sobrediseño: el estado de las habitaciones está cacheado en el core y también en el engine;
- core con estado;
- zona horaria.

Decisiones tomadas:
- **core sin estado**: solo `decidir` y el modelo;
- el **`ts` del termostato** pasa a ser el instante de la lectura;
- **zona fija del sitio**, por defecto `America/Montevideo`.

Se trabaja en `implementacion5`, con un commit por punto y sin push.

## 1. OFF antes que ON: ya está, solo se verifica
`CoreAdapter.enviarPendientes()` manda primero los OFF y después los ON, y retiene un ON si un OFF no se confirmó. Lo cubre `CoreAdapterTest` (línea ~358, `sw-dorm:OFF` antes de `sw-living:ON`). Se entró con el commit `c454323`, probablemente después del comentario del docente.

No hay cambios de código. Al terminar el punto 4 se revisa que siga valiendo, porque el refactor toca `CoreAdapter`.

## 2. CI: sacar `-DskipTests`
En `.github/workflows/maven.yml` los dos pasos (`mvn clean test` y `mvn package -DskipTests`) se reemplazan por uno solo: `mvn -B verify`. Así quedan incluidos los tests, Spotless y la cobertura.

## 3. Timestamp del termostato
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
- **Lecturas fuera de orden**: el engine descarta las que tienen un `ts` anterior a la última (punto 4).
- **Tests**:
  - `PayloadTermostatoTest`: el ts exacto, por ejemplo `.123` → 123 ms sin error de redondeo, y el caso sin ts;
  - un test de lectura con `ts` viejo (descartada) y con `ts` futuro (se usa el reloj).

## 4. Core sin estado
**Core** (`modules/core`):
- `Core` queda solo con la función pura `decidir(sitio, habitaciones, estados, ahora, factorTiempo)` más `criterio` y `parametros`.
- Se borran:
  - `procesar` y los estímulos (`registrarLectura`, `avanzarReloj`, `configurar`, `sincronizar`, `decidirConEstadoActual`);
  - los campos de estado y `estadoDe`/`ahora()`.
- `validar` también se va: ya lo cubre `ValidadorInventario` en el engine.
- Se borran `model/Estimulo.java`, `cliente/CoreClient.java` y `cliente/ICoreClient.java`.
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

## 5. Zona horaria
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

## Orden y commits
Cada commit tiene formato `módulo: descripción`, sin coautoría.
1. `ci: mvn verify sin -DskipTests`
2. `engine: usar el ts del termostato como instante de la lectura`
3. `core: core sin estado, el engine guarda la situación` (core + engine + tests)
4. `engine: zona horaria fija del sitio`

Al final, actualizar las referencias en `docs/arquitectura/4mas1.md`: el diagrama de clases del core menciona `Estimulo`/`procesar`/`CoreClient`. También regenerar `docs/calidad/cobertura/` y los conteos de `docs/tests/resultados.md`. Ese cierre se puede dejar para la tarea de documentación.

## Verificación
- `mvn verify` en la raíz: Spotless, todos los tests en verde y cobertura del core ≥ 85 % (`modules/core/target/site/jacoco/index.html`).
- `grep -r "Estimulo\|ICoreClient\|CoreClient\|systemDefault\|skipTests"` sobre `modules` y `.github` no devuelve nada.
- Smoke opcional: `docker compose up` con el simulador, `INICIAR` con `factorTiempo` > 1 y `GET /diagnostico`, para ver que `ultimaLectura` coincide con el `ts` de los mensajes.
