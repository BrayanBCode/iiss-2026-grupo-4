# Decisiones del desarrollo

Registro de las decisiones que dieron forma al controlador, para el equipo que continúe el
desarrollo. Cada una dice qué se decidió, por qué, qué se descartó y qué consecuencias tiene. Las de
arquitectura (`A…`) se tomaron entre las iteraciones 2 y 6; las del equipo (`D…`) salieron de la
revisión del docente en la iteración 5 ([`revision-docente.md`](../planificacion/revision-docente.md))
y del planning de la 6 ([`planificacion-iter6.md`](../planificacion/planificacion-iter6.md)).

El diseño que resulta de estas decisiones está en el [4+1](4mas1.md); lo que todavía no resuelven,
en [limitaciones y roadmap](roadmap.md).

## Índice

| Id | Decisión | Estado |
|---|---|---|
| A1 | Dos módulos: `core` en Java puro y `engine` con Spring Boot | Vigente |
| A2 | Prioridad por mayor déficit, con margen de intercambio y desempate por id | Vigente |
| A3 | Histéresis de 0,3 °C | Vigente |
| A4 | Un solo dueño del estado del control: `CoreAdapter` | Vigente (D6) |
| A5 | La red hacia los switches, fuera del candado y en un hilo propio, con pasadas secuenciales | Vigente (L9) |
| A6 | Nunca superar la contratada, tampoco por un instante: OFF antes que ON y ON retenido | Vigente |
| A7 | Consultar los switches (§4) antes de comandar y reconciliar cada 15 s | Vigente |
| A8 | MQTT con Paho y reconexión propia | Vigente |
| A9 | Registro (§6) con reintentos; el broker lo informa el registro | Vigente |
| A10 | Inventario persistido en un archivo JSON | Vigente (L13) |
| A11 | Seguridad con `X-API-Key`, una clave por dirección | Vigente (L11, L12) |
| A12 | Tests de integración con broker y switches dentro del proceso | Vigente |
| A13 | Calidad exigida por el build: formato, tests y cobertura del core | Vigente |
| A14 | Imagen Docker en dos etapas, sin correr los tests adentro | Vigente |
| A15 | Timeouts de los switches: 2 s de conexión, 10 s de respuesta, 5 s entre reintentos | Vigente |
| A16 | Cortar la franja punta un poco antes de que empiece | Vigente (T24e) |
| A17 | Una habitación cuyo switch no prende cede su potencia mientras espera | Vigente (T34) |
| A18 | De lo que está fuera de contrato, tolerar lo que se entiende sin riesgo | Vigente (T24d, T31, T33) |
| A19 | Un switch colgado no traba a los demás: timeout en tiempo de control y sanos primero | Vigente (T37) |
| A20 | El switch de una habitación que sale del sitio no se comanda; se avisa | Vigente (T38, L20) |
| A21 | Con switches lentos, la punta se corta antes | Vigente (T39) |
| A22 | Un switch que no obedece se deja de comandar un rato | Vigente (T40) |
| D1 | El core queda sin estado: modelo y función pura `decidir` | Vigente |
| D2 | `Estimulo` es modelo del core y lo procesa `CoreAdapter` | Vigente |
| D3 | El `ts` es el instante de la lectura, llevado a la hora del control | Vigente (cambió en T24b) |
| D4 | Zona fija del sitio: `America/Montevideo` | Vigente |
| D5 | Una rama por tarjeta, con PR hacia la rama de la iteración | Vigente |
| D6 | El estado de ejecución lo administra `CoreAdapter` | Vigente |
| D7 | Al re-sincronizar, las lecturas vencidas se olvidan | Vigente |
| D8 | Una lectura con `ts` más viejo que la vigencia se descarta y se cuenta | Vigente |
| D9 | DETENER deja los switches como están; apagar al detener queda configurable en el roadmap | Vigente (T22) |
| D10 | Tras reiniciar el contenedor, el control arranca DETENIDO hasta el próximo INICIAR | Vigente (T22, L2) |
| D11 | Contar y loguear las temperaturas fuera de rango (mejora de T20) | Vigente (T29, confirmada en T22) |
| D12 | Después de T13 solo entran correcciones | Vigente (T22) |

---

## Arquitectura

### A1 · Dos módulos: `core` en Java puro y `engine` con Spring Boot

- **Contexto.** La letra pide un core de decisión desarrollado con TDD y separado de la comunicación
  con el exterior (MQTT, REST, registro).
- **Decisión.** `modules/core` no tiene dependencias de producción: solo el modelo y la decisión.
  `modules/engine` (Spring Boot 3.5, Java 25) hace todo lo demás y llama al core.
- **Alternativas.** Un solo módulo con paquetes separados: nada impide que la decisión termine
  dependiendo de Spring o de la red.
- **Consecuencias.** El core se prueba rápido y sin mocks (`DecidirTest`) y su cobertura se exige en
  el build (A13). Cualquier cosa que necesite hora, red o persistencia vive en el engine.

### A2 · Prioridad por mayor déficit, con margen de intercambio y desempate por id

- **Contexto.** Si la potencia contratada no alcanza, hay que elegir qué habitaciones calefaccionar
  con un criterio "justificable y no azaroso" (letra).
- **Decisión.** `MayorDeficitPrimero`: primero la habitación más lejos de su temperatura esperada. A
  una que ya está encendida se le suma un margen de 0,5 °C (`control.margen-intercambio-c`) para que
  dos habitaciones parecidas no se alternen en cada lectura. Los empates se resuelven por `id`.
- **Alternativas.** Orden fijo del inventario (injusto); turnos por tiempo (no mira la necesidad).
- **Consecuencias.** Es determinista y explicable en la demo. `CriterioPrioridad` es una interfaz:
  se puede agregar otro criterio sin tocar `decidir` (roadmap R13).

### A3 · Histéresis de 0,3 °C

- **Contexto.** Una lectura que oscila alrededor de la temperatura esperada haría conmutar el switch
  en cada mensaje.
- **Decisión.** Una habitación encendida se apaga al llegar a la esperada; una apagada se enciende
  recién cuando baja 0,3 °C de la esperada (`control.histeresis-c`).
- **Consecuencias.** Menos conmutaciones a cambio de una banda de 0,3 °C. Lo prueban los grupos de
  histéresis de `DecidirTest` y `CoreAdapterEstimulosTest`.

### A4 · Un solo dueño del estado del control: `CoreAdapter`

- **Contexto.** Hasta la iteración 5 el estado estaba duplicado entre el core y el engine, con dos
  banderas de "en ejecución" que podían desincronizarse (observación del docente).
- **Decisión.** `CoreAdapter` guarda lecturas, sitio, hora y estado de ejecución, con sus métodos
  `synchronized`; es el único que llama al core. Los candados se toman siempre en el mismo orden
  (`InventarioService` → `ControlService` → `CoreAdapter`) y nunca al revés (D1, D6).
- **Consecuencias.** No hay deadlock posible y no hay estado duplicado. La clase quedó grande (unas
  700 líneas): separarla es el paso R6 del roadmap (L15).

### A5 · La red hacia los switches, fuera del candado y en un hilo propio, con pasadas secuenciales

- **Contexto.** En el registro (§6) el entorno nos da INICIAR antes de responder, y es él mismo quien
  sirve los switches: esperar a los switches dentro de INICIAR era un bloqueo circular.
- **Decisión.** Cada estímulo actualiza la decisión y pide una pasada al hilo `switches`; la pasada
  manda los comandos de a uno. Los pedidos que llegan durante una pasada se juntan en la siguiente.
- **Alternativas.** Pasadas en paralelo: más rápidas con switches caídos, pero complican el orden OFF
  antes que ON (A6).
- **Consecuencias.** INICIAR y `PUT /sitio` responden sin esperar a los switches. Un switch colgado
  atrasa a los que vienen detrás en la pasada hasta su timeout; A19 lo acota a una sola llamada en
  curso (limitación L9, paso R7 del roadmap).

### A6 · Nunca superar la contratada, tampoco por un instante

- **Contexto.** Al redistribuir la potencia, el engine mandaba el ON antes que el OFF y por un
  momento había 4,5 kW con 3,7 contratados (encontrado en los tests de integración de la iteración 5).
- **Decisión.** En cada pasada se mandan primero los OFF y después los ON. Si el OFF que liberaba
  potencia no se confirma, el ON que superaría la contratada se retiene hasta que el switch confirme.
- **Consecuencias.** La contratada se respeta aunque un switch falle (`FallaSwitchTest`,
  `ControlCargaTest`). No se deja margen: se puede usar la contratada completa.

### A7 · Consultar los switches (§4) antes de comandar y reconciliar cada 15 s

- **Contexto.** El estado real de un switch puede no ser el que creemos: lo reinicia el simulador
  entre escenarios (`DESCONOCIDO`), alguien lo mueve a mano o falla un comando.
- **Decisión.** Al INICIAR la primera pasada consulta cada switch antes de comandar. Cada 15 s
  (`control.reconciliacion-segundos`) se vuelve a consultar y se corrige lo que no coincida. Los
  15 s son de tiempo de control, como el tick: con factor 60 la reconciliación corre cada 1 s
  real, que es el mínimo (T36). Sin escalarla, a ×60 un switch prendido a mano seguía prendido
  hasta 15 min de control, aunque se pasara de la contratada.
- **Consecuencias.** El control se recupera solo de switches reiniciados o caídos. La consulta solo
  deja una línea en el log cuando hay que corregir algo.

### A8 · MQTT con Paho y reconexión propia

- **Contexto.** La reconexión automática de Paho 1.2.5 duplicaba la espera hasta 128 s, la compartía
  entre clientes (campo estático) y dejaba clientes vivos que no se podían cerrar.
- **Decisión.** Se apaga la reconexión automática. El hilo `mqtt-conexion` reintenta cada 5 s con un
  cliente nuevo y restablece las suscripciones; nunca desde el hilo de Paho. Suscripción con QoS 1 y
  sesión limpia. La versión es MQTT 3.1.1 fija y el identificador del cliente tiene como máximo 23
  caracteres: si no, Paho reintenta con 3.1 y ese broker rechaza identificadores largos (pasó con
  `ecowarm-run` al volver de una caída, T35).
- **Consecuencias.** Tras una caída del broker se reconecta en segundos (`FallaBrokerMqttTest`). Lo
  publicado durante la caída se pierde; si la caída supera la vigencia, la habitación se apaga (L10).

### A9 · Registro (§6) con reintentos; el broker lo informa el registro

- **Contexto.** El entorno puede tardar en levantar, y su broker no tiene una dirección fija.
- **Decisión.** El registro reintenta con espera exponencial (2, 4, 8 … 30 s). El broker es el que
  devuelve el registro; `MQTT_BROKER` solo se usa si no lo informa o no hay entorno. Al arrancar, si
  la `baseUrl` que declaramos no es alcanzable por el entorno (`localhost` con el entorno en otra
  máquina, una IP mal escrita), se avisa en el log (T24a).
- **Consecuencias.** El orden de arranque no importa y la configuración es una sola dirección. Una
  `baseUrl` que es una IP válida pero no la nuestra no se puede detectar.

### A10 · Inventario persistido en un archivo JSON

- **Contexto.** El §5.1 pide que el sitio sobreviva a un reinicio.
- **Decisión.** `data/inventario.json`, con escritura atómica; uno corrupto no impide arrancar. En
  Docker va en un volumen.
- **Alternativas.** Una base de datos: no hacía falta para un solo documento.
- **Consecuencias.** Simple, pero sin historial de lecturas ni decisiones (L13, paso R11).

### A11 · Seguridad con `X-API-Key`, una clave por dirección

- **Decisión.** Como pide el estándar v2: una clave que exige el controlador (`API_KEY`), otra que
  exige el entorno (`ENTORNO_API_KEY`) y otra la de los switches (`SWITCH_API_KEY`), todas por
  variable de entorno.
- **Consecuencias.** Sin TLS ni roles: solo apto para una red de confianza (L11, L12).

### A12 · Tests de integración con broker y switches dentro del proceso

- **Decisión.** Moquette (broker MQTT embebido) y un servidor HTTP con switches falsos, en el mismo
  proceso que el engine (`IntegracionBase`).
- **Alternativas.** Testcontainers o Docker en los tests: más lento y necesita Docker en la CI.
- **Consecuencias.** Las fallas (broker caído, switch colgado, termostato caído) se prueban de punta a
  punta en segundos, con `mvn verify`. Usan tiempos reales: alguno tarda varios segundos.

### A13 · Calidad exigida por el build

- **Decisión.** `mvn verify` falla si el código no está formateado (Spotless con google-java-format,
  estilo AOSP), si falla un test o si la cobertura del core baja del 85 % de líneas o de ramas
  (JaCoCo). La CI (GitHub Actions) corre `mvn -B verify` en cada push a `master` o a las ramas
  `implementacion*`, en los tags `version*` y en cada PR, salvo que solo cambie documentación.
- **Consecuencias.** No se puede mergear algo que rompa estas reglas. La cobertura del engine no se
  mide todavía (L16, paso R5).

### A14 · Imagen Docker en dos etapas, sin correr los tests adentro

- **Decisión.** `modules/engine/Dockerfile` compila con Maven y JDK 25 y copia solo el `engine.jar` a
  una imagen con JRE 25. Los tests los corre `scripts/test.sh` o la CI.
- **Consecuencias.** No hace falta Java en el anfitrión para levantar el controlador. La imagen se
  construye desde la copia local: hay que reconstruirla (`--build`) para que tome cambios.

### A15 · Timeouts de los switches

- **Decisión.** 2 s para conectar, 10 s para recibir la respuesta y 5 s antes de reintentar a un
  switch que falló (`switch.timeout-conexion-ms`, `switch.timeout-lectura-ms`,
  `switch.espera-reintento-segundos`). La respuesta pasó de 3 s a 10 s para que un switch lento del
  simulador no cuente como caído. Son tiempos con factor 1: con el control acelerado se dividen por
  el factor, con un piso de 0,5 s (A19).
- **Consecuencias.** Con factor 1, un switch colgado se da por caído a los 12 s; a ×60, a los 0,5 s
  reales (30 s de control). Por las pasadas de a una (A5) atrasa a los que vienen detrás; A19 pone a
  los sanos adelante.

### A16 · Cortar la franja punta un poco antes de que empiece

- **Contexto.** En el escenario E11 del simulador oficial (`ecowarm-run`, ×60) los OFF llegaron
  entre 1 y 2 s de control después de las 17:00: el tick y la pasada de switches tardan unos
  milisegundos reales, que a ×60 son segundos simulados. La expectativa "en punta no se enciende
  nada" (nivel riesgo) falló.
- **Decisión.** El core trata la punta como si empezara `anticipacionPunta` antes
  (`ParametrosControl`; en el engine, `control.anticipacion-punta-segundos`, 60 s de tiempo del
  control por defecto). La salida no se adelanta: a la hora de fin se restituye como antes (T24e).
- **Alternativas.** Programar un evento en el borde exacto de la franja (se descartó en T12 por
  complejidad, y igual llegaría tarde por la latencia de los comandos).
- **Consecuencias.** Con el corte anticipado pasan los 9 escenarios del simulador oficial
  ([corrida del 2026-11-06](../tests/corrida-simulador/2026-11-06-ecowarm-run/README.md)). Se
  calefacciona un minuto menos antes de cada punta. Lo prueban
  `DecidirTest.anticipacion_cortaAntesYRestituyeALaHora` y `anticipacion_respetaLosDias`.

### A17 · Una habitación cuyo switch no prende cede su potencia mientras espera

- **Contexto.** Escenario F09 de la clase ("un switch que no prende"): el core no sabe nada de los
  switches, así que seguía eligiendo esa habitación y su potencia quedaba sin usar mientras otra,
  fría, esperaba sin potencia.
- **Decisión.** Si el switch aceptó el ON y sigue apagado, o el ON falló y una consulta lo muestra
  apagado, `CoreAdapter` deja esa habitación afuera de la decisión (`OFF` con
  `SWITCH_NO_RESPONDE`) durante una espera que se duplica en cada falla seguida (6 esperas de
  reintento, 30 s, hasta 10 min). Al vencer se reintenta; si prende, vuelve a tener prioridad. Un
  switch de estado desconocido se consulta en la próxima pasada, no a los 15 s de la reconciliación.
- **Alternativas.** Reintentar en cada pasada (las vecinas conmutarían cada pocos segundos) o
  avisar al core (el core no conoce los switches, A1).
- **Consecuencias.** La potencia se aprovecha aunque un switch falle. Un switch caído o sin
  respuesta sigue igual que antes: no se sabe si prendió, así que se lo cuenta como encendido (A6).
  Lo prueban `CoreAdapterTest.switchQueNoPrende_cedeLaPotencia` y
  `onConError_seConsultaYCedeLaPotencia`.

### A18 · De lo que está fuera de contrato, tolerar lo que se entiende sin riesgo

- **Contexto.** Escenarios F04 y F07 de la clase: termostatos y switches que no respetan el
  estándar. Descartar todo lo que no es exacto deja al control sin datos; aceptar cualquier cosa es
  calefaccionar a ciegas.
- **Decisión.** Se acepta lo que tiene un único significado posible y no contradice el contrato: un
  `tC` numérico escrito como texto, un `ts` como texto o en milisegundos (T33), un `estado` de
  switch en minúscula, un JSON con otro `Content-Type` (T24d, T31) y un comando que responde 2xx
  aunque su cuerpo no sea JSON (se toma como aplicado y la reconciliación lo verifica). **No** se
  acepta lo que el contrato prohíbe o es ambiguo: una lectura sin `tC` (aunque traiga `tF`), una
  temperatura fuera del rango del sensor, una consulta de switch que no es JSON.
- **Consecuencias.** Más tolerante sin bajar la seguridad: todo lo aceptado pasa por las mismas
  reglas (rango del sensor, vigencia, contratada). Lo prueban `PayloadTermostatoTest`,
  `ClienteSwitchRestTest` y `PerdidaMensajesTest.payloadInvalido`.

### A19 · Un switch colgado no traba a los demás

- **Contexto.** Una prueba de estrés propia (`EstresTest`, T37), más dura que los escenarios de la
  clase y que `ecowarm-run`: a ×60, con 3 switches que dejan de responder a las 16:54, el switch
  sano seguía encendido a las 17:54 de control, una hora entera en punta. Las llamadas de la pasada
  son de a una (A5): cada colgado trababa el hilo 10 s reales (10 min de control), la espera de 5 s
  entre reintentos vencía antes de terminar la pasada, y la siguiente volvía a empezar por ellos.
- **Decisión.** Sin agregar concurrencia, tres reglas:
  1. Los timeouts de los switches (A15) se miden en tiempo de control: se dividen por el factor en
     cada INICIAR, con un piso de 0,5 s reales y nunca más que lo configurado
     (`ClienteSwitchRest.ajustarAlFactor`). Es el mismo criterio que el tick (T12) y la
     reconciliación (T36).
  2. Cada pasada atiende primero (consulta, OFF, ON) los switches sanos y después los que fallaron y
     todavía no volvieron a responder.
  3. Antes de llamar a un switch que viene fallando, si cambió la acción decidida de alguna
     habitación, la pasada se corta y la siguiente empieza de nuevo por los sanos.
- **Alternativas.** Una tarea por switch, o un cliente HTTP asíncrono: un colgado no atrasaría a
  nadie, pero los comandos saldrían en paralelo y habría que extender la retención del ON (A6) a los
  ON en vuelo, en la clase central y a días de la aceptación (queda como R7). Dos hilos fijos (sanos
  y fallados): un colgado que falla por primera vez igual traba a los sanos un timeout completo.
  Solo acortar el timeout: con varios colgados el atraso se suma.
- **Consecuencias.** Un switch sano espera, como mucho, una llamada en curso a uno que no responde:
  0,5 s reales a ×60, dentro del minuto de corte anticipado (A16). Un switch lento pero vivo, que a
  ×60 tarda más de 0,5 s, se toma como caído: se reintenta a los 5 s y, mientras tanto, cuenta
  como encendido para la contratada (A6). Con factor 1 el atraso por switch colgado sigue siendo de
  hasta 12 s (L9). Lo prueban `EstresTest` (switches colgados, ×600, ×3600, 50 habitaciones y
  fallas combinadas cruzando las 17:00), `CoreAdapterTest.switchQueFalla_seAtiendeDespuesDeLosSanos`
  y `ClienteSwitchRestTest.conFactorAlto_noEsperaElTimeoutConfigurado`.

### A20 · El switch de una habitación que sale del sitio no se comanda; se avisa

- **Contexto.** Prueba A1 de la lista de estrés (T38): si un `PUT /sitio` saca una habitación cuyo
  switch está encendido, el controlador se olvidaba de ella y el calefactor quedaba prendido sin que
  nada lo dijera, consumiendo potencia que ya no se cuenta.
- **Decisión.** No se lo comanda: ya no es un switch del sitio vigente, y el simulador verifica
  como contrato que todos los comandos vayan a switches del sitio (`soloSwitchesDelSitio`). Si se
  lo conocía ON, o no se sabía y la última decisión era ON, se avisa con un WARN y queda en
  `/diagnostico` (`switchesFueraDelSitio`) para apagarlo a mano, hasta que vuelva al sitio. Lo
  mismo con el switch viejo de una habitación que cambia de switch (T38).
- **Alternativas.** Mandarle OFF antes de olvidarlo: más seguro en la casa, pero rompe el contrato
  del simulador. Contar su potencia como encendida al decidir los ON: el escenario E16 de la clase
  (achicar de 20 a 5 habitaciones) espera que las 5 que quedan prendan, y no prenderían.
- **Consecuencias.** Es la limitación L20: el estándar no dice qué hacer, igual que con DETENER
  (D9). Lo prueban `CoreAdapterTest.quitarHabitacionEncendida_noSeComandaYSeAvisa` y
  `EscenariosSimuladorTest.crecerYAchicarElSitio`.

### A21 · Con switches lentos, la punta se corta antes

- **Contexto.** Prueba S1 (T39): 20 switches que tardan 400 ms reales en responder. A ×60 no llegan
  al timeout de 0,5 s (A19), así que cuentan como sanos, pero los OFF salen de a uno (A5): apagar
  todo lleva 8 s reales, 8 min de control, y la última habitación se apagaba a las 17:10, en punta.
  Además, si a las 17:00 había una reconciliación en curso, los OFF esperaban a que consultara los
  20.
- **Decisión.** El adaptador mide lo que tarda cada llamada que responde (un máximo que decae 10 %
  por llamada, en ms reales) y amplía la anticipación de la punta (A16) en cada decisión: switches
  del sitio × lo que tarda cada llamada × el factor × 1,5 de margen, sumado a la configurada y con
  un tope de 30 min de control. Se cuentan todos los switches del sitio y no solo los encendidos:
  la primera versión contaba los encendidos y, a medida que se confirmaban los OFF, la anticipación
  se achicaba y las habitaciones volvían a prender antes de la punta (la última se apagaba a las
  17:04). El core recibe la anticipación como parámetro de `decidir` y nunca usa menos
  que la configurada. Además, si cambia una decisión mientras se consulta, se dejan de consultar
  los sanos y se pasa a mandar.
- **Alternativas.** Mandar los OFF en paralelo: es el R7 que se descartó en A19 por el riesgo en la
  clase central. Subir la anticipación configurada para todos: con switches rápidos se
  calefaccionaría menos sin necesidad.
- **Consecuencias.** Con switches rápidos no cambia nada (la medición da milisegundos). Con
  switches lentos se calefacciona unos minutos menos antes de la punta, y el log lo dice
  (`Anticipación de la punta: … s de control`). La salida de la punta no se adelanta. Lo prueban
  `EstresTest.veinteSwitchesLentos_cruzandoLaPunta`,
  `CoreAdapterTest.switchesLentos_amplianLaAnticipacionDeLaPunta` y
  `DecidirTest.anticipacionAmpliada_cortaAntesYNuncaMenos`.

### A22 · Un switch que no obedece se deja de comandar un rato

- **Contexto.** Prueba S3 (T40): un switch que acepta el comando y responde lo pedido, pero la
  consulta (§4) lo sigue mostrando como estaba. La reconciliación lo corregía en cada pasada, cada
  1 s real a ×60, sin fin.
- **Decisión.** Si un switch aceptó lo decidido y la consulta muestra lo contrario 3 veces
  seguidas, no obedece: con ON decidido cede su potencia como uno que no prende (A17); con OFF
  decidido no se lo comanda durante una espera escalonada (la misma de A17: 6 esperas de reintento
  que se duplican, hasta 10 min) y se avisa con un WARN. Mientras tanto su estado conocido es el de
  la consulta, así que cuenta como encendido para la contratada (A6). Cuando una consulta lo
  muestra como se decidió, se olvida.
- **Alternativas.** Seguir reintentando siempre: llena el log y la red, y no cambia nada. Darlo por
  caído: sí responde, y el grupo de los que fallan (A19) es para los que no responden.
- **Consecuencias.** Un switch que se movió a mano una vez se sigue corrigiendo en la primera
  reconciliación, como antes. Lo prueban `CoreAdapterTest.switchQueMienteConElOff_seDejaDeComandarUnaEspera`
  y `CoreAdapterTest.switchQueMienteConElOn_cedeLaPotencia`.

---

## Equipo (revisión de la iteración 5 y planning de la 6)

### D1 · El core queda sin estado

- **Contexto.** El core guardaba un estado que el engine también guardaba (sugerencia del docente).
- **Decisión.** El core contiene el modelo y la función pura `decidir(sitio, habitaciones, estados,
  ahora)`, más `lecturaValida` y `lecturaVencida`. Se eliminaron `Core.procesar` y `CoreClient`.
- **Consecuencias.** La decisión se prueba por situación (`DecidirTest`). Los tests de estímulos
  pasaron al engine (`CoreAdapterEstimulosTest`): por eso el core bajó de 116 a 54 tests.

### D2 · `Estimulo` es modelo del core y lo procesa `CoreAdapter`

- **Contexto.** La letra de la iteración 4 pide el "modelo de los estímulos".
- **Decisión.** `NuevaLectura`, `Tick`, `ConfiguracionActualizada` y `Sincronizacion` quedan en el
  core como modelo; los aplica `CoreAdapter.procesar`.

### D3 · El `ts` es el instante de la lectura, llevado a la hora del control

- **Contexto.** El estándar no fija la escala del `ts` (§2). El docente indicó hora real, y así se
  implementó en T8b. En la corrida del 2026-11-04 el simulador lo mandó **ya en hora del control**:
  convertido como hora real caía meses atrás y se descartaron todas las lecturas.
- **Decisión.** Se aceptan las dos escalas: `RelojControl.tsEnHoraDelControl` calcula el `ts` tal
  cual y convertido desde hora real, y se queda con el más cercano a la hora actual del control
  (T24b).
- **Consecuencias.** Funciona con las dos versiones del simulador sin configurar nada. A factores
  altos la interpretación equivocada queda a horas de distancia, así que no puede ganar.

### D4 · Zona fija del sitio: `America/Montevideo`

- **Decisión.** La franja punta se lee en hora de Uruguay, configurable con `CONTROL_ZONA_HORARIA`;
  nunca `ZoneId.systemDefault()`, que en Docker suele ser UTC.
- **Consecuencias.** Confirmada por el docente y por la corrida del 2026-10-28: el simulador cruzó la
  punta con `fechaHora` 01:00Z, las 22:00 en Montevideo.

### D5 · Una rama por tarjeta, con PR hacia la rama de la iteración

- **Decisión.** `iter6/<id>-<tema>` hacia `implementacion6`; cada cambio pasa por revisión y por la CI.

### D6 · El estado de ejecución lo administra `CoreAdapter`

- **Decisión.** Ver A4. `ControlService` le delega `iniciar`, `detener` y `enEjecucion`: la
  dependencia va en un solo sentido.

### D7 · Al re-sincronizar, las lecturas vencidas se olvidan

- **Contexto.** Un INICIAR repetido re-sellaba todas las lecturas con la hora nueva y un dato viejo
  volvía a contar como reciente.
- **Decisión.** Las lecturas ya vencidas con el reloj anterior se olvidan (la habitación queda
  `SIN_LECTURA`); las vigentes se marcan con la hora nueva.
- **Consecuencias.** Una lectura vigente recupera la vigencia completa (L3, paso R3).

### D8 · Una lectura con `ts` más viejo que la vigencia se descarta y se cuenta

- **Decisión.** Se descarta y se cuenta en `/diagnostico` como `TS_VENCIDO`, igual que las que
  llegan fuera de orden (`FUERA_DE_ORDEN`).
- **Consecuencias.** Un dato obsoleto no sostiene la calefacción. Los descartes dejan una línea INFO
  en el log con el `ts` y la hora del control.

### D11 · Las temperaturas fuera de rango se cuentan y se loguean

- **Contexto.** Una temperatura fuera del rango del sensor (-40 a 85 °C) o `NaN` se descartaba sin
  dejar rastro: ni una línea en el log ni un contador en `/diagnostico`. En una corrida, una
  habitación con el sensor roto parecía simplemente "no cambiar". Era la mejora pendiente de T20.
- **Decisión.** Se descarta igual que antes (se conserva la última lectura buena mientras esté
  vigente), pero además se cuenta por habitación en `/diagnostico` como `FUERA_DE_RANGO`, junto a
  `TS_VENCIDO` y `FUERA_DE_ORDEN` (D8), y deja una línea INFO en el log con el valor recibido (T29).
- **Alternativas.** Un rango "razonable" para interiores, más angosto que el del sensor: cambiaría
  qué se acepta y no solo qué se ve, así que queda en el roadmap (R4).
- **Consecuencias.** No cambia ninguna decisión del control. Un sensor roto se ve en el log y en
  `/diagnostico`. Lo prueba `PerdidaMensajesTest.lecturaFueraDeRango`. El equipo la confirmó en T22.

### D9 · DETENER deja los switches como están

- **Contexto.** El §5.2 no dice qué hacer con los switches al recibir DETENER. Apagarlos deja la casa
  sin calefacción si el entorno se detiene por un problema propio; no apagarlos deja calefactores
  prendidos sin control.
- **Decisión.** Se mantiene el comportamiento de siempre: DETENER deja de comandar y los switches
  quedan como estaban (T22).
- **Alternativas.** Apagar todo al detener, o hacerlo configurable (`control.al-detener=DEJAR|APAGAR`).
  La opción configurable, con el default actual, queda como paso R1 del roadmap.
- **Consecuencias.** Es la limitación L1. Lo prueba `ArranqueParadaTest.detenerDejaDeComandar`.

### D10 · Tras reiniciar el contenedor, el control arranca DETENIDO

- **Contexto.** El inventario sobrevive al reinicio (A10), pero la fecha, el factor y el instante del
  INICIAR no se guardan.
- **Decisión.** Después de un reinicio el control arranca DETENIDO y espera el próximo INICIAR del
  entorno (T22). Se documenta como limitación.
- **Alternativas.** Guardar el reloj junto al inventario y retomar solo (paso R2 del roadmap).
- **Consecuencias.** Es la limitación L2. Lo fija `ApiEstandarTest.controlArrancaDetenido`.

### D12 · Después de T13 solo entran correcciones

- **Decisión.** Desde la validación con el simulador (T13) hasta la entrega solo se mergean
  correcciones de problemas encontrados, no funcionalidades nuevas (T22).
- **Consecuencias.** Lo que entró después de la corrida del 2026-11-06 (T31–T37) son correcciones
  de comportamientos vistos en el simulador oficial o en las pruebas de estrés.

## Cómo agregar una decisión

Una sección por decisión, con **Contexto** (qué problema había), **Decisión** (qué se hizo, con los
nombres del código), **Alternativas** (qué se descartó y por qué) y **Consecuencias** (qué se gana,
qué se pierde y dónde se ve: test, limitación o paso del roadmap). Se agrega al índice con un id
nuevo y no se borra una decisión reemplazada: se marca como reemplazada y se enlaza la nueva.
