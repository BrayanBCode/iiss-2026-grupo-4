# Planificación de la iteración 6

Última iteración: validar el controlador contra el simulador (operación normal y con fallas) y
dejarlo listo para que otro equipo lo continúe. Se entrega con el tag `version6` en GitHub y el zip
en Moodle. Sigue la [metodología del equipo](metodologia-elegida.md); las tarjetas, sus responsables
y su estado al día están en el Jira (épica Iter 6, `G1234-153`).

## Entregables y tarjetas

| Entregable de la letra | Tarjetas |
|---|---|
| Versión final del componente | T23, T24a (y las T24b… que salgan de T28), T29 |
| Tests de integración mejorados | T25, T17 |
| 4+1 completo (al menos un diagrama por vista) | T11 |
| Limitaciones conocidas y roadmap | T21 |
| Documentación para el traspaso (README, decisiones, problemas conocidos) | T26, T27, T2 |
| Validación con el simulador | T13, T28 |
| Tag `version6` y zip | T30 |

## Estado al 2026-11-04

| Tarjeta | Qué entrega | Estado | Rama |
|---|---|---|---|
| T21 · `G1234-152` | Limitaciones conocidas (L1–L17) y roadmap (R1–R15) en [`roadmap.md`](../arquitectura/roadmap.md) | Hecha, falta PR | `iter6/n4-roadmap-limitaciones` |
| T11 · `G1234-137` | Vista física del 4+1 (§6); cobertura, spotless, `resultados.md` y `criterio-tests.md` al día | Hecha, falta PR | `iter6/t11-vista-fisica` |
| T23 · `G1234-155` | Relevamiento de la corrida del simulador del 2026-10-28, con extracto del log | Hecha, falta PR | `iter6/t23-problemas-simulador` |
| T24a · `G1234-163` | Aviso al arrancar si el entorno no puede alcanzar la `baseUrl` (problema P2 de la corrida) | Hecha, falta PR | `iter6/t24a-aviso-baseurl` |
| T25 · `G1234-157` | `EscenariosSimuladorTest` (3 tests como corre el simulador) y mapa escenario → test | Hecha, falta PR | `iter6/t25-tests-escenarios` |
| T17 · `G1234-146` | Tests de re-INICIAR con otro factor y hacia atrás | En revisión | `revision/t17-tests-reinicio` |
| T2 · `G1234-129` | Guía "Java que usamos" | En curso | — |
| T22 · `G1234-154` | Reunión: decisiones D9–D12 (este documento) | En curso | `iter6/t22-planning` |
| T26 · `G1234-158` | [`decisiones.md`](../arquitectura/decisiones.md) (A1–A15 de arquitectura, D1–D12 del equipo) | Hecha, falta PR | `iter6/t26-decisiones` |
| T27 · `G1234-159` | README final y [guía de traspaso](../traspaso.md) | Hecha, falta PR | `iter6/t27-traspaso` |
| T29 · `G1234-161` | Implementar D9/D10, solo si cambian el comportamiento | Pendiente | `iter6/t29-<tema>` |
| T13 · `G1234-149` | Corrida contra el simulador con evidencia | Pendiente | — |
| T28 · `G1234-160` | Ensayo de aceptación con los escenarios nuevos | Pendiente | `iter6/t28-aceptacion` |
| T30 · `G1234-162` | Cierre: evidencia final, limpieza de `docs/`, tag `version6` y zip | Pendiente | `iter6/t30-cierre` |

## El camino

Vamos en cuatro etapas; el orden crítico es T22 → T13 → T28 → T30. Lo que no depende de las
decisiones avanza en paralelo.

1. **Decidir e integrar.** Reunión T22 (D9–D12). En paralelo, revisar y mergear los PR hacia
   `implementacion6` en este orden: T21 → T11 → T23 → T24a → T25, y T17.
2. **Completar el producto y la documentación.** T29 si las decisiones cambian el comportamiento.
   T26 (decisiones) depende de T22. T27 (traspaso) depende de T21 y T26.
3. **Validar con el simulador.** T13: la corrida con evidencia, sobre todo switch caído, lecturas
   atrasadas y termostato caído, que no se vieron el 2026-10-28. Desde acá se congela (D12): solo
   entran correcciones. Después, T28: el ensayo con los escenarios nuevos del docente; cada problema
   es una tarjeta T24b, T24c…
4. **Cerrar.** T30: `mvn clean verify` sobre el commit final, cobertura, spotless y conteos de tests
   regenerados, sacar de `docs/` los documentos internos, tag `version6` y zip.

La corrida del 2026-10-28 ya mostró que el control funciona en los 7 escenarios que corrió el
simulador; los problemas fueron de configuración (detalle en
[`corrida-simulador/2026-10-28`](../tests/corrida-simulador/2026-10-28/README.md)).

## Decisiones para la reunión (T22)

Son cuatro decisiones que afectan a varias tarjetas. Hay una propuesta para cada una, pero las toma
el equipo; se registran en [`revision-docente.md`](revision-docente.md) y después en
`decisiones.md` (T26).

| Id | Qué hay que decidir | Hoy | Propuesta | A qué afecta |
|---|---|---|---|---|
| D9 | Qué hace DETENER con los switches | Los deja como están (el §5.2 no exige apagar) | Configurable, por defecto como hoy; el default lo valida el docente | T29, roadmap L1/R1 |
| D10 | Qué pasa al reiniciar el contenedor | Arranca DETENIDO hasta el próximo INICIAR | Dejarlo así y documentarlo como limitación | T29, roadmap L2/R2 |
| D11 | Mejoras pendientes de T20 | No están | Contar en `/diagnostico` las lecturas fuera de rango (ayuda en la prueba); el resto al roadmap | T29 |
| D12 | Congelamiento antes de la aceptación | — | Después de T13 solo entran correcciones | T13, T28 |

## Cómo trabajamos

- **Ramas:** una por tarjeta, `iter6/<id>-<tema>`, desde `implementacion6`; PR hacia
  `implementacion6`. El commit sigue el formato `módulo: descripción`.
- **Jira:** Backlog → Tareas por hacer (en curso, con responsable) → to test (PR abierto) → Listo
  (mergeado). Como máximo 2 tarjetas en curso en todo el equipo, y 1 sola entre las que tocan los
  mismos archivos.
- **Hecho:** `mvn verify` en verde, PR revisado por otro integrante, documentación afectada
  actualizada.
- **Maven:** correr siempre desde la raíz o con `-am`. `mvn test -pl modules/engine` solo usa un
  `core` viejo de `~/.m2` y todos los tests dan 500.
- **Contra el simulador:** `ENTORNO_API_KEY` = la clave del simulador (sin ella, el registro da 401)
  y `CONTROLADOR_BASE_URL` = la IP de tu máquina en esa red (con `localhost` el simulador no te
  alcanza; desde T24a el controlador lo avisa al arrancar).
- **Con `ecowarm-check`:** si el controlador corre en Docker, levantar el checker con
  `--anunciar host.docker.internal`; si el puerto 1883 está ocupado, `--broker 1884`.
- **Conteos de tests:** `resultados.md` y el árbol del 4+1 §5 se regeneran una sola vez, en T30,
  para no generar conflictos entre ramas.

## Fuera de alcance

No entra en esta iteración; queda en el [roadmap](../arquitectura/roadmap.md) para el equipo que
siga.

- **T19 y T18**, separar el despachador de switches y unificar el mapa de lecturas de `CoreAdapter`:
  refactorizar la clase central justo antes de la aceptación es riesgo sin ganancia para la prueba
  (roadmap R4).
- **T15**, versiones en el pom padre: en pausa.
- **PIT, Actuator, historial en base de datos, TLS y roles:** pasos de mediano y largo plazo del
  roadmap.

## Lo próximo

- [ ] Reunión T22, con una postura sobre D9–D12
- [ ] Revisar los PR de T21, T11, T23, T24a y T25 (y T17), en ese orden
- [ ] Pasar a "to test" en el Jira las tarjetas con PR abierto
- [ ] Tomar T26 y T27 cuando T22 esté cerrada (pull, sin asignar)
- [ ] Coordinar con el docente la fecha de la corrida T13 contra el simulador
