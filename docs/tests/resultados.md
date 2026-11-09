# Resultados de la ejecución de las pruebas — Iteración 6

Entregable "Ejecución de las pruebas unitarias, con sus resultados". El criterio por el que el
conjunto se considera adecuado está en [`criterio-tests.md`](criterio-tests.md). La cobertura y la
verificación del estándar de codificación están en [`../calidad/`](../calidad/README.md).

## Cómo se reproduce

```bash
./scripts/test.sh            # todos los módulos (Docker, Java 25, mvn clean test)
./scripts/test.sh core       # solo el core
mvn verify                   # local: spotless:check + tests + cobertura
```

Los reportes detallados quedan en `modules/<módulo>/target/surefire-reports/` y el de cobertura
del core en `modules/core/target/site/jacoco/index.html` (copia en
[`docs/calidad/cobertura/`](../calidad/cobertura/README.md)).

## Última ejecución — 2026-11-09 (rama `iter6/t38-t40-casos-extremos`, sobre `implementacion6`, Java 25)

**Resultado: BUILD SUCCESS — 289 tests, 0 fallas, 0 errores, 0 omitidos** (`mvn clean verify`).

| Módulo | Tests | Fallas |
|---|---|---|
| `core` | 57 | 0 |
| `engine` | 232 | 0 |
| **Total** | **289** | **0** |

Respecto del cierre (2026-11-08: 56 + 225 = 281) se sumaron 8 tests de los casos extremos T38–T40 (A20–A22). Respecto de la ejecución de la iteración 5 cerrada (2026-11-03: 54 + 185 = 239), los 42 anteriores son de las
correcciones de la iteración 6: el corte de la punta anticipado (T24e), el `ts` en dos escalas
(T24b), las respuestas de switch y de termostato fuera de contrato (T24d, T31, T33), el switch que no
prende (T34), el cliente MQTT 3.1.1 (T35), la reconciliación escalada (T36), el switch colgado y
las pruebas de estrés (T37) y el sitio que crece y se achica (T32).

**Por qué el core tiene menos tests que en la iteración 5 (116).** Con el core sin estado (T4/T5)
quedó solo la función pura `decidir` y el modelo; los tests de estímulos que eran `CoreTest` (lectura,
tick, configuración, INICIAR) pasaron al engine como `CoreAdapterEstimulosTest`, junto con el
manejo de estímulos que verifican: esos casos no se perdieron, cambiaron de módulo. `CoreClientTest`
sí se borró, junto con la fachada `CoreClient` que probaba.

### Módulo `core` (TDD de la función de decisión) — 57 tests

| Clase de test | Grupo | Tests | Fallas |
|---|---|---|---|
| `DecidirTest` | Una habitación | 6 | 0 |
| `DecidirTest` | Reparto de potencia | 8 | 0 |
| `DecidirTest` | Criterio de prioridad | 5 | 0 |
| `DecidirTest` | Histéresis | 4 | 0 |
| `DecidirTest` | Tarifa punta (con el corte anticipado, T24e, y ampliado, T39) | 10 | 0 |
| `DecidirTest` | Lecturas vencidas | 5 | 0 |
| `DecidirTest` | Lectura válida y situación incompleta | 4 | 0 |
| `DecidirTest` | Tope de potencia | 1 | 0 |
| `MayorDeficitPrimeroTest` | criterio de prioridad | 9 | 0 |
| `ModeloTest` | validaciones del modelo | 5 | 0 |
| **Total** | | **57** | **0** |

Cobertura (JaCoCo) del módulo `core` — el build exige ≥ 85 % de líneas y de ramas:

| Métrica | Cubierto | Total | % |
|---|---|---|---|
| Líneas | 163 | 163 | 100 % |
| Ramas | 87 | 92 | 94,6 % |

### Módulo `engine` — 232 tests

Estímulos (antes `CoreTest`, en el core):

| Clase de test | Grupo | Tests | Fallas |
|---|---|---|---|
| `CoreAdapterEstimulosTest` | Decisión básica | 8 | 0 |
| `CoreAdapterEstimulosTest` | Tarifa punta: cortar y restituir | 10 | 0 |
| `CoreAdapterEstimulosTest` | Nunca superar el consumo máximo | 7 | 0 |
| `CoreAdapterEstimulosTest` | Criterio de selección | 6 | 0 |
| `CoreAdapterEstimulosTest` | Configuración del sitio | 5 | 0 |
| `CoreAdapterEstimulosTest` | Histéresis | 5 | 0 |
| `CoreAdapterEstimulosTest` | Lecturas defectuosas | 4 | 0 |
| `CoreAdapterEstimulosTest` | Lecturas vencidas (termostato caído) | 6 | 0 |
| `CoreAdapterEstimulosTest` | Reloj del control y re-sincronización | 7 | 0 |
| `CoreAdapterEstimulosTest` | Invariantes | 3 | 0 |

Unitarios y de API:

| Clase de test | Qué verifica | Tests | Fallas |
|---|---|---|---|
| `CoreAdapterTest` | decisiones → comandos a switches, consulta §4, reconciliación, switches caídos, OFF antes que ON, estado de ejecución, lecturas descartadas; el switch que no prende cede su potencia (T34); los sanos antes que los que fallan y los timeouts ajustados al factor (T37); habitación que sale del sitio encendida (T38), switches lentos (T39) y switch que no obedece (T40) | 37 | 0 |
| `RelojControlTest` | reloj del control con aceleración (§5.2), conversión del `ts` en hora real (T8b, D8) y `ts` en hora del control (T24b) | 15 | 0 |
| `ApiEstandarTest` | API §5 con contexto Spring completo (sitio, control, 400/401/404/409, ABM, INICIAR sin offset en hora de Montevideo) | 14 | 0 |
| `ValidadorInventarioTest` | reglas de consistencia del §1 | 10 | 0 |
| `ClienteSwitchRestTest` | respuesta del switch leída como texto (T24d), fuera de contrato (T31) y timeout escalado con el factor (T37) | 9 | 0 |
| `PayloadTermostatoTest` | mensaje MQTT del termostato (§2), también fuera de contrato: `tC` o `ts` como texto, `ts` en milisegundos (T33) | 6 | 0 |
| `RegistroEntornoTest` | broker informado por el registro (§6) y aviso si el entorno no puede alcanzar la `baseUrl` (T24a) | 6 | 0 |
| `RepositorioInventarioTest` | persistencia del inventario | 4 | 0 |
| `ControlServiceTest` | períodos del tick (T12) y de la reconciliación (T36) escalados por el factor, con su mínimo | 3 | 0 |
| `LecturaMqttListenerTest` | identificador del cliente MQTT de 23 caracteres como máximo (T35) | 1 | 0 |

De integración (broker MQTT embebido + switches HTTP falsos):

| Clase de test | Qué verifica | Tests | Fallas |
|---|---|---|---|
| `ArranqueParadaTest` | distintas situaciones iniciales; qué se hace con la calefacción al iniciar y al detener | 12 | 0 |
| `TermostatoCaidoTest` | un termostato deja de publicar; re-INICIAR no revive lecturas vencidas (D7), también hacia atrás | 8 | 0 |
| `ControlCargaTest` | la carga total limita / no limita; nunca se supera la potencia contratada; franja punta en hora de Montevideo | 7 | 0 |
| `CambioConfiguracionTest` | cambios de configuración con el control en ejecución | 7 | 0 |
| `PerdidaMensajesTest` | pérdida de mensajes; `ts` fuera de orden, vencido o temperatura fuera de rango se descartan y se cuentan (D11) | 6 | 0 |
| `FallaSwitchTest` | switch que no responde o falla; sin superar la contratada si falla un OFF | 5 | 0 |
| `FallaBrokerMqttTest` | el broker MQTT cae y se recupera, también al INICIAR y con el `ts` en hora del control | 5 | 0 |
| `EstresTest` | 3 switches colgados cruzando la punta a ×60, ×600 y ×3600, 50 habitaciones y fallas combinadas (T37); 20 switches lentos (T39) | 6 | 0 |
| `EscenariosSimuladorTest` | escenarios como los corre el simulador (T25) y crecer de 5 a 20 habitaciones y achicar (T32) | 4 | 0 |
| `TiempoAceleradoTest` | a ×600 el corte y la restitución de la punta llegan en menos de 1 s real (T12); re-INICIAR con otro factor (T17) | 4 | 0 |
| `ReconciliacionEscaladaTest` | un switch prendido a mano a ×60 vuelve a OFF en segundos reales (T36) | 2 | 0 |
| **Total engine** | | **232** | **0** |

Los mensajes `ERROR` del log de la corrida son esperados: ver la última sección.

### Falla corregida

Esta falla apareció en una corrida de la iteración 5 (2026-10-27) y quedó corregida; se conserva como registro.

En la primera corrida falló `FallaBrokerMqttTest.caidaLarga_seReconectaEnSegundos`:

```
AssertionFailedError: el engine no se reconectó ni se re-suscribió en PT5S
```

El test baja la espera entre reintentos a 1 s, pero el engine no se reconectaba ni a los 5 s. El
log mostró la causa: clientes MQTT de contextos de Spring **ya cerrados** seguían reconectándose
al broker. Eran dos problemas de la reconexión automática de Paho 1.2.5:

1. Un cliente que se cierra mientras está reintentando rechaza el `close()` ("Conexión ya en
   curso"), queda vivo y sigue reconectándose para siempre. En producción pasaría lo mismo al
   cambiar de broker con el anterior caído.
2. La espera entre reintentos es un campo **estático** (`MqttAsyncClient.reconnectDelay`): la
   comparten todos los clientes de la JVM y se duplica en cada intento fallido. Los clientes que
   quedaban vivos la inflaban también para el cliente nuevo.

Corrección en `LecturaMqttListener`: se apaga la reconexión automática de Paho. Ante
`connectionLost` se cierra el cliente caído (ya desconectado, así que el `close()` funciona) y
el hilo `mqtt-conexion` reintenta cada `mqtt.reconexion-max-segundos` con un cliente nuevo. El
test no se modificó en lo que verifica; solo se actualizaron sus comentarios. Después del cambio:
`mvn verify` completo sin fallas, `FallaBrokerMqttTest` aislado 3 veces seguidas sin fallas
(27 s cada corrida, antes 35 s) y ningún aviso "Error al cerrar el cliente MQTT" en el log.

## Mensajes `ERROR` / `WARN` esperados en el log

Algunos tests provocan fallas a propósito para verificar la robustez; los mensajes de error que
aparecen en la salida **no** son fallas de los tests:

| Mensaje | Lo provoca | Qué demuestra |
|---|---|---|
| `No se pudo leer el inventario guardado … Unexpected character` | `RepositorioInventarioTest` (archivo corrupto) | el controlador arranca igual, sin sitio |
| `No se pudo conectar al broker MQTT en tcp://127.0.0.1:1` | `ApiEstandarTest` (broker inexistente) | el API funciona con MQTT caído y se reintenta cada 5 s |
| `Switch … está DESCONOCIDO y se creía ON: se corrige` | `CoreAdapterTest` (switch reiniciado / movido a mano) | la reconciliación corrige el estado real |
| `No se pudo consultar el switch …` | `CoreAdapterTest` (switch caído) | un switch caído no bloquea a los demás |
| `Conexión perdida con el broker MQTT …` / `No se pudo conectar al broker MQTT en tcp://127.0.0.1:<puerto>` | `FallaBrokerMqttTest` (broker embebido detenido) | el control sigue con los switches como estaban y se reconecta al volver el broker |
| `Payload inválido en '…', se descarta` | `PerdidaMensajesTest` (mensajes corruptos) | un mensaje inválido no cambia la decisión |
| `No se pudo enviar OFF a el switch …` + `Se retiene el ON de '…'` | `FallaSwitchTest` (falla un OFF) | no se supera la potencia contratada aunque un switch no confirme el OFF |
