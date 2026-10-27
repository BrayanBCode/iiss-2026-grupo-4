# Resultados de la ejecución de las pruebas — Iteración 5

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

## Última ejecución — 2026-10-27 (rama `implementacion5`, Java 25)

**Resultado: 215 tests, 214 pasan, 1 falla, 0 errores, 0 omitidos.** El `core` pasa completo;
en el `engine` falla un test de robustez ante caídas del broker (ver
[Falla conocida](#falla-conocida)).

| Módulo | Tests | Fallas |
|---|---|---|
| `core` | 116 | 0 |
| `engine` | 99 | 1 |
| **Total** | **215** | **1** |

### Módulo `core` (TDD de la función de decisión) — 116 tests

| Clase de test | Grupo | Tests | Fallas |
|---|---|---|---|
| `DecidirTest` | función `decidir` por situación: una habitación, reparto de potencia, criterio, histéresis, tarifa punta, lecturas vencidas, tope | 35 | 0 |
| `CoreTest` | 1. Decisión básica | 8 | 0 |
| `CoreTest` | 2. Tarifa punta: cortar y restituir | 10 | 0 |
| `CoreTest` | 3. Nunca superar el consumo máximo | 7 | 0 |
| `CoreTest` | 4. Criterio justificable y no azaroso | 6 | 0 |
| `CoreTest` | 5. Configuración del sitio | 9 | 0 |
| `CoreTest` | 6. Histéresis | 5 | 0 |
| `CoreTest` | 7. Lecturas defectuosas | 4 | 0 |
| `CoreTest` | 8. Lecturas vencidas (termostato caído) | 6 | 0 |
| `CoreTest` | 9. Reloj del control | 5 | 0 |
| `CoreTest` | 10. Invariantes | 3 | 0 |
| `MayorDeficitPrimeroTest` | criterio de prioridad | 9 | 0 |
| `CoreClientTest` | fachada del core | 6 | 0 |
| `ModeloTest` | validaciones del modelo | 3 | 0 |
| **Total** | | **116** | **0** |

Cobertura (JaCoCo) del módulo `core` — objetivo ≥ 85 % de líneas:

| Métrica | Cubierto | Total | % |
|---|---|---|---|
| Líneas | 237 | 238 | 99,6 % |
| Ramas | 118 | 130 | 90,8 % |

### Módulo `engine` — 99 tests

Unitarios y de API:

| Clase de test | Qué verifica | Tests | Fallas |
|---|---|---|---|
| `CoreAdapterTest` | decisiones → comandos a switches, consulta §4, reconciliación, switches caídos, OFF antes que ON | 21 | 0 |
| `ApiEstandarTest` | API §5 con contexto Spring completo (sitio, control, 400/401/404/409, ABM) | 13 | 0 |
| `ValidadorInventarioTest` | reglas de consistencia del §1 | 9 | 0 |
| `RepositorioInventarioTest` | persistencia del inventario | 4 | 0 |
| `RelojControlTest` | reloj del control con aceleración (§5.2) | 4 | 0 |
| `PayloadTermostatoTest` | mensaje MQTT del termostato (§2) | 3 | 0 |
| `RegistroEntornoTest` | broker informado por el registro (§6) | 2 | 0 |

De integración (broker MQTT embebido + switches HTTP falsos, nuevos en la iteración 5):

| Clase de test | Qué verifica | Tests | Fallas |
|---|---|---|---|
| `ControlCargaTest` | la carga total limita / no limita; nunca se supera la potencia contratada | 6 | 0 |
| `ArranqueParadaTest` | distintas situaciones iniciales; qué se hace con la calefacción al iniciar y al detener | 11 | 0 |
| `CambioConfiguracionTest` | cambios de configuración con el control en ejecución | 7 | 0 |
| `FallaBrokerMqttTest` | el broker MQTT cae y se recupera | 4 | **1** |
| `PerdidaMensajesTest` | pérdida de mensajes de los termostatos | 5 | 0 |
| `FallaSwitchTest` | switch que no responde o falla; sin superar la contratada si falla un OFF | 5 | 0 |
| `TermostatoCaidoTest` | un termostato deja de publicar | 5 | 0 |
| **Total engine** | | **99** | **1** |

### Falla conocida

`FallaBrokerMqttTest.caidaLarga_seReconectaEnSegundos`:

```
AssertionFailedError: el engine no se reconectó ni se re-suscribió en PT5S
```

El test apaga el broker 8 s y, al levantarlo, exige que el engine esté reconectado y
re-suscripto en 5 s. La espera máxima entre reintentos de Paho también está acotada a 5 s
(`mqtt.reconexion-max-segundos`), así que el margen del test es nulo: si el próximo reintento cae
justo antes de que el broker vuelva, la reconexión llega apenas después de los 5 s. Queda
registrada para que la revise el responsable de los tests de fallas (Persona 3).

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
