# Revisión del código según el docente (iteración 5)

En la revisión del código de la iteración 5 el docente nos marcó seis puntos: apagar antes de encender, `-DskipTests` en la CI, sobrediseño, core con estado, el `ts` del termostato redondeado o ignorado y la zona horaria. Al corregirlos encontramos además problemas propios del tiempo acelerado (vigencia, tick y re-sincronización) y de legibilidad. Este documento registra qué hicimos con cada punto, en qué commit o PR quedó, qué test lo demuestra y qué queda diferido. Todo está integrado en `implementacion5` (último merge: PR #7, `ff9b8be`).

Las reglas del tiempo (relojes, conversión del `ts`, vigencia, tick y re-sincronización) se explican en la sección [Tiempo del control](../arquitectura/4mas1.md#4-tiempo-del-control) del 4+1.

## Observaciones y qué hicimos

Los tests se citan como `Clase.método`. "Equipo" indica un punto que detectamos nosotros al trabajar sobre una observación del docente.

| Observación | Qué hicimos | Tarjeta | Commit / PR | Test que lo demuestra | Estado |
|---|---|---|---|---|---|
| Apagar antes de encender | `CoreAdapter` manda primero los OFF y después los ON, y retiene un ON si el OFF que liberaba potencia no se confirmó. Lo verificamos después del refactor del core | T3 | `c454323` | `CoreAdapterTest.hiloDeSwitches_unSwitchLentoNoDemoraAlQueLlama` (orden `sw-dorm:OFF`, `sw-living:ON`); `ControlCargaTest.cargaLimitante_redistribuye` (el pico de potencia nunca supera la contratada) | Hecho |
| `-DskipTests` en la CI | Un solo `mvn -B verify`: Spotless, todos los tests y JaCoCo `check` (≥ 85 % de líneas y ramas del core). Corre en `implementacion*`, en los PR y en los tags `version*`, y sube los reportes de JaCoCo y surefire. No se dispara en cambios que solo tocan documentación | T1, T1b | `67fe9a9`; `13b8dbe` (PR #6) | La propia CI: falla si falla un test o si la cobertura baja de 85 % | Hecho |
| Sobrediseño y core con estado | El core queda sin estado: el modelo y la función pura `decidir` (más `lecturaValida` y `lecturaVencida`). El estado de la situación lo guarda solo `CoreAdapter`. Se eliminaron `Core.procesar`, `CoreClient` e `ICoreClient`. `Estimulo` queda como modelo y lo procesa `CoreAdapter.procesar` (D1, D2) | T4, T5 | `67fe9a9` | `DecidirTest` (decisión por situación), `CoreAdapterEstimulosTest` (comportamiento ante cada estímulo) | Hecho |
| Camino de una lectura MQTT (equipo) | De 10 pasos a 5, con un solo try/catch (el de `messageArrived`) | T7 | `67fe9a9` | `PerdidaMensajesTest`, `CoreAdapterEstimulosTest` | Hecho |
| Dos banderas de "en ejecución" (equipo) | Una sola, en `CoreAdapter`; `ControlService` le delega. Sin `ObjectProvider` ni `BooleanSupplier` (D6) | T7b | `bb4b1e6` (PR #3) | `CoreAdapterTest.detener_noComanda_iniciar_si`, `ArranqueParadaTest.detenerDejaDeComandar` | Hecho |
| El `ts` se redondea | Parseo exacto con `BigDecimal` (`Instant.ofEpochSecond(seg, nanos)`). El `ts` es el instante de la lectura y una lectura anterior a la última se descarta | T8 | `67fe9a9` | `PayloadTermostatoTest.tsExacto_sinErrorDeRedondeo`, `CoreAdapterEstimulosTest.lecturaFueraDeOrden_seDescarta` | Hecho |
| El `ts` se ignora | El `ts` llega en hora real y siempre se convierte: `fechaHora + (ts − iniciadoEn) × factor`. Se eliminó la propiedad `termostato.ts-tiempo-real`. Si falta o queda en el futuro, se usa `ahora` (D3) | T8b | `5283b55` (PR #7) | `RelojControlTest.conversion_factorDos_mismaHora` (13:00, `ts` 13:05, factor 2 ⇒ 13:10), `RelojControlTest.tsFuturo_usaElReloj`, `RelojControlTest.desfaseDeReloj_noDescartaNiDesordena` | Hecho |
| `ts` más viejo que la vigencia (equipo) | Se descarta y se cuenta en `/diagnostico` (`TS_VENCIDO`), igual que las fuera de orden (`FUERA_DE_ORDEN`) (D8) | T8b | `5283b55` (PR #7) | `RelojControlTest.tsMasViejoQueLaVigencia_seDescarta`, `CoreAdapterTest.lecturasDescartadas_seCuentan` | Hecho |
| Vigencia escalada por el factor (equipo) | La vigencia (600 s) se mide en tiempo de control, sin multiplicar por el factor. `factorTiempo` salió de `Core.decidir` | T8c | `f2bb1e7` (PR #7) | `DecidirTest.vigenciaEnTiempoDeControl_hace601sVencida`, `CoreAdapterEstimulosTest.conTiempoAcelerado_vencePorTiempoDeControl`, `TermostatoCaidoTest.tiempoAcelerado` | Hecho |
| Zona horaria | Zona fija del sitio, `America/Montevideo` (configurable con `CONTROL_ZONA_HORARIA`). Sin `ZoneId.systemDefault()`; una `fechaHora` sin offset es hora de Montevideo (D4) | T9 | `89aba3d` (PR #7) | `ControlCargaTest.franjaPunta` (21:00Z son las 18:00: punta), `ControlCargaTest.dieciochoUtc_noEsPunta`, `ApiEstandarTest.iniciarSinOffset_esHoraDeMontevideo` | Hecho |
| Tick fijo con tiempo acelerado (equipo) | El período del tick es `max(10 ms, tickMs / factor)` y se rearma en cada INICIAR. El corte de la punta se atrasa como mucho un tick de tiempo de control | T12 | `bfd8495` (PR #7) | `ControlServiceTest.periodoEscalado`, `TiempoAceleradoTest.cortaAlEntrarEnPunta`, `TiempoAceleradoTest.restituyeAlSalirDePunta` | Hecho |
| Re-sincronización que revivía lecturas (equipo) | Un INICIAR repetido olvida las lecturas ya vencidas con el reloj anterior (la habitación queda `SIN_LECTURA`) y marca las vigentes con la hora nueva (D7) | D7 | `83dbc1b` (PR #7) | `CoreAdapterEstimulosTest.resincronizar_noReviveLecturasVencidas`, `TermostatoCaidoTest.reiniciarDespuesDeLaVigencia_noReviveLaLectura`, `TermostatoCaidoTest.reiniciarEnseguida_conservaLaLectura` | Hecho |
| Código difícil de leer (equipo) | Loops e `if` en lugar de streams y ternarios largos, aplicado solo en `CoreAdapter`. Falta la guía "Java que usamos" y el resto del engine | T10, T2 | — | — (no cambia el comportamiento: los tests siguen igual) | Parcial (ver Diferido) |
| Documentación de las decisiones sobre el tiempo | Sección "Tiempo del control" en el 4+1: tres relojes, fórmula, `ts`, vigencia, tick, re-sincronización y la tabla letra frente a decisión | T14 | — | — | Hecho |

## Decisiones del equipo

| # | Decisión | Motivo |
|---|---|---|
| D1 | El core queda sin estado: solo el modelo y `decidir` | Sugerencia del docente; elimina el estado duplicado entre core y engine |
| D2 | `Estimulo` queda en el core como modelo y lo procesa `CoreAdapter` | La letra de la iteración 4 pide el "modelo de los estímulos" |
| D3 | El `ts` es el instante de la lectura: llega en hora real y se convierte a la hora del control | Un solo reloj para decidir; regla confirmada con el docente |
| D4 | Zona fija del sitio, `America/Montevideo` | El sitio es de Uruguay (confirmado por el docente); no depende de la zona del contenedor |
| D5 | Una rama por tarjeta, con PR hacia `implementacion5` | Cada cambio se revisa y se verifica en la CI por separado |
| D6 | El estado de ejecución lo administra `CoreAdapter` | La dependencia va en un solo sentido (`ControlService` → `CoreAdapter`), sin ciclos escondidos |
| D7 | Al re-sincronizar, las lecturas vencidas se olvidan y las vigentes se marcan con la hora nueva | Un dato viejo no puede volver a contar como reciente |
| D8 | Una lectura con `ts` más viejo que la vigencia se descarta y se cuenta en `/diagnostico` | Un dato obsoleto no debe sostener la calefacción |

Además, `DETENER` deja los switches como están (el §5.2 no exige apagar) y un reinicio del contenedor arranca `DETENIDO` (`ApiEstandarTest.controlArrancaDetenido`).

## Diferido

| Qué | Motivo |
|---|---|
| T10 y T2: legibilidad en el resto del engine, guía "Java que usamos" y renombres | Tocan muchas líneas de `ControlService` y `CoreAdapter` sin cambiar el comportamiento; preferimos no arriesgar el código integrado antes de la entrega. Pasa a la iteración 6 |
| T13: corrida contra el simulador nuevo, con evidencia | El script `scripts/captura-simulador.sh` y la [guía de la corrida](../tests/corrida-simulador/README.md) están listos (`3ad22bb`); falta correrla contra el simulador. Ahí se confirma si el paso a modo acelerado llega como un INICIAR nuevo |
| T11: revisión final del 4+1 contra el tag y vista física | Se hace sobre el código del tag; la vista física queda para la iteración 6 |
| Conservar la antigüedad de una lectura al re-sincronizar | Hoy una lectura vigente recupera la vigencia completa (D7). Es una mejora posible, no un error: la regla actual está implementada y probada |
| Tests de re-INICIAR con otro factor de tiempo, y hacia atrás con lecturas vencidas y vigentes mezcladas | Hacia adelante con lecturas mezcladas ya lo cubre `CoreAdapterEstimulosTest.resincronizar_noReviveLecturasVencidas`; los dos casos que faltan se agregan con T13 |
| Análisis de riesgos de `DETENER` sin apagar | Queda para la iteración 6, junto con el comportamiento ante un reinicio del contenedor |
