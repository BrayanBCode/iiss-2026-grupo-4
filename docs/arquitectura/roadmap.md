# Limitaciones conocidas y roadmap

Entregable de la iteración 6. Es para el equipo que continúe el desarrollo: qué no hace hoy el
controlador, qué riesgos asumimos a sabiendas y por dónde seguir. Las decisiones que explican cada
limitación están en [`decisiones.md`](decisiones.md) (A1–A18, D1–D12) y en el
[4+1 §4.6](4mas1.md#46-qué-dice-la-letra-y-qué-decidimos).

Cada limitación tiene un id (`L…`) que el roadmap usa para decir qué resuelve cada paso.

> **Actualizado después de la entrega** (rama `post-entrega/doc-aceptacion`, posterior al tag
> `version6`): incorpora lo que mostró la prueba de aceptación en el laboratorio del 2026-11-09
> ([corrida](../tests/corrida-simulador/2026-11-09-laboratorio/README.md)). Cambian L1, L15, L19 y
> L20; se agregan L21, L22, R16 y R17.

---

## 1. Limitaciones conocidas

### 1.1 Comportamiento del control

| Id | Limitación | Impacto | Qué hay hoy |
|---|---|---|---|
| L1 | **DETENER deja los switches como están.** El §5.2 no exige apagar, y decidimos no hacerlo. | Si el control se detiene con habitaciones en ON, quedan prendidas sin supervisión: no se corta la punta ni se respeta la contratada si alguien cambia algo a mano. | Documentado como decisión. El API del controlador no tiene un comando para apagar todo: hay que comandar cada switch directamente (§3). En la aceptación del 2026-11-09 pasó de verdad: el simulador mandó DETENER y enseguida un sitio más chico, y 19 switches que estaban en ON quedaron así (18 a las 19:35, al pasar de 50 habitaciones a 5, y 1 a las 19:58). El simulador no lo penaliza porque reinicia los switches entre escenarios; en una casa serían calefactores prendidos sin supervisión. |
| L2 | **Un reinicio del contenedor arranca DETENIDO.** No se guarda si el control estaba en ejecución, ni el reloj. | Tras un corte de luz o un reinicio, nadie controla hasta que el entorno mande un INICIAR. Los switches quedan como estaban (ver L1). | El inventario sí se recupera del archivo (`RepositorioInventario`). `ApiEstandarTest.controlArrancaDetenido` fija el comportamiento. |
| L3 | **Al re-sincronizar, una lectura vigente recupera la vigencia completa** (D7). Una de 9:59 min vuelve a valer 10 min. | Con re-INICIAR seguidos, un termostato caído tarda más en detectarse. | Las lecturas ya vencidas sí se olvidan (D7). Conservar la antigüedad es la alternativa (4+1 §4.7). |
| L4 | **El modo acelerado solo se conoce por el `factorTiempo` del INICIAR.** | Un entorno que acelerara por otro canal dejaría al reloj del control con el factor viejo: tarifa y vencimientos mal calculados. | Es el único canal del contrato (§5.2), y es el que usa el simulador: en la [corrida del 2026-10-28](../tests/corrida-simulador/2026-10-28/README.md) cada escenario llegó como INICIAR con factor 60. |
| L5 | **Los relojes del simulador y del controlador se suponen sincronizados.** Un `ts` en hora real se convierte con la hora real de esta máquina (uno que ya viene en hora del control se usa tal cual, D3). | Un desfase se multiplica por el factor: 2 s a ×30 es 1 min de control. Con desfases grandes, lecturas descartadas como `TS_VENCIDO` o `FUERA_DE_ORDEN`. | En Docker sobre el mismo host comparten reloj. ±2 s a ×30 no descarta nada (`RelojControlTest`). Los descartes se ven en `/diagnostico`. |
| L6 | **El corte de la punta se adelanta un tiempo fijo** (1 min de tiempo de control) para compensar el tick y la latencia de los comandos. | A factores muy altos (×600 o más) el atraso de un tick más los comandos puede superar ese minuto y algún calefactor quedaría encendido unos segundos en punta. A ×1 se calefacciona un minuto menos antes de cada punta. | Tick escalado con el factor (T12) y corte anticipado configurable con `CONTROL_ANTICIPACION_PUNTA_SEGUNDOS` (A16). Con ×60, el simulador oficial no registra consumo en punta (E11). |
| L7 | **Un solo sitio y una sola franja punta**, con zona fija `America/Montevideo`. | No sirve para otro país ni para tarifas con varias franjas o feriados. | La zona se puede cambiar con `CONTROL_ZONA_HORARIA`; la franja aplica a días hábiles (`HABILES`) o a todos (`TODOS`). |
| L8 | **Criterio de prioridad único**: mayor déficit primero. | No se puede priorizar por habitación (por ejemplo, el dormitorio de noche). | `CriterioPrioridad` es una interfaz: se puede agregar otro criterio sin tocar `decidir`. |
| L18 | **El rango válido de temperatura es el físico del sensor** (-40 a 85 °C, extremos incluidos), no uno razonable para interiores. | Un valor extremo pero dentro del rango (-40 °C en un living) se toma como real y puede hacer calefaccionar de más. | Lo que cae fuera del rango se descarta, se conserva la última lectura buena hasta que vence, y se cuenta en `/diagnostico` (`FUERA_DE_RANGO`) con una línea en el log (D11, T29; `PerdidaMensajesTest.lecturaFueraDeRango`). |

### 1.2 Comunicación y fallas

| Id | Limitación | Impacto | Qué hay hoy |
|---|---|---|---|
| L9 | **Las llamadas a los switches son de a una.** Mientras se espera a un switch colgado no se atiende a ningún otro. | Un switch sano puede esperar una llamada en curso a uno colgado: con factor 1, hasta 12 s (2 s de conexión + 10 s de respuesta); a ×60, 0,5 s reales (30 s de control). Un switch lento pero vivo que a ×60 tarda más de 0,5 s se toma como caído. | Timeouts en tiempo de control, sanos primero en cada pasada y corte de la pasada si cambia una decisión (A19, T37): con 3 switches colgados cruzando las 17:00, los sanos se apagan a tiempo (`EstresTest`). Al switch que falló no se le reintenta por 5 s. Con switches lentos que no llegan al timeout, la punta se corta antes según lo que tardan (A21, T39). Se decidió no paralelizar (4+1 §3). |
| L10 | **MQTT con sesión limpia y QoS 1.** Lo publicado mientras el controlador estaba desconectado se pierde. | Tras una caída del broker, la decisión espera a la primera lectura nueva de cada habitación. | Reconexión cada 5 s con re-suscripción (`FallaBrokerMqttTest`). Si la caída supera la vigencia, la habitación se apaga por `LECTURA_VENCIDA`, que es lo seguro. |
| L11 | **Sin TLS ni usuario en MQTT; HTTP sin TLS.** La `X-API-Key` viaja en texto plano. | Solo apto para una red de confianza (la del laboratorio o la de la casa). | Una clave por dirección (controlador, entorno, switches), configurables por variables de entorno. |
| L12 | **Una sola clave de API** para todo el API del controlador, sin roles. | Quien puede consultar también puede DETENER o cambiar el sitio. | `ApiKeyFilter` exige la clave en todas las operaciones, como pide el estándar v2. |
| L20 | **El switch de una habitación que sale del sitio no se apaga.** Ya no es del sitio vigente, y el estándar solo comanda switches del sitio. | Si salió encendido, el calefactor queda prendido sin control y consume potencia que el controlador ya no cuenta. | WARN en el log y `switchesFueraDelSitio` en `/diagnostico` para apagarlo a mano (A20, T38). Si vuelve al sitio, se lo controla de nuevo. En la aceptación del 2026-11-09 pasó con el control en ejecución: a las 19:48 llegó un sitio de 5 habitaciones en lugar de 20 y 9 switches que el controlador creía en ON dejaron de comandarse. "Creía": el aviso sale si el último estado conocido era ON, o si no se sabía y la última decisión era ON; no se confirma con el switch. |
| L21 | **La anticipación ampliada de la punta (A21) puede ser grande y oscila.** Cuenta todos los switches del sitio, por lo que tarda la llamada más lenta reciente, por el factor y por 1,5. | En la aceptación, con 50 switches de 200 a 430 ms en la red del laboratorio, llegó a 1000–1800 s de control (el tope de 30 min dos veces). A ×60 son 17 a 30 s reales; a ×1, con las mismas demoras, serían unos 80 s (los 60 configurados más 22). Si ese escenario hubiera cruzado las 17:00, se habría dejado de calefaccionar hasta 30 min de control antes. Además sube y baja en segundos con la latencia: con 5 habitaciones pasó por 136, 76, 149, 82, 157 y 96 s en 26 s reales. | En la aceptación el escenario de 50 habitaciones no cruzó la punta; el que sí (5 habitaciones, INICIAR 16:00) cortó a las 16:58:53 de control, unos 67 s antes. Contar solo los encendidos oscilaba peor y cortaba tarde (17:04, A21). Ajustarla es R17. |

### 1.3 Operación y código

| Id | Limitación | Impacto | Qué hay hoy |
|---|---|---|---|
| L13 | **Persistencia en un archivo JSON** (`./data/inventario.json`). Sin historial de lecturas ni de decisiones. | No hay datos para analizar consumo ni para auditar por qué se prendió algo hace una semana. | El log en INFO deja sitio aplicado, decisiones, descartes y un resumen cada 30 s (4+1 §3.2). `/diagnostico` muestra el estado actual. |
| L14 | **Sin métricas ni health check estándar** (Actuator, Prometheus). | El monitoreo es leyendo el log o consultando `/diagnostico`. | `/diagnostico` informa control, MQTT, registro, decisión y motivo por habitación y lecturas descartadas. |
| L15 | **`CoreAdapter` concentra estado, decisión y red** (1031 líneas en `version6`, después de T37–T40). | Es la clase más difícil de cambiar; un error ahí afecta a todo el control. | Tests unitarios por estímulo y de switches, más los de integración. Separarla es el paso R6. |
| L16 | **La cobertura solo se exige en el core** (JaCoCo ≥ 85 %). En el engine no se mide. | Un cambio en el engine puede quedar sin tests sin que la CI lo note. | El engine tiene tests unitarios y 11 clases de integración con broker y switches reales en el proceso. |
| L17 | **Sin tests de mutación (PIT).** | La cobertura alta del core no garantiza que los tests detecten errores. | Diferido por decisión del equipo. |
| L19 | ~~La validación con el simulador se hizo en la misma máquina.~~ **Resuelta después del tag:** la prueba de aceptación en la red del laboratorio, con los escenarios nuevos del docente, dio **10 de 10** (2026-11-09). | Lo que la limitación anticipaba se vio: la latencia de la red agrandó la anticipación de la punta (L21). Las direcciones no dieron problemas (las variables del traspaso). | [Corrida del laboratorio](../tests/corrida-simulador/2026-11-09-laboratorio/README.md): las 35 corridas del log, una por una. El reporte del simulador no se guardó. Antes: `ecowarm-run` 9 de 9, también en Docker ([corrida](../tests/corrida-simulador/2026-11-09-docker/README.md)), y `EstresTest`. |
| L22 | **No se puede saber qué versión está corriendo.** El arranque no loguea el commit y `/diagnostico` no lo informa. | En la aceptación, las primeras 8 corridas (18:46–19:12) usaron una imagen anterior a la versión 6, sin T24b: 7032 lecturas descartadas como `TS_VENCIDO` y ninguna habitación prendida. Se notó por el log y se reconstruyó con `--build`; desde las 19:13 corrió la versión 6. Nada en el log lo probaba. | La guía de traspaso advierte que `--force-recreate` no recompila. Mostrar la versión es R16. |

---

## 2. Hoja de ruta para el equipo que continúe

Esto no es un plan nuestro: es lo que haríamos en su lugar si tuviéramos que seguir con el
controlador, en el orden que conviene y con lo que aprendimos al construirlo. Cada paso dice qué
limitación resuelve, por dónde empezar en el código y con qué tener cuidado. Los nombres de archivos
y tests son los de esta versión; cómo levantar el proyecto y hacer un cambio está en la
[guía de traspaso](../traspaso.md).

### Primero: lo que reduce el riesgo para el usuario

Son cambios chicos sobre un comportamiento que hoy está probado. Conviene hacerlos antes de tocar la
estructura. Después de la aceptación, R1 y R16 son los más urgentes: los dos problemas que se vieron
en el laboratorio (calefactores que quedan prendidos y una imagen vieja que nadie podía identificar)
se resuelven con ellos.

| Paso | Qué | Resuelve | Por dónde empezar | Cuidado con |
|---|---|---|---|---|
| R1 | **Apagar al DETENER, configurable** (`control.al-detener=DEJAR\|APAGAR`, por defecto como hoy) | L1 | `ControlService.detener` y `CoreAdapter`: con el control ya detenido, encolar en el hilo de switches los OFF de todas las habitaciones; si llega un INICIAR mientras tanto, el apagado se corta. Tests en `ArranqueParadaTest` | El §5.2 no lo exige: el default lo decide quien defina el producto. Un OFF que falla no puede dejar al control "detenido pero comandando". Con `APAGAR`, el caso de L20 que viene después de un DETENER desaparece; el que pasa en ejecución sigue dependiendo del contrato (E16) |
| R16 | **Mostrar la versión que corre**: el commit y la versión del build en el log de arranque y en `/diagnostico` | L22 | `spring-boot-maven-plugin` con el goal `build-info` (y el commit con `git-commit-id-maven-plugin`) en `modules/engine/pom.xml`; `BuildProperties` en `DiagnosticoController` | En la imagen Docker, el build corre sin `.git` (`modules/engine/Dockerfile`): pasar el commit como argumento del build o copiar el `.git` en la etapa de compilación |
| R2 | **Retomar la ejecución tras un reinicio**: guardar `fechaHora`, `factorTiempo` e `iniciadoEn` junto al inventario y recalcular el reloj al arrancar | L2 | `RepositorioInventario` y `RegistroEntorno.arrancar`. Hoy `ApiEstandarTest.controlArrancaDetenido` fija lo contrario | Qué hacer si el entorno, al registrarse de nuevo, manda otro INICIAR (D7 ya cubre la re-sincronización) |
| R3 | **Conservar la antigüedad de una lectura al re-sincronizar**: `nuevo − (ahoraViejo − instante)` en lugar de la hora nueva | L3 | `CoreAdapter.sincronizarReloj` y `CoreAdapterEstimulosTest` (grupo del reloj) | Que una lectura re-sellada no quede "en el futuro" si el reloj nuevo va hacia atrás |
| R4 | **Un rango "razonable" para interiores**, más angosto que el del sensor y configurable (contar y loguear lo que queda afuera ya está hecho: D11) | L18 | `ParametrosControl` y `Core.lecturaValida` en el core; `application.properties` | Que un frío real (una ventana abierta en invierno) no se tome como sensor roto: conviene que el valor fuera del rango razonable se cuente pero igual se use, o que se use solo si se repite |

### Después: lo que hace el código más fácil de cambiar

Antes de agregar funcionalidad grande. Los tests de integración existentes son la red de seguridad:
tienen que seguir en verde sin cambios.

| Paso | Qué | Resuelve | Por dónde empezar | Cuidado con |
|---|---|---|---|---|
| R5 | **Medir y exigir cobertura en el engine** (JaCoCo con umbral en `adaptador` y `control`) | L16 | `modules/engine/pom.xml`, copiando la configuración de `modules/core/pom.xml` | Medir primero y fijar el umbral sobre lo que ya hay, para no frenar el trabajo |
| R6 | **Separar el envío y la consulta a los switches de `CoreAdapter`**, y unir los dos mapas de lecturas en uno | L15 | `CoreAdapter` (unas 1000 líneas): estado y decisión por un lado, red por otro | Mantener el orden OFF antes que ON y la retención del ON (4+1 §2.5): lo prueban `FallaSwitchTest` y `ControlCargaTest` |
| R7 | **Pasadas a los switches en paralelo** (una tarea por switch, o un cliente HTTP asíncrono, con el mismo control de potencia) | L9 | Más fácil después de R6. Quitaría el atraso de una llamada que A19 todavía deja, y el piso de 0,5 s del timeout a factores altos | Que dos tareas no prendan a la vez dos habitaciones que juntas superan la contratada: un ON en vuelo tiene que contar como encendido, igual que hoy un switch sin confirmar OFF (A6). `EstresTest` y `picoDePotencia` sirven de red |
| R8 | **Tests de mutación (PIT) sobre el core** | L17 | `modules/core/pom.xml` | El core es Java puro y rápido: es donde PIT rinde más |
| R17 | **Ajustar la anticipación ampliada**: contar los switches que de verdad van a recibir un OFF (los que no confirmaron OFF), con un margen menor, y que la demora baje más despacio que hoy (por tiempo y no por llamada) | L21 | `CoreAdapter.anticipacionPunta` y `registrarDemora`; `EstresTest.veinteSwitchesLentos_cruzandoLaPunta` y `DecidirTest.anticipacionAmpliada_cortaAntesYNuncaMenos` | No volver al error que A21 corrigió: si la anticipación se achica a medida que llegan los OFF, las ya apagadas vuelven a prender antes de la punta (cortaba a las 17:04). Con R7 la anticipación necesaria baja sola |

### Cuando haga falta: lo que amplía el producto

| Paso | Qué | Resuelve | Por dónde empezar | Cuidado con |
|---|---|---|---|---|
| R9 | **Health check y métricas** (Actuator): MQTT, registro, descartes, comandos fallidos | L14 | `DiagnosticoController` ya junta esos datos | No exponer el endpoint sin la clave de API |
| R10 | **Aviso de desfase de reloj**: comparar el `ts` con la hora de llegada y mostrarlo en `/diagnostico` | L5 | `RelojControl.instanteDeLectura` | Con tiempo acelerado el desfase se multiplica por el factor |
| R11 | **Historial de lecturas y decisiones** en una base de datos | L13 | El inventario puede migrar a la misma base | Habilita reportes de consumo, que hoy no hay |
| R12 | **Tarifas más ricas**: varias franjas, feriados, zona por sitio | L7 | `Sitio` y `FranjaHoraria` en el core, y su validación en `ValidadorInventario` | Es un cambio del inventario: hay que acordarlo en el estándar de interoperabilidad |
| R13 | **Otros criterios de prioridad** elegibles en el inventario | L8 | Otra implementación de `CriterioPrioridad` | Que siga siendo determinista (desempate por id) |
| R14 | **TLS y roles en el API, credenciales en el broker** | L11, L12 | `ApiKeyFilter`, `LecturaMqttListener`, `ClienteSwitchRest` | Cambia el contrato con el simulador y los dispositivos |
| R15 | **Varios sitios por controlador** | L7 | Todo el estado del control es de un solo sitio | Es el cambio más grande: conviene después de R6 |

### Dependencias entre pasos

```mermaid
flowchart LR
    R1[R1 apagar al DETENER] --> R2[R2 retomar tras reinicio]
    R5[R5 cobertura del engine] --> R6[R6 separar switches]
    R6 --> R7[R7 switches en paralelo]
    R6 --> R15[R15 varios sitios]
    R7 --> R17[R17 anticipación ajustada]
    R9[R9 métricas] --> R10[R10 desfase de reloj]
    R11[R11 historial] --> R12[R12 tarifas]
```

---

## 3. Lo que queda abierto para quien siga

- **Qué hace DETENER con los switches (R1).** Hoy los deja como están; apagar es más seguro, pero la
  letra no lo pide. Es una decisión de producto, no técnica.
- **La escala del `ts` (D3).** El estándar no la fija. El simulador de la cátedra la mandó en hora
  del control, y el controlador acepta las dos. Si aparece un termostato real, conviene confirmar qué
  escala usa (`RelojControlTest` tiene los dos casos).
- **La red del laboratorio (L19).** Ya se corrió: la aceptación dio 10 de 10. Lo que dejó para
  revisar son L1, L20, L21 y L22, con los números de esa corrida.
- **El estándar de interoperabilidad v2** es un contrato con terceros: cambios como TLS, roles o
  tarifas nuevas (R12, R14) se acuerdan primero ahí.
