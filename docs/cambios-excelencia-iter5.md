# Grupo 4 — Iteración 5: cambios para llegar a un proyecto excelente

Este documento complementa la planificación de [`revision-docente.md`](revision-docente.md). Cada cambio dice **qué** hacer, **por qué** (qué requisito o qué observación atiende) y **cuándo** se considera hecho. Están ordenados por prioridad:

- **Bloque A:** obligatorios para que la entrega sea coherente.
- **Bloque B:** hacen que la entrega quede sólida.
- **Bloque C:** hacen que la entrega destaque.

La idea que guía todo: hoy el proyecto funciona bien, pero la entrega afirma más de lo que demuestra, y arrastra observaciones del docente que el propio repositorio reconoce como pendientes. Ser excelente es cerrar esa brecha: que **cada afirmación del README tenga detrás algo verificable**.

---

## A. Obligatorio: cerrar lo que el docente ya marcó

### A1. CI que verifique de verdad
- **Qué:**
  - reemplazar los dos pasos de `.github/workflows/maven.yml` (`mvn clean test` y `mvn package -DskipTests`) por un solo `mvn -B verify`;
  - disparar también en la rama de trabajo (`implementacion5`) y en el tag, no solo en `master`;
  - subir como artefactos de la corrida el reporte JaCoCo y los `surefire-reports`.
- **Por qué:** el estándar de codificación y la cobertura dicen que "`mvn verify` falla si algo no cumple", pero hoy ninguna máquina lo ejecuta. El docente ya lo observó.
- **Hecho cuando:** hay una corrida verde de GitHub Actions sobre el commit del tag `version5`, con el enlace en el README.

### A2. Cobertura ≥ 85 % como regla, no como dato
- **Qué:** agregar a `modules/core/pom.xml` la meta `check` de JaCoCo en la fase `verify`, con dos reglas:
  - `LINE COVEREDRATIO ≥ 0.85`;
  - `BRANCH COVEREDRATIO ≥ 0.85`, ya que se puede.
- **Por qué:** la letra pide "asegurar" la cobertura. Un reporte de hoy no asegura nada sobre mañana.
- **Hecho cuando:**
  - bajar artificialmente la cobertura (por ejemplo, comentando un test) hace fallar `mvn verify` y la CI;
  - está mencionado en `docs/calidad/cobertura/README.md`.

### A3. Core sin estado
- **Qué:** ejecutar las tarjetas T4 y T5 de `revision-docente.md`:
  - `Core` queda con la función pura `decidir(...)`, el criterio, los parámetros y un helper puro `lecturaValida(tC)`;
  - el estado (temperaturas, instante de la última lectura, sitio, `ahora`, `factorTiempo`) pasa a ser propiedad única de `CoreAdapter`;
  - se borran `CoreClient`/`ICoreClient` y `Core.procesar`;
  - `Estimulo` se mantiene en `core/model` como modelo de los estímulos, porque lo pide la letra de la iteración 4. El `switch` exhaustivo se muda a `CoreAdapter.procesar(Estimulo)`;
  - `validar` se borra del core porque ya lo hace `ValidadorInventario`.
- **Por qué:** es la observación principal del docente: sobrediseño y estado duplicado en core y engine (`encendidas` del core frente a `deseado` del adaptador).
- **Tests:**
  - lo que en `CoreTest` es decisión pasa a `DecidirTest`;
  - lo que es manejo de estímulos pasa a `CoreAdapterTest`: lecturas inválidas, fuera de orden, habitación desconocida, reloj que no retrocede, re-sincronizar;
  - ningún comportamiento se pierde: cada test viejo tiene su equivalente nuevo. Conviene dejar una tabla de correspondencia en el PR.
- **Hecho cuando:**
  - el core no tiene campos mutables;
  - no existen `procesar` ni `CoreClient`;
  - los 215 tests, o su equivalente migrado, están en verde;
  - la cobertura del core sigue ≥ 85 %.

### A4. Una sola bandera de estado de ejecución y un camino corto para la lectura
- **Qué:** tarjeta T7.
  - El estado de ejecución vive en un solo lugar, `ControlService`. `CoreAdapter` no guarda `habilitado` por su cuenta: lo deriva de `ControlService`.
  - Queda un solo `try/catch`, el de `messageArrived`, que Paho exige.
- **Por qué:** dos banderas pueden desincronizarse, por ejemplo con un DETENER concurrente con una lectura.
- **Hecho cuando:**
  - el camino de una lectura es `messageArrived → procesarMensaje → ControlService.alRecibirLectura → CoreAdapter → core.decidir`;
  - la secuencia 2.2 del 4+1 lo muestra así.

### A5. Usar el `ts` del termostato
- **Qué:** tarjeta T8.
  - Parsear el `ts` exacto con `decimalValue()` → `Instant.ofEpochSecond(seg, nanos)`, sin pasar por `double × 1000`.
  - Usar ese `ts` como instante de la lectura. Si falta, o está en el futuro respecto del reloj del control, usar `reloj.ahora()`. Nunca se descarta la temperatura por este motivo.
  - Descartar las lecturas con `ts` anterior a la última guardada.
- **Por qué:**
  - hoy la regla "lectura fuera de orden" compara instantes de llegada, así que nunca se dispara;
  - el docente marcó que el `ts` se redondeaba o se perdía.
- **Ojo con el tiempo acelerado:** el docente respondió de palabra que el `ts` viene en tiempo simulado, en la misma escala que el reloj del control. Entonces se toma tal cual, sin conversión. Pedir la confirmación por escrito y dejar la respuesta en el javadoc y en `4mas1.md`.
- **Hecho cuando:** existen tests de:
  - `ts` exacto (`.123` → 123 ms);
  - `ts` ausente;
  - `ts` viejo, que se descarta;
  - `ts` futuro, que usa el reloj.

### A6. Zona horaria del sitio, explícita
- **Qué:** tarjeta T9.
  - `control.zona-horaria` pasa a tener default `America/Montevideo` y se fija también en el compose.
  - Se elimina el fallback a `ZoneId.systemDefault()` en los dos lugares donde aparece:
    - `ControlService.configurarCore`;
    - `ControlController`: una `fechaHora` sin offset hoy se interpreta en la zona del contenedor, que en Docker suele ser UTC.
  - Se elimina el parámetro `zonaDeFechaHora` de `iniciar`.
- **Por qué:** la tarifa punta es hora local del sitio. Con `...Z`, hoy la punta de las 18:00 se evalúa a las 18:00 UTC (15:00 en Uruguay). El docente lo marcó.
- **Hecho cuando:**
  - un test de integración hace INICIAR a las 21:00Z (18:00 en Montevideo) y verifica `TARIFA_PUNTA`;
  - antes de cerrar, se confirmó con el contrato del simulador nuevo en qué zona razona él.

### A7. Que `revision-docente.md` refleje lo hecho
- **Qué:** cuando se cierren A1–A6, convertir el documento de "plan" a "registro". Por cada observación del docente se anota:
  - la tarjeta que la resolvió;
  - el commit o PR;
  - el test que la demuestra.

  Las tarjetas que no entren en la iteración se marcan explícitamente como diferidas, con su motivo.
- **Por qué:** hoy el repositorio entregado dice, en su propia documentación, que las correcciones no están hechas.

---

## B. Para que la iteración 5 quede sólida

### B1. Tiempo acelerado bien resuelto y demostrado
1. **Granularidad de la franja.** El tick es de 1 s real fijo: con factor 600, el corte de la punta puede llegar hasta 10 minutos simulados tarde. Hay dos opciones:
   - **(a, recomendada)** programar un evento exactamente en el próximo borde de franja:
     - calcular el instante simulado del próximo `desde`/`hasta`;
     - convertirlo a tiempo real con el factor y agendarlo;
     - reprogramarlo en cada INICIAR o cambio de sitio.

     El atraso queda en milisegundos con cualquier factor.
   - **(b)** escalar el período del tick a `max(10 ms, tickMs / factor)` y documentar el error máximo.

   En los dos casos, documentar en `4mas1.md` cuánto se atrasa como máximo el corte en tiempo simulado.
2. **Vigencia de las lecturas.** Decidido: se mide en tiempo simulado, sin escalar por el factor (T8c).
   - Hoy se escala con el factor: 600 s reales a ×60 son 10 horas simuladas calefaccionando con un dato viejo.
   - Si el simulador acelera también la frecuencia de publicación de los termostatos (lo esperable), la vigencia debería medirse en tiempo simulado: comparar `ahora - ultimaLectura` contra una vigencia fija en minutos de control, sin multiplicar por el factor.
   - Corregir la contradicción con el PDF interno, que dice lo contrario.
3. **Cómo llega el factor.** Verificar contra la versión nueva del simulador que el "parámetro al inicio" es el `factorTiempo` de INICIAR (§5.2) y no otra cosa, como un campo del registro §6 o una variable de entorno. Si llega por otro canal, soportarlo. Dejar el hallazgo en el README.
4. **Re-sincronización.** Testear dos casos, verificando la tarifa y la vigencia después de cada uno:
   - INICIAR dos veces con distinto factor;
   - INICIAR con una `fechaHora` anterior (reloj hacia atrás).
5. **Tests de integración acelerados:**
   - con factor alto (×600), arrancar 5 minutos simulados antes de la punta, esperar menos de 1 s real y verificar el corte (`TARIFA_PUNTA`, switch OFF); después, la restitución al salir;
   - un termostato caído con factor alto, verificando que vence según la regla elegida en el punto 2.

- **Hecho cuando:** existen esos tests y la sección "tiempo acelerado" del 4+1 explica el diseño con números.

### B2. Integración real con el simulador nuevo, con evidencia
- **Qué:** un script `scripts/integracion-simulador.sh` que haga lo siguiente:
  1. levantar el compose lab;
  2. esperar el registro;
  3. cargar el sitio;
  4. hacer INICIAR con un factor (por ejemplo, ×60);
  5. guardar `/diagnostico` cada N segundos en `docs/tests/corrida-simulador/`.

  Correrlo al menos con:
  - carga no limitante;
  - carga limitante;
  - cruce de franja punta acelerado;
  - broker caído y recuperado (`docker stop`/`start` del broker del simulador);
  - un switch caído, si el simulador lo permite.
- **Entregar:**
  - los logs resumidos;
  - una tabla en `resultados.md` con qué se esperaba, qué se observó y si pasó;
  - capturas o el JSON del diagnóstico en los momentos clave.
- **Por qué:** la letra habla de la "prueba final del prototipo" con el simulador. Los tests con dobles (Moquette y switches falsos) son necesarios pero no suficientes: no prueban el contrato contra el software de terceros.

### B3. Arranque y parada justificados como decisión de diseño
1. **DETENER.** Hoy deja todo como estaba, lo que el §5.2 permite. Para una entrega excelente hay dos caminos:
   - **(a)** agregar `control.al-detener=APAGAR|DEJAR`, con sus tests. El default es `APAGAR`, el más seguro: sin control no hay garantía de punta ni de potencia;
   - **(b)** mantener `DEJAR`, pero escribir el análisis de riesgos (sobrecalentamiento, consumo en punta, superar la potencia si alguien enciende a mano) y por qué se acepta.

   En cualquier caso, el comportamiento elegido tiene que estar en el README, en el 4+1 (escenario de parada) y en un test.
2. **Caída del contenedor.** Decidir qué pasa al reiniciar con `restart: unless-stopped`.
   - **Opción 1:** persistir el último estado del control (en ejecución, `fechaHora` de referencia, factor) junto al inventario y retomar solo, re-sincronizando el reloj con el tiempo real transcurrido. Al retomar, primero consultar los switches (§4) y reconciliar.
   - **Opción 2:** arrancar DETENIDO y documentarlo como decisión, con el riesgo explícito: calefacción encendida sin control hasta el próximo INICIAR.

   Un test de integración que reinicie el contexto Spring a mitad de una corrida cubre ese caso.
3. **Apagado ordenado (`@PreDestroy`).** Si se elige `APAGAR`, aplicarlo también al bajar el contenedor (SIGTERM), con un timeout acotado.

### B4. MQTT: decisiones explícitas
- **Sesión:** documentar por qué se usa `cleanSession=true` con QoS 1: las lecturas son "último valor" y no tiene sentido recibir viejas al reconectar. O cambiar a una sesión persistente y documentar el porqué.
- **Re-suscripción:** al reconectar, verificar con un test que se re-suscribe a todos los topics del inventario vigente, incluidos los que cambiaron mientras el broker estaba caído. Se puede combinar `FallaBrokerMqttTest` con un `PUT /sitio` durante la caída.
- **Fail-safe:** mientras el broker está caído, las lecturas van venciendo y las habitaciones se apagan por `LECTURA_VENCIDA`. Explicitar que es intencional en el escenario 2.6 del 4+1.

### B5. Switch que no responde: completar el caso
Ya está muy bien resuelto (OFF antes que ON, retención del ON). Falta:
- **Timeout total de una pasada con N switches lentos.** Las pasadas son secuenciales, así que un switch colgado demora a todos hasta su timeout. Por ejemplo: con 10 habitaciones y 5 s de timeout, ¿cuánto tarda un corte de punta? Hay que medirlo y documentarlo, o paralelizar con un timeout global por pasada.
- **Un test de switch lento** (responde a los 4 s), además del caído (503).

### B6. Estándar de codificación verificable más allá del formato
- **Qué:**
  - agregar **Checkstyle** con la configuración de Google (`google_checks.xml`, ajustada a AOSP con 4 espacios) para lo que Spotless no cubre: llaves obligatorias, convenciones de nombres, imports con comodín, largo de métodos;
  - opcionalmente, **PMD** o **SpotBugs** con un set chico de reglas;
  - ejecutar las tarjetas T2 y T10: la sección "Java que usamos" y la refactorización correspondiente. Las reglas de esa sección son: loops antes que streams largos, sin ternarios anidados, referencias `::` solo si se leen solas y `Optional` solo como retorno.
- **Evidencia:**
  - la salida de `mvn checkstyle:check` en `docs/calidad/`, como ya se hace con Spotless;
  - el enlace a la corrida de CI.
- **Hecho cuando:** cada regla del estándar indica qué herramienta la verifica, o dice explícitamente que se revisa en el PR.

### B7. Arquitectura 4+1 completa
- **Vista física:** un diagrama de despliegue que muestre:
  - el contenedor del controlador;
  - el simulador (broker, switches, inventario);
  - las redes Docker (`iiss-lab-net` frente al anfitrión);
  - los puertos;
  - el volumen `inventario`;
  - quién inicia cada conexión: MQTT saliente, REST entrante y saliente, registro §6.

  Ya está todo en los dos compose: falta dibujarlo.
- **Vista de desarrollo:** reemplazar el árbol ASCII por un diagrama de paquetes o componentes con las dependencias permitidas:
  - core sin dependencias;
  - engine → core;
  - rest → servicios;
  - nadie → adaptador, salvo control.

  Si se puede, verificarlo con un test de **ArchUnit**.
- **Vista lógica y escenarios:**
  - actualizarlos después de A3–A6: sin `CoreClient`, con el camino corto de la lectura, con el `ts` y con la zona;
  - agregar los escenarios "INICIAR con factor alto y cruce de franja" y "DETENER / reinicio del contenedor".
- **Vista de procesos:** agregar el temporizador de bordes de franja (B1) y los timeouts de los switches (B5).
- **Hecho cuando:** hay al menos un diagrama por cada vista y todos coinciden con el código del tag.

### B8. Criterio de adecuación actualizado
`criterio-tests.md` sigue titulado "Iteración 4". Hay que actualizarlo para que incluya:
- **Los tests de integración** como parte del criterio, con una matriz que vaya de cada requisito de la letra de la iteración 5 al test que lo verifica. Tiene que cubrir las seis preguntas del "Objetivo" y los tres casos mínimos de integración.
- **La cobertura de ramas**, no solo de líneas, y el análisis de las ramas que quedan sin cubrir.
- **(Recomendado) mutation testing con PIT** sobre el core (`pitest-maven`), reportando el mutation score. Es la mejor respuesta a "¿99 % de cobertura significa que los tests son buenos?".
- **La semilla del test de invariantes:** si usa 3000 estímulos seudoaleatorios, documentar la semilla fija y cómo reproducir un fallo.

### B9. Tests de integración más robustos
- **Esperas:** revisar las esperas con `Thread.sleep` y los límites de tiempo fijos (`PT5S`) para que no sean intermitentes en la CI, que es más lenta. Usar un helper de espera con condición (ya existe `esperarSwitch`) y timeouts configurables.
- **Estabilidad:** correr la suite de integración 10 veces seguidas en la CI, o localmente, y dejar constancia de 0 fallas intermitentes.

---

## C. Para destacar

### C1. Característica opcional: servidor MCP
- **Qué:** un módulo `modules/mcp` con Spring AI (`spring-ai-starter-mcp-server-webmvc`, transporte Streamable HTTP), desplegado como un servicio más en el `docker-compose.yml`.
- **Diseño:** es un adaptador delgado que llama por REST al API del controlador, o reutiliza sus servicios, sin tocar el core ni `CoreAdapter`. Ese es el punto que quiere ver el docente: la separación core/engine permite agregar un canal nuevo a bajo costo.
- **Herramientas sugeridas**, cada una con una descripción escrita para que un modelo decida cuándo usarla:
  - `listar_habitaciones`;
  - `ver_habitacion`;
  - `configurar_habitacion` (temperatura esperada, termostato, switch);
  - `accionar_switch` (ON/OFF). La descripción tiene que advertir que, con el control en ejecución, la reconciliación puede revertirlo;
  - `estado_controlador`;
  - `iniciar_control` y `detener_control`.
- **Entregables:**
  - `docs/mcp/herramientas.md`, con nombre, descripción y esquema de cada herramienta;
  - `docs/mcp/rest-vs-mcp.md`;
  - una demo con MCP Inspector (capturas o grabación) de las tres operaciones.
- **Ideas para `rest-vs-mcp.md`:**
  - REST se diseña alrededor de recursos y verbos, para un programa que ya conoce el contrato;
  - MCP se diseña alrededor de intenciones, para un modelo que elige la herramienta leyendo la descripción;
  - por eso cambian la granularidad, los mensajes de error (tienen que poder leerse en lenguaje natural), las confirmaciones en las acciones riesgosas y la idempotencia.

### C2. Observabilidad
- **Métricas** con Spring Boot Actuator:
  - lecturas recibidas o descartadas, por motivo;
  - comandos a switches OK o fallidos;
  - potencia "comprometida" frente a la contratada;
  - estado de MQTT;
  - desfase del reloj del control.
- **Health:** exponer `/actuator/health` con indicadores de MQTT y del registro, y usarlo como healthcheck en el compose.

### C3. Calidad de la documentación
- **Coherencia:** revisar estos puntos:
  - el encabezado del compose dice "Iteración 4";
  - el PDF de integración menciona la "rama implementacion4";
  - hay un enlace de Jira de otro grupo;
  - los conteos de tests de todos los documentos tienen que salir de la corrida, no escribirse a mano.
- **Un `CHANGELOG.md` por iteración:** qué cambió, qué observación atendió y qué test lo demuestra.
- **En el README, una sección "Cómo verificar esta entrega en 5 minutos":** los comandos exactos para build, test, cobertura, estándar y demo contra el simulador, con lo que debería verse.

---

## Orden sugerido de trabajo

| Orden | Cambios | Motivo |
|---|---|---|
| 1 | A1, A2 | Rápidos, y protegen todo lo que viene después: cualquier refactor queda verificado por la CI |
| 2 | A3 → A4 → A5 → A6 | Refactor del core y correcciones del docente. Van en serie porque tocan los mismos archivos (`CoreAdapter`, `ControlService`) |
| 3 | B1, B3 | Tiempo acelerado y arranque/parada: el núcleo de la letra de la iteración 5 |
| 4 | B2 | Corrida contra el simulador con todo lo anterior ya integrado |
| 5 | B4, B5, B8, B9 | Endurecimiento y criterio de tests |
| En paralelo | B6, B7, C3 | Documentación y herramientas, sin conflicto con el código |
| Si queda tiempo | C1, C2 | Diferenciales |
| Al final | A7 + tag `version5` | Registro de lo resuelto, zip a Moodle idéntico al tag |

## Checklist final antes del tag

- [ ] CI verde sobre el commit del tag, con `mvn verify` (Spotless, Checkstyle, tests y JaCoCo `check`).
- [ ] Core sin estado; sin `CoreClient`; cobertura del core ≥ 85 % en líneas y ramas, y mutation score reportado.
- [ ] `ts` usado; zona `America/Montevideo` explícita; sin `ZoneId.systemDefault()` en el código.
- [ ] Tiempo acelerado: cruce de franja probado con factor alto; regla de vigencia justificada; factor verificado contra el simulador nuevo.
- [ ] DETENER y reinicio del contenedor: comportamiento decidido, documentado y testeado.
- [ ] Corrida contra el simulador nuevo, con evidencia en `docs/tests/`.
- [ ] 4+1 con un diagrama por vista (incluida la física), coherente con el código.
- [ ] `criterio-tests.md` con matriz requisito → test; `resultados.md` y README con conteos reales.
- [ ] `revision-docente.md` como registro de lo resuelto.
- [ ] Zip de Moodle = tag `version5`.

---

## Postura del equipo

Este documento está escrito desde el punto de vista del docente. Esta sección registra qué decidió el equipo sobre cada cambio. El plan del equipo para tocar Core y Engine (`docs/revision-docente.md`, tarjetas T4 y T5) se considera el mejor camino.

| Cambio | Decisión | Motivo | Estado (commit `67fe9a9`) |
|---|---|---|---|
| A1 CI con `mvn -B verify` + artefactos | Adoptar | Hoy ninguna máquina ejecuta lo que el estándar dice que falla en `verify`. | Hecho en `67fe9a9` |
| A2 JaCoCo `check` ≥ 85 % en líneas y ramas | Adoptar | La letra pide "asegurar" la cobertura; una regla en `verify` lo asegura. | Hecho en `67fe9a9` |
| A3 Core sin estado | Adoptar | `Estimulo` queda en `core/model` y el `switch` pasa a `CoreAdapter.procesar`. Es el plan de las tarjetas T4/T5. | Hecho en `67fe9a9` (T4 y T5) |
| A4 Una sola bandera de estado de ejecución | Ajustar | Se elimina el `try/catch` duplicado (queda solo el de `messageArrived`). `67fe9a9` dejó una sola bandera con `ObjectProvider` (inyección perezosa); el equipo lo considera un parche poco intuitivo. D6: el estado de ejecución pasó a `CoreAdapter` y `ControlService` le delega (tarjeta T7b, hecha). | Hecho (T7b) |
| A5 Usar el `ts` del termostato | Adoptar | Con la regla de tiempo acelerado aclarada por el docente: el `ts` es simulado y se toma tal cual (ver notas). | Parcial → T8b / T8c |
| A6 Zona horaria explícita | Adoptar | Requisito: la zona es Uruguay, sí o sí (ver notas). | Pendiente (T9) |
| A7 `revision-docente.md` como registro | Adoptar | El repositorio no debe decir que las correcciones no están hechas cuando sí lo están. | Pendiente |
| B1(a) Eventos en los bordes de franja | Descartar | Suma la complejidad que el docente marcó como sobrediseño. | — |
| B1 resto (granularidad, vigencia, tests acelerados) | Adoptar | Tick con opción (b): `max(10 ms, tickMs / factor)`, error máximo documentado en `4mas1.md`. Vigencia en tiempo simulado, sin escalar por el factor (T8c). Tests acelerados de cruce de franja incluidos. | Tick pendiente (T12); vigencia pendiente (T8c); tests acelerados pendientes |
| B2 Corrida real contra el simulador | Adoptar, prioridad alta | Es la "prueba final del prototipo" de la letra. | Pendiente (T13) |
| B3 Arranque y parada | Ajustar | `DETENER` mantiene `DEJAR` (el §5.2 no exige apagar), con análisis de riesgos y un test. El reinicio del contenedor arranca DETENIDO. | Pendiente |
| B4 MQTT: decisiones explícitas | Adoptar | Son decisiones que hay que dejar escritas y testeadas. | Pendiente |
| B5 Peor caso de switches lentos | Adoptar | Se mide y documenta; no se paraleliza. | Pendiente |
| B6 Estándar verificable | Ajustar | Solo Checkstyle mínimo + tarjetas T2/T10. PMD/SpotBugs: Diferir. | Pendiente |
| B7 Arquitectura 4+1 completa | Adoptar (vistas) / Diferir (ArchUnit) | Se dibujan todas las vistas, incluida la física. ArchUnit queda para después. | Pendiente |
| B8 Criterio de adecuación (matriz) | Adoptar | Matriz requisito → test y semilla documentada. | Pendiente |
| B8 PIT (mutation testing) | Diferir | Solo si sobra tiempo. | Diferido |
| B9 Tests de integración robustos | Adoptar | Evita intermitencias en la CI. | Pendiente |
| C1 Servidor MCP | Diferir | Opcional, según el tiempo. | Diferido |
| C2 Actuator / observabilidad | Diferir | Fuera del alcance de la iteración. | Diferido |
| C3 Calidad de la documentación | Adoptar | Con una corrección sobre el enlace de Jira (ver notas). | Pendiente |

### Notas

**A4 (bandera de estado de ejecución).** `ControlService` ya depende de `CoreAdapter`; derivar `habilitado` de `ControlService` a secas habría creado un ciclo. En `67fe9a9` se resolvió con un `BooleanSupplier` que `CoreAdapter` recibe vía `ObjectProvider<ControlService>` en `CoreConfig` (inyección perezosa): queda una sola bandera, pero el ciclo queda escondido. Por eso D6 de `revision-docente.md` la movió a `CoreAdapter` (`iniciar()` / `detener()` / `enEjecucion()`), `ControlService` le delega y desaparecieron el `ObjectProvider` y el `BooleanSupplier` (tarjeta T7b, hecha).

**A5 (`ts` en tiempo acelerado).** El tiempo del sistema es simulado. El `ts` del termostato viene en tiempo simulado, en la misma escala que el reloj del control (`fechaHora` + tiempo transcurrido × factor), así que **se toma tal cual**: no se convierte y no se aplica ninguna fórmula (la conversión `fechaHora + (tsReal − iniciadoEn) × factorTiempo` ya no aplica). Los mensajes MQTT llegan y se procesan en tiempo real, pero con intervalos inconsistentes, porque el simulador provoca a propósito atrasos, caídas y velocidad acelerada; por eso la hora sale del contexto recibido (reloj del control y `ts`) y nunca de la máquina donde corre el controlador.

La conversión que dejó `67fe9a9` (`RelojControl.instanteDeLectura` y `aTiempoDeControl`) y la propiedad `termostato.ts-tiempo-real` se eliminan en T8b. Reglas:

- si falta el `ts`, o queda en el futuro respecto de `reloj.ahora()`, se usa `reloj.ahora()`;
- las lecturas con `ts` anterior a la última guardada se descartan;
- una lectura con `ts` más viejo que la vigencia se descarta y se cuenta en `/diagnostico` (T8b; decisión del equipo pendiente, D8 de `revision-docente.md`). Hoy se registra con `ahora` y sostendría la calefacción con un dato viejo.

Esto resuelve también la duda de B1 sobre la vigencia: si todo está en tiempo de control, la vigencia se mide en tiempo de control. Hoy `Core.vencida` calcula `vigenciaMs = vigenciaLectura × factorTiempo`: con factor 30 y 600 s son 5 h simuladas y ninguna lectura vence en toda la corrida. T8c quita esa multiplicación y saca `factorTiempo` de `Core.decidir`.

**A6 (zona horaria).** La zona es Uruguay, sin excepciones. Implementación:

- constante en el código `ZoneId ZONA_URUGUAY = ZoneId.of("America/Montevideo")`, usada como default de `control.zona-horaria`;
- variable `CONTROL_ZONA_HORARIA=America/Montevideo` explícita en `docker/docker-compose.yml`;
- `TZ=America/Montevideo` en el contenedor, para que los logs también salgan en hora local;
- se elimina el uso de la zona del offset de `fechaHora` y todo `ZoneId.systemDefault()`.

**B1 (tiempo acelerado).** Se descarta programar eventos en los bordes de franja (a). El tiempo acelerado se resuelve con un solo reloj, el del control (regla de A5): la vigencia se mide en tiempo simulado, sin escalarla por el factor (T8c). La granularidad del tick va con la opción (b): período del tick = `max(10 ms, tickMs / factor)` (T12), con error máximo del corte de la franja ≤ un tick en tiempo simulado, documentado en `4mas1.md`.

**B3 (DETENER y reinicio).** `DETENER` mantiene `DEJAR` porque el §5.2 del estándar no exige apagar; el análisis de riesgos (sobrecalentamiento, consumo en punta, potencia si alguien enciende a mano) queda documentado y cubierto por un test. Un reinicio del contenedor arranca DETENIDO, y se documenta. No se persiste el estado de ejecución.

**B5 (peor caso).** Timeouts de 2 s de conexión + 3 s de lectura implican hasta 5 s por switch, con pasadas secuenciales. Se mide y documenta ese peor caso; no se paraleliza.

**B8 (semilla).** La semilla del test de invariantes ya está fija en 42; solo falta documentarla y explicar cómo reproducir un fallo.

**C3 (enlace de Jira).** El enlace de Jira **no** es de otro grupo. El sitio de Atlassian se creó con un dominio mal nombrado (`estudiantes-grupo8-2026.atlassian.net`) y aloja tanto el espacio de PdeA como el de Ingeniería (esta materia); el espacio enlazado, G1234 "Grupo4_2026_Ing", es el del grupo 4. Acción: aclararlo en una línea en el README, junto al enlace.

Pendiente de confirmar con el simulador nuevo: que el paso a modo acelerado llega como un nuevo INICIAR con `factorTiempo` (§5.2), que es el único canal del contrato (el yaml nuevo no agrega otro). Si el simulador acelera sin enviar INICIAR, los `ts` quedarían en el futuro y la tarifa se calcularía con un reloj equivocado.

El detalle de los tres relojes y qué es letra frente a decisión del equipo está en la sección 5 de [`revision-docente.md`](revision-docente.md).
