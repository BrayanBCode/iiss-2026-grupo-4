# Planificación de la iteración 6

Última iteración: validar el controlador contra el simulador (operación normal y con fallas) y
dejarlo listo para que otro equipo lo continúe. Se entrega con el tag `version6` en GitHub y el zip
en Moodle. Sigue la [metodología del equipo](metodologia-elegida.md); las tarjetas, sus responsables
y su estado al día están en el Jira (épica Iter 6, `G1234-153`).

## Entregables y tarjetas

| Entregable de la letra | Tarjetas |
|---|---|
| Versión final del componente | T23, T24a–T24e, T31–T37 (y las que salgan de T28), T29 |
| Tests de integración mejorados | T25, T17, T32, T36, T37 (pruebas de estrés) |
| 4+1 completo (al menos un diagrama por vista) | T11 |
| Limitaciones conocidas y roadmap | T21 |
| Documentación para el traspaso (README, decisiones, problemas conocidos) | T26, T27, T2 |
| Validación con el simulador | T13, T28 |
| Tag `version6` y zip | T30 |

## Estado al 2026-11-08

| Tarjeta | Qué entrega | Estado | Rama |
|---|---|---|---|
| T21 · `G1234-152` | Limitaciones conocidas (L1–L18) y hoja de ruta para el equipo que continúe (R1–R15) en [`roadmap.md`](../arquitectura/roadmap.md) | Mergeada (la versión de traspaso entra con T27) | `iter6/n4-roadmap-limitaciones` |
| T11 · `G1234-137` | Vista física del 4+1 (§6); cobertura, spotless, `resultados.md` y `criterio-tests.md` al día | Mergeada | `iter6/t11-vista-fisica` |
| T22 · `G1234-154` | Reunión: decisiones D9–D12 (este documento) | Hecha: se adoptaron las propuestas; D9, D10 y D12 vigentes y D11 confirmada en [`decisiones.md`](../arquitectura/decisiones.md) | `iter6/t22-planning` |
| T23 · `G1234-155` | Relevamiento de la corrida del simulador del 2026-10-28, con extracto del log | Mergeada | `iter6/t23-problemas-simulador` |
| T24a · `G1234-163` | Aviso al arrancar si el entorno no puede alcanzar la `baseUrl` (problema P2 de la corrida) | Mergeada | `iter6/t24a-aviso-baseurl` |
| T24b · `G1234-164` | El `ts` del termostato en hora real o en hora del control (corrida del 2026-11-04: se descartaban todas las lecturas) | Mergeada (#22) | `iter6/t24b-ts-escala` |
| T24c · `G1234-165` | Timeout de respuesta de los switches a 10 s | Mergeada (#25) | `iter6/t24c-timeout-switch` |
| T24d · `G1234-166` | Respuesta del switch leída como texto, con el detalle si no es JSON | Mergeada (#23) | `iter6/t24d-respuesta-switch` |
| T24e · `G1234-167` | Corte de la punta anticipado (E11 del simulador oficial) y la corrida 9 de 9 | Mergeada (#28) | `iter6/t24e-anticipar-punta` |
| T31 · `G1234-168` | Respuestas de switch fuera de contrato toleradas: `estado` sin mayúsculas, comando 2xx con cuerpo raro (F07) | Mergeada (#29) | `iter6/t31-switch-fuera-de-contrato` |
| T32 · `G1234-169` | Crecer de 5 a 20 habitaciones y achicar en caliente (E15/E16) y cobertura de los escenarios de la clase | Mergeada (#30) | `iter6/t32-sitio-grande` |
| T33 · `G1234-170` | Payload del termostato con `tC` o `ts` como texto y `ts` en milisegundos (F04) | Mergeada (#31) | `iter6/t33-termostato-fuera-de-contrato` |
| T34 · `G1234-171` | Una habitación cuyo switch no prende cede su potencia (F09, A17) | Mergeada (#32) | `iter6/t34-switch-no-prende` |
| T35 · `G1234-172` | Cliente MQTT con versión 3.1.1 fija e identificador de 23 caracteres como máximo (visto en F01 de `ecowarm-run`) | Mergeada (#33) | `iter6/t35-mqtt-client-id` |
| T36 · `G1234-173` | Reconciliación de switches escalada con el factor: un switch prendido a mano a ×60 vuelve a OFF en 1 s real (antes, hasta 15 min de control) | Mergeada (#34) | `iter6/t36-reconciliacion-escalada` |
| T37 · `G1234-174` | Un switch colgado no traba a los demás (A19): timeout en tiempo de control, sanos primero y corte de la pasada; `EstresTest` (switches colgados, ×600, ×3600, 50 habitaciones, fallas combinadas) | Mergeada (#35) | `iter6/t37-switch-colgado` |
| T38 | Un `PUT /sitio` que saca una habitación con el switch encendido: no se lo comanda (contrato del simulador), WARN y `switchesFueraDelSitio` en `/diagnostico` (A20, L20) | PR abierto | `iter6/t38-t40-casos-extremos` |
| T39 | Switches lentos que no llegan al timeout: la punta se corta antes según lo que tardan, y una reconciliación en curso no demora los OFF (A21) | PR abierto | `iter6/t38-t40-casos-extremos` |
| T40 | Un switch que acepta el comando y no lo aplica: tras 3 correcciones se lo deja de comandar un rato o cede su potencia (A22) | PR abierto | `iter6/t38-t40-casos-extremos` |
| T25 · `G1234-157` | `EscenariosSimuladorTest` (3 tests como corre el simulador) y mapa escenario → test | Mergeada | `iter6/t25-tests-escenarios` |
| T17 · `G1234-146` | Tests de re-INICIAR con otro factor y hacia atrás | Mergeada | `revision/t17-tests-reinicio` |
| T2 · `G1234-129` | Guía "Java que usamos" | Cerrada en el Jira; la guía no está en el repo | — |
| T26 · `G1234-158` | [`decisiones.md`](../arquitectura/decisiones.md) (A1–A18 de arquitectura, D1–D12 del equipo) | Mergeada (#24) | `iter6/t26-decisiones` |
| T27 · `G1234-159` | README final, [guía de traspaso](../traspaso.md) y roadmap como traspaso | Mergeada (#27) | `iter6/t27-traspaso` |
| T29 · `G1234-161` | D11: contar en `/diagnostico` y loguear las temperaturas fuera de rango; documentación al día (D11, L18, R4) | Mergeada (#26); D11 confirmada en T22 | `iter6/t29-fuera-de-rango` |
| T13 · `G1234-149` | Corrida contra el simulador con evidencia | Hecha con el simulador oficial: 9 de 9, también [con todo integrado](../tests/corrida-simulador/2026-11-08-implementacion6/README.md) (2026-11-08). No hay corrida en el laboratorio (roadmap L19) | `iter6/t13-corrida-implementacion6` |
| T28 · `G1234-160` | Ensayo de aceptación con los escenarios nuevos | Cerrada sin evidencia en el repo: no se corrieron escenarios nuevos del docente (roadmap L19) | `iter6/t28-aceptacion` |
| T30 · `G1234-162` | Cierre: evidencia final, limpieza de `docs/`, tag `version6` y zip | Hecha: conteos, cobertura y spotless regenerados; `docs/` se entrega completo; tag `version6` sobre `master` | `iter6/t30-cierre` |

## El camino

Vamos en cuatro etapas; el orden crítico es T22 → T13 → T28 → T30. Lo que no depende de las
decisiones avanza en paralelo.

1. **Decidir e integrar.** Reunión T22 (D9–D12). En paralelo, revisar y mergear los PR hacia
   `implementacion6` en este orden: T24b → T24c → T24d → T24e → T31 → T32 → T33 → T34 → T35 → T36 → T37 (ya están mergeadas
   T21, T11, T23, T24a, T25, T17, T26 y T27).
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

## Decisiones de la reunión (T22)

Son cuatro decisiones que afectan a varias tarjetas. En la reunión T22 el equipo adoptó las
propuestas de esta tabla. Están registradas en
[`decisiones.md`](../arquitectura/decisiones.md): D9, D10 y D12 quedaron vigentes y D11 confirmada.
Lo configurable de D9 queda en el roadmap (R1).

| Id | Qué hay que decidir | Hoy | Propuesta | A qué afecta |
|---|---|---|---|---|
| D9 | Qué hace DETENER con los switches | Los deja como están (el §5.2 no exige apagar) | Configurable, por defecto como hoy; el default lo valida el docente | T29, roadmap L1/R1 |
| D10 | Qué pasa al reiniciar el contenedor | Arranca DETENIDO hasta el próximo INICIAR | Dejarlo así y documentarlo como limitación | T29, roadmap L2/R2 |
| D11 | Mejoras pendientes de T20 | Mergeado con T29 (#26) | Contar en `/diagnostico` las lecturas fuera de rango (ayuda en la prueba); el rango razonable al roadmap (R4) | T29 |
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
  (roadmap R6).
- **T15**, versiones en el pom padre: en pausa.
- **PIT, Actuator, historial en base de datos, TLS y roles:** pasos de mediano y largo plazo del
  roadmap.

## Lo próximo

- [x] Reunión T22, con una postura sobre D9–D12 (se adoptaron las propuestas)
- [x] Revisar los PR de T21, T11, T23, T24a y T25 (y T17)
- [x] Revisar y mergear los PR de T24b, T24c, T24d, T24e, T31, T32, T33, T34, T35, T36 y T37, en ese orden (T26 y T27 ya están)
- [x] Pasar a "to test" en el Jira las tarjetas con PR abierto y a "Listo" las mergeadas
- [x] Correr el simulador oficial `ecowarm-run` (9 de 9, 2026-11-06; con todo integrado, 9 de 9, 2026-11-08)
- [ ] Coordinar con el docente la corrida T13 en el laboratorio y los escenarios nuevos (T28): no se hizo, queda en el roadmap (L19)
- [x] Cierre T30: tag `version6` y zip
