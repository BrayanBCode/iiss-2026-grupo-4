# Grupo 4 — Iteración 5: segunda evaluación

Visto como el estudiante que entrega la versión actualizada de `implementacion5` (zip del 2026-10-28), contra la letra de la iteración 5 y lo que arrastran las iteraciones 3 y 4. Reemplaza a `evaluacion-1-iter5.md`.

**Qué se verificó y qué no.** Leí el código, la configuración y toda la documentación, y comparé archivo por archivo con la versión anterior. **No pude ejecutar `mvn verify`**: el proyecto pide Java 25 y este entorno no llega a Maven Central. Los números de tests y de cobertura que cito salen de los reportes entregados o de contar los métodos de test en el código.

---

## Opinión general

**Hubo un avance importante y bien dirigido.**
- La CI ahora verifica todo y la cobertura se exige en el build.
- El core quedó realmente sin estado, `CoreClient` desapareció y hay una sola bandera de "en ejecución".
- El `ts` se parsea exacto.
- `revision-docente.md` tiene una sección de **relojes y decisiones sobre el tiempo** (letra frente a decisión del equipo) que es de lo mejor de la entrega.

**El problema cambió de forma.** Antes el código estaba bien y el plan de correcciones no se había ejecutado. Ahora el plan se ejecutó a medias, y **la documentación de evidencia quedó describiendo un código que ya no existe**:
- el README, `resultados.md` y `criterio-tests.md` siguen hablando de `CoreTest`, `Core.procesar`, `CoreClientTest`, 116/215 tests y 99,6 %;
- dos observaciones del docente siguen abiertas: la zona horaria, y el `ts`, que se parsea bien pero en la práctica, con el simulador, se sigue ignorando por defecto;
- el tiempo acelerado sigue con los tres defectos que **el propio equipo** dejó documentados.

Lo que hoy se puede defender es el diseño. Lo que no se puede defender es la coherencia entre lo que se afirma y lo que hay.

---

## Lo que mejoró (y conviene mostrar)

| Antes | Ahora |
|---|---|
| CI con `-DskipTests`, sin Spotless ni cobertura | Un solo `mvn -B verify`, artefactos de JaCoCo y surefire, dispara en `implementacion*` y en los tags `version*` |
| Cobertura solo reportada | JaCoCo `check` ≥ 85 % en **líneas y ramas**, que falla el build. Core: 150/150 líneas, 79/84 ramas |
| Core con estado + `CoreClient` | `Core` con campos finales: `decidir` y `lecturaValida`. `Estimulo` queda como modelo en el core y lo procesa `CoreAdapter.procesar` con un `switch` exhaustivo |
| Dos banderas de "en marcha" | Una sola, en `CoreAdapter`, sin ciclo ni `ObjectProvider` (D6/T7b) |
| Lectura MQTT en 10 pasos, dos try/catch | 5 pasos, un solo try/catch, documentado en el 4+1 §2.2 |
| `ts` redondeado y descartado | Parseo exacto con `BigDecimal`; descarte por `ts` anterior al último |
| — | `alRecibirLectura` y `tick` son `synchronized` con INICIAR: una lectura sellada con el reloj viejo no se procesa después de re-sincronizar |
| — | Sección 5 de `revision-docente.md`: tres relojes, la fórmula, ejemplos numéricos, la tabla "letra / decisión / motivo" y las preguntas al docente |

---

## Puntos críticos (lo más probable es que bajen nota)

1. **Observaciones del docente que siguen abiertas.**
   - **Zona horaria (T9, pendiente).** Sigue `ZoneId.systemDefault()` en `ControlService.aplicarInventario` y en `ControlController` (una `fechaHora` sin offset se interpreta en la zona del contenedor, que en Docker es UTC). `CONTROL_ZONA_HORARIA` sigue vacía en el compose y en `application.properties`. La propia tabla dice "Confirmado por el docente: Uruguay"; que esté confirmado y no implementado es peor que no saberlo.
   - **`ts` (T8 parcial, T8b pendiente).** La regla acordada con el docente es "`ts` en hora real, se convierte". Pero `termostato.ts-tiempo-real` vale `false` por defecto: el `ts` se toma como si fuera hora del control.
     - Con el simulador, la `fechaHora` del INICIAR casi nunca coincide con la hora real. Entonces el `ts` queda "en el futuro" o "más viejo que la vigencia", y `instanteDeLectura` lo reemplaza por `ahora`.
     - **En la práctica, el `ts` se sigue ignorando**, que es exactamente lo que había marcado el docente. El equipo lo reconoce ("lo cual es incorrecto"), pero lo entrega igual.

2. **Tiempo acelerado: los tres defectos están diagnosticados pero no corregidos** (T8c, T12, D7):
   - **Vigencia escalada por el factor** (`Core.vencida`): a ×30, 600 s son 5 h simuladas. En toda la prueba acelerada ninguna lectura vence, así que el "termostato que deja de funcionar" **no se detecta** en la prueba final.
   - **Tick fijo de 1 s real:** a ×30 el corte de la punta puede atrasarse hasta 30 s simulados; a ×600, 10 min.
   - **Re-sincronización que revive lecturas** (`sincronizarReloj` sella todas con la hora nueva). Si, como supone el equipo, el paso a acelerado llega como un nuevo INICIAR, esto ocurre **justo** al empezar la prueba acelerada: datos viejos vuelven a contar como recientes.
   - Sigue **sin una corrida contra el simulador** (T13) y con una pregunta clave sin cerrar: ¿el paso a acelerado es un nuevo INICIAR?

3. **La evidencia escrita contradice el código.**
   - **README:** "core 116: `DecidirTest` (35) … `CoreTest` (63) … 99,6 % líneas, 90,8 % ramas", "215 tests". `CoreTest` y `CoreClientTest` ya no existen.
   - **`resultados.md`:** sin cambios, con la tabla de `CoreTest` y `CoreClientTest`.
   - **`criterio-tests.md`:** sigue titulado "Iteración 4" y explica el criterio con `Core.procesar` y `CoreTest`. **El documento que justifica la adecuación de los tests describe un diseño eliminado.**
   - **`spotless-check.txt`:** es la corrida anterior (69 archivos), y desde entonces se borraron y agregaron clases.
   - **`revision-docente.md`** dice "`mvn -B verify` sobre `67fe9a9` todavía no lo corrimos nosotros", mientras que `cobertura/README.md` dice que la cobertura se midió con `mvn clean verify`. No hay enlace a una corrida verde de la CI.
   - **Encabezado de `docker-compose.yml`:** "Iteración 4".

4. **4+1: la vista física se vuelve a postergar a la iteración 6.** La letra de la iteración 5 pide avanzar hacia un diagrama por vista, y el despliegue (dos compose, redes, volumen, puertos) ya está definido: solo falta dibujarlo.

5. **Documentos internos dentro del entregable.**
   - `docs/cambios-excelencia-iter5.md` es una crítica externa de la entrega anterior. Dice, por ejemplo, que "la entrega afirma más de lo que demuestra" y que "arrastra observaciones del docente". Entregarlo dentro del repositorio invita al docente a leerlo como autoevaluación de **esta** versión.
   - `revision-docente.md` registra por nombre de usuario quién hizo un commit de 68 archivos incumpliendo D5. La trazabilidad del proceso Scrumban está bien, pero eso corresponde al tablero o a Jira, no al documento técnico que lee el docente.

## Puntos dudosos o discutibles

6. **El TDD del core quedó más delgado.**
   - El core pasó de unos 116 tests a unos 53 métodos (`DecidirTest` 39, `MayorDeficitPrimeroTest` 9, `ModeloTest` 5). Los 59 tests de estímulos se mudaron a `CoreAdapterTest`/`CoreAdapterEstimulosTest`, en el engine, **donde no se mide ni se exige cobertura**.
   - El 100 % de líneas sobre un core de 150 líneas es fácil de alcanzar.
   - La letra de la iteración 4 pide TDD "sobre el core" para "cómo se comporta a partir de cierta situación inicial ante distintos estímulos": hay que explicar por qué esa parte ahora vive en el engine y cómo se asegura su calidad.
7. **`CoreAdapter` tiene 561 líneas** y hace cinco cosas: estado de la situación, procesamiento de estímulos, estado de ejecución, red de switches (pasadas, OFF antes que ON, retención) y reconciliación. El "sobrediseño" que marcó el docente no desapareció: se concentró en una clase. Es probable que pregunte por la responsabilidad única.
8. **`decidir` sigue recibiendo `factorTiempo`** solo para escalar la vigencia. Cuando se haga T8c, debería salir de la firma: la función pura no tendría por qué saber de aceleración.
9. **Interacción entre las reglas del `ts`.** El descarte por "`ts` anterior al último" convive con el reemplazo por `ahora` cuando el `ts` es futuro o viejo. Una lectura guardada con `ahora` (por un desfase de reloj) puede hacer que las siguientes, con su `ts` real, se descarten como fuera de orden. Falta un test que lo cubra (por ejemplo, desfase de reloj de ±2 s a ×30).
10. **DETENER sigue sin apagar nada**, el reinicio del contenedor sigue sin estar decidido y el MCP opcional sigue sin hacerse. Ninguno de los tres es nuevo, pero la letra de la iteración 5 pregunta explícitamente qué hace el controlador al terminar.
11. **Proceso.** T3 (verificar OFF antes que ON) figura "Pendiente", aunque el test existe y sigue en `CoreAdapterTest` (línea 414): basta marcarlo como hecho. T2 y T10 (legibilidad) están a medias: solo se aplicaron en `CoreAdapter`.

## Lo que está bien resuelto (para defenderlo con seguridad)

- La separación core/engine ahora es real: core puro y determinista, compartible entre hilos.
- CI y umbral de cobertura que hacen cumplir lo que dice el estándar.
- OFF antes que ON con medición del pico de potencia, la reconexión MQTT propia y la reconciliación de switches.
- La sección de tiempo de `revision-docente.md`: tres relojes con un solo trabajo cada uno, la fórmula del `ts` con ejemplos y la tabla letra frente a decisión. Es justo lo que el docente quiere oír en el oral. Debería pasar al 4+1 (T14).

---

## Lo que el docente probablemente pregunte

**Continuidad y evidencia**
1. ¿Qué observaciones les hice y cuáles están cerradas **en este tag**? ¿Por qué la zona horaria sigue con `systemDefault()` si ya les confirmé Uruguay?
2. Muéstrenme la corrida verde de la CI sobre el tag `version5`. ¿Qué pasa si comento un test del core?
3. El README dice 215 tests y `CoreTest` 63. ¿Dónde está `CoreTest`? ¿Cuántos tests hay hoy?
4. `criterio-tests.md` habla de `Core.procesar`. ¿Cuál es el criterio de adecuación **de este** diseño?

**Core sin estado y TDD**
5. ¿Qué gana el diseño con el core sin estado? ¿Qué quedó en el core y qué pasó al engine, y por qué?
6. Si los tests de estímulos están en el engine, ¿siguen siendo TDD del core? ¿Qué cobertura tiene `CoreAdapter`?
7. ¿Por qué `Estimulo` sigue en el core si el core no lo procesa?
8. `CoreAdapter` tiene más de 500 líneas: ¿cuántas responsabilidades tiene? ¿Cómo lo dividirían?
9. ¿Por qué `decidir` recibe `factorTiempo`?

**Tiempo acelerado**
10. ¿Cómo llega el factor en el simulador nuevo? ¿Lo probaron contra él? ¿Qué hacen si el simulador acelera sin mandar un INICIAR?
11. Con factor 30, ¿cuánto tarda en vencer la lectura de un termostato caído, en tiempo real y en simulado? ¿Lo detectarían en la prueba final?
12. Con factor 600, ¿con cuánto atraso cortan al entrar en la punta?
13. Al re-sincronizar, ¿qué pasa con una lectura de hace 3 horas simuladas?
14. Explíquenme `tsControl = fechaHora + (ts − iniciadoEn) × factor` con un ejemplo. ¿Por qué hoy la conversión está apagada por defecto?
15. ¿Qué pasa si el reloj del simulador y el del controlador difieren 2 s y el factor es 30?

**Zona horaria y tarifa**
16. Si INICIAR llega con `2026-10-01T21:00:00Z`, ¿están en punta? ¿Y si llega sin offset?

**Arranque, parada y casos borde**
17. ¿Qué hace el controlador con la calefacción al INICIAR y al DETENER? ¿Por qué no apagan al detener?
18. Si el contenedor se reinicia en plena corrida, ¿qué pasa con los switches que estaban encendidos?
19. El broker cae 2 minutos: ¿qué lecturas se pierden y qué hacen los switches mientras tanto?
20. Un switch no confirma el OFF que liberaba potencia: ¿qué pasa con el ON pendiente y cuánto tiempo lo retienen?
21. Llega una lectura con `ts` futuro y después una con `ts` correcto: ¿se descarta la segunda?

**Calidad**
22. ¿Qué reglas del estándar de codificación verifica una herramienta y cuáles no? ¿Dónde está la evidencia de Spotless sobre este código?
23. 100 % de líneas en el core: ¿eso prueba que los tests son buenos? ¿Qué otra evidencia tienen?

**Arquitectura y proceso**
24. ¿Qué diagrama hay de cada vista del 4+1? ¿Por qué la física sigue pendiente?
25. ¿Cómo trabajaron en Scrumban esta semana? ¿Por qué entró un commit de 68 archivos sin rama propia?
26. (Opcional MCP) Ahora que el core es puro, ¿qué tendrían que tocar para exponer las operaciones por MCP?
