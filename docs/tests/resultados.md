# Resultados de la ejecución de las pruebas — Iteración 4

Entregable "Ejecución de las pruebas unitarias, con sus resultados". El criterio por el que el
conjunto se considera adecuado está en [`criterio-tests.md`](criterio-tests.md).

## Cómo se reproduce

```bash
./scripts/test.sh            # todos los módulos (Docker, Java 25, mvn clean test)
./scripts/test.sh core       # solo el core
```

Los reportes detallados quedan en `modules/<módulo>/target/surefire-reports/` y el de cobertura
del core en `modules/core/target/site/jacoco/index.html`.

## Última ejecución — 2026-10-14

**Resultado: BUILD SUCCESS — 172 tests, 0 fallas, 0 errores, 0 omitidos.**

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

Cobertura (JaCoCo) del módulo `core`:

| Métrica | Cubierto | Total | % |
|---|---|---|---|
| Líneas | 226 | 227 | 99 % |
| Ramas | 118 | 130 | 91 % |

### Módulo `engine` (integración) — 56 tests

| Clase de test | Qué verifica | Tests | Fallas |
|---|---|---|---|
| `CoreAdapterTest` | decisiones → comandos a switches, consulta §4, reconciliación, switches caídos | 21 | 0 |
| `ApiEstandarTest` | API §5 con contexto Spring completo (sitio, control, 400/401/404/409, ABM) | 13 | 0 |
| `ValidadorInventarioTest` | reglas de consistencia del §1 | 9 | 0 |
| `RepositorioInventarioTest` | persistencia del inventario | 4 | 0 |
| `RelojControlTest` | reloj del control con aceleración (§5.2) | 4 | 0 |
| `PayloadTermostatoTest` | mensaje MQTT del termostato (§2) | 3 | 0 |
| `RegistroEntornoTest` | broker informado por el registro (§6) | 2 | 0 |
| **Total** | | **56** | **0** |

## Mensajes `ERROR` / `WARN` esperados en el log

Algunos tests provocan fallas a propósito para verificar la robustez; los mensajes de error que
aparecen en la salida **no** son fallas de los tests:

| Mensaje | Lo provoca | Qué demuestra |
|---|---|---|
| `No se pudo leer el inventario guardado … Unexpected character` | `RepositorioInventarioTest` (archivo corrupto) | el controlador arranca igual, sin sitio |
| `No se pudo conectar al broker MQTT en tcp://127.0.0.1:1` | `ApiEstandarTest` (broker inexistente) | el API funciona con MQTT caído y se reintenta cada 5 s |
| `Switch … está DESCONOCIDO y se creía ON: se corrige` | `CoreAdapterTest` (switch reiniciado / movido a mano) | la reconciliación corrige el estado real |
| `No se pudo consultar el switch …` | `CoreAdapterTest` (switch caído) | un switch caído no bloquea a los demás |
