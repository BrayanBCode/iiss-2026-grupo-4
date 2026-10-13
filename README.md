# EcoWarm — Control de calefacción eléctrica (IoT)

Controlador de calefacción para el hogar: recibe la temperatura de cada habitación de termostatos
(MQTT), decide qué habitaciones reciben energía —sin superar la potencia contratada, cortando en el
horario de energía cara y con un criterio de prioridad justificable— y comanda los switches de
calefacción (REST). Implementa el **estándar de interoperabilidad EcoWarm v2** para integrarse con
el simulador de la cátedra y con cualquier implementación conforme.

Proyecto del curso **Taller IISS 2026** (UTEC) — grupo 4. Iteración 4.

| Documento | |
|---|---|
| Visión del producto | [`docs/vision.md`](docs/vision.md) |
| Historias, personas, escenarios | [`docs/Producto/`](docs/Producto) |
| Estándar de interoperabilidad v2 | [`docs/api/interop-v2.md`](docs/api/interop-v2.md) (+ [`controlador`](docs/api/controlador.openapi-v2.yaml) / [`simulador`](docs/api/simulador.openapi-v2.yaml) OpenAPI) |
| API de este controlador (OpenAPI 3) | [`docs/api/openapi.yaml`](docs/api/openapi.yaml) |
| Arquitectura 4+1 (vista lógica + secuencias) | [`docs/arquitectura/4mas1.md`](docs/arquitectura/4mas1.md) |
| Criterio de adecuación de los tests | [`docs/tests/criterio-tests.md`](docs/tests/criterio-tests.md) |
| Jira | [enlace](https://estudiantes-grupo8-2026.atlassian.net/jira/software/projects/G1234/boards/3) |

---

## Arquitectura en una página

```
  termostatos ──MQTT §2──▶ ┌────────────── controlador (Docker) ──────────────┐ ──REST §3/§4──▶ switches
                           │  engine (Spring Boot)                            │
  entorno ──REST §5──────▶ │   MQTT · REST · registro §6 · reloj · persistencia│
  (simulador / checker)    │        │ estímulos            ▲ decisiones       │
         ◀──REST §6─────── │        ▼                      │                  │
                           │  core (Java puro): función de decisión (TDD)     │
                           └──────────────────────────────────────────────────┘
```

- **core** (`modules/core`): decide qué habitación recibe energía, con un modelo interno del estado,
  de los estímulos (`NuevaLectura`, `Tick`, `ConfiguracionActualizada`, `Sincronizacion`) y de las
  decisiones (`ON`/`OFF` + `Motivo`). No se comunica con nada: no tiene dependencias de producción.
- **engine** (`modules/engine`): integra el core con el exterior. Recibe MQTT, expone el API del
  estándar, se registra ante el entorno, persiste el inventario, lleva el reloj del control (con
  aceleración), dispara los eventos temporales y comanda/consulta los switches.

### Cómo decide el core

Ante cada estímulo recalcula, para cada habitación:

1. **Necesidad**: sin lectura → OFF; lectura vencida (termostato caído, 10 min reales) → OFF;
   en su temperatura (con histéresis de 0.3 °C para no conmutar con cada décima) → OFF;
   potencia mayor que la contratada → OFF; si no, tiene **déficit**.
2. **Tarifa**: en la franja punta, ninguna con déficit recibe energía (corte al entrar,
   restitución al salir).
3. **Reparto**: las que tienen déficit se ordenan por **mayor déficit primero** (la que ya está
   encendida conserva su lugar salvo que otra la supere por más de 0.5 °C: se turnan en vez de
   alternar; desempate por id) y se les asigna potencia mientras entre en la contratada.

Además descarta lecturas imposibles, fuera de orden o de habitaciones desconocidas, nunca hace
retroceder el reloj con un tick atrasado, y valida y aplica la configuración de forma atómica.
Detalle en [`4mas1.md`](docs/arquitectura/4mas1.md).

---

## Estructura del repositorio

```
.
├── docs/
│   ├── api/            interop-v2.md, OpenAPI del estándar y openapi.yaml de este controlador
│   ├── arquitectura/   4mas1.md (vista lógica + diagramas de secuencia)
│   ├── tests/          criterio-tests.md (criterio de adecuación) y resultados.md (última ejecución)
│   └── Producto/       historias, personas, características, escenario
├── docker/
│   ├── docker-compose.yml       el controlador, contra un entorno en el anfitrión (:9090)
│   └── docker-compose.lab.yml   superposición para la red del simulador (iiss-lab-net)
├── scripts/            build, up, down, stop, test, api-demo, send-temp, receive-temp
├── modules/
│   ├── core/           lógica de decisión pura + tests unitarios (TDD)
│   └── engine/         Spring Boot: integración con el exterior + tests
└── pom.xml             POM padre
```

Paquetes del engine: `inventario` (§1, §5.1), `control` (§5.2, reloj, temporizador), `adaptador`
(`CoreAdapter`, único puente con el core), `switches` (§3, §4), `mqtt` (§2), `registro` (§6),
`rest` (controllers, filtro `X-API-Key`, errores uniformes), `config`.

---

## Cómo compilar

No hace falta Java ni Maven en el host: todo se compila dentro de Docker.

```bash
./scripts/build.sh          # compila core + engine (imagen Docker del controlador)
./scripts/test.sh           # corre todos los tests en Docker
./scripts/test.sh core      # solo los del core
```

Con Java 25 y Maven locales también se puede: `mvn package` (el JAR queda en
`modules/engine/target/engine.jar`).

---

## Cómo correr e integrarse

El controlador **solo necesita la dirección del entorno**: se registra (§6), recibe el broker MQTT,
y el entorno le carga el sitio (`PUT /sitio`) y le da `INICIAR`. Si el entorno todavía no está,
reintenta solo (2, 4, 8 … 30 s).

### A. Con el verificador `ecowarm-check` (recomendado para el primer intento)

```bash
# terminal 1: el entorno (broker MQTT, switches e inventario en :9090)
java -jar ecowarm-check.jar --anunciar host.docker.internal

# terminal 2: el controlador en Docker
./scripts/up.sh
docker logs -f ecowarm-grupo4
```

`--anunciar host.docker.internal` es necesario porque el controlador corre en un contenedor: así
el broker y los `urlSwitch` que le informa el checker resuelven desde adentro. Al revés, el
controlador declara `CONTROLADOR_BASE_URL=http://localhost:8080` (su puerto publicado), que es como
lo alcanza el checker desde el anfitrión.

Para ejercitar la autenticación del entorno: `java -jar ecowarm-check.jar --anunciar host.docker.internal --clave X`
y `ENTORNO_API_KEY=X ./scripts/up.sh`.

Sin Docker (controlador y checker en la misma máquina):

```bash
java -jar ecowarm-check.jar
API_KEY=mi-clave ENTORNO_URL=http://localhost:9090 java -jar modules/engine/target/engine.jar
```

### B. Con el simulador de la cátedra

Si el simulador publica su puerto 9090 en el anfitrión, alcanza con `./scripts/up.sh` (pasando su
clave en `ENTORNO_API_KEY`). Si se lo quiere en la misma red Docker del simulador (`iiss-lab-net`):

```bash
ENTORNO_API_KEY=<clave-del-simulador> ./scripts/up.sh lab
```

Eso usa [`docker/docker-compose.lab.yml`](docker/docker-compose.lab.yml): `ENTORNO_URL=http://iiss-simulator:9090`
y `CONTROLADOR_BASE_URL=http://ecowarm-grupo4:8080`.

### C. Instalación real (sin entorno con registro)

Dejar `ENTORNO_URL` vacío y fijar `MQTT_BROKER=tcp://<broker>:1883`; el sitio se carga con
`PUT /sitio` y se arranca con `INICIAR` (ver `scripts/api-demo.sh`).

### Ver qué está pasando

```bash
docker logs -f ecowarm-grupo4          # o ./scripts/receive-temp.sh (logs/engine.log)
curl -H "X-API-Key: ecowarm-grupo4-2026" localhost:8080/diagnostico
```

`/diagnostico` muestra, por habitación, la última temperatura, la decisión del core **y su motivo**
(`CALEFACCIONANDO`, `TARIFA_PUNTA`, `SIN_POTENCIA_DISPONIBLE`, `LECTURA_VENCIDA`, …) y el estado
conocido de su switch; además si MQTT está conectado y si se registró ante el entorno.

```bash
./scripts/stop.sh     # detiene
./scripts/down.sh     # baja el contenedor (el inventario persiste en el volumen "inventario")
```

### Variables de entorno

| Variable | Default | Para qué |
|---|---|---|
| `API_KEY` | `ecowarm-grupo4-2026` (compose) | Clave que **exige** el controlador en `X-API-Key` y que declara al registrarse. No puede ir vacía. |
| `ENTORNO_URL` | `http://host.docker.internal:9090` (compose) | Entorno ante el que registrarse (§6). Vacío = sin registro. |
| `ENTORNO_API_KEY` | vacío | Clave que exige el entorno. Vacío = no se envía. |
| `SWITCH_API_KEY` | = `ENTORNO_API_KEY` | Clave que exigen los switches (§3/§4). |
| `CONTROLADOR_BASE_URL` | `http://localhost:8080` | Dónde el **entorno** alcanza a este controlador. |
| `CONTROLADOR_ETIQUETA` | `ecowarm-grupo4` | Etiqueta estable del registro (idempotente). |
| `MQTT_BROKER` | `tcp://localhost:1883` | Broker si el entorno no lo informa o no hay entorno. |
| `INVENTARIO_ARCHIVO` | `/app/data/inventario.json` (Docker) | Dónde se persiste el inventario. |
| `CONTROL_ZONA_HORARIA` | vacío | Zona para leer la franja punta. Vacío = la zona/offset en que viene la `fechaHora` de `INICIAR` (`…Z` ⇒ UTC). Ej.: `America/Montevideo`. |
| `CONTROL_HISTERESIS_C` | `0.3` | Banda de histéresis (°C). |
| `CONTROL_MARGEN_INTERCAMBIO_C` | `0.5` | Ventaja de la habitación encendida en el reparto (°C). |
| `CONTROL_VIGENCIA_LECTURA_SEGUNDOS` | `600` | Tiempo **real** tras el cual una lectura vence (se escala con `factorTiempo`). |
| `CONTROL_TICK_MS` | `1000` | Período real del tick (cortes de franja, vencimientos). |
| `CONTROL_RECONCILIACION_SEGUNDOS` | `15` | Cada cuánto se consulta el estado real de los switches (§4). |

**Zona horaria de la tarifa.** El estándar dice "hora local, sin conversión de zona". Por defecto se
toma la zona en que el entorno escribe la `fechaHora` de `INICIAR`, así ambos leen la misma hora
local en el mismo instante. Si el entorno espera otra (por ejemplo hora de Uruguay con `fechaHora`
en `Z`), fijar `CONTROL_ZONA_HORARIA`.

---

## API REST

Contrato completo: [`docs/api/openapi.yaml`](docs/api/openapi.yaml). Todas las operaciones exigen
`X-API-Key` (falta o no coincide → `401`). Los errores tienen siempre el cuerpo uniforme del
estándar: `{"timestamp","status","mensaje","errores"}`.

| Método | Ruta | Estándar | Qué hace | Respuestas |
|---|---|---|---|---|
| `GET` | `/sitio` | §5.1 | Inventario **persistido y en uso** | `200`, `404` si no hay |
| `PUT` | `/sitio` | §5.1 | Reemplaza el inventario: valida entero, persiste, aplica en caliente | `200`, `400` con `errores` |
| `GET` | `/control` | §5.2 | `estado`, `fechaHora` (hora del control), `factorTiempo`, `iniciadoEn` | `200` |
| `POST` | `/control/comandos` | §5.2 | `{"accion":"INICIAR","fechaHora":…,"factorTiempo":…}` / `{"accion":"DETENER"}` | `200`, `400` |
| `GET` | `/habitaciones`, `/habitaciones/{id}` | extensión | Habitaciones del inventario | `200`, `404` |
| `POST` | `/habitaciones` | extensión | Alta sobre el inventario (mismas reglas que `PUT /sitio`) | `201`+`Location`, `400`, `409` |
| `PUT` / `DELETE` | `/habitaciones/{id}` | extensión | Modificación / baja | `200` / `204`, `400`, `404` |
| `GET` | `/diagnostico` | extensión | Decisión y motivo por habitación, switches, conexiones | `200` |

Notas: se **soporta aceleración** (`factorTiempo` > 0 cualquiera); `INICIAR` sobre uno en ejecución
re-sincroniza; `DETENER` es idempotente y deja de comandar switches (no apaga lo encendido).

Recorrido completo con curl: `./scripts/api-demo.sh [api-key] [url]`.

---

## Tests

| Módulo | Tests | Qué cubren |
|---|---|---|
| core | 81 | Función de decisión, en 10 grupos: decisión básica, tarifa punta, tope de potencia, criterio, configuración, histéresis, lecturas defectuosas, lecturas vencidas, reloj del control, invariantes (3000 estímulos seudoaleatorios). Cobertura JaCoCo: 99 % líneas, 91 % ramas. |
| engine | 55 | `CoreAdapter` (switches falsos con memoria, reconciliación, fallas), API completa con contexto Spring (`ApiEstandarTest`), validación y persistencia del inventario, reloj acelerado, payload MQTT, registro. |

El criterio por el que el conjunto se considera adecuado está en
[`docs/tests/criterio-tests.md`](docs/tests/criterio-tests.md).

```bash
./scripts/test.sh            # todos (Docker)
./scripts/test.sh core       # solo core; reporte de cobertura en modules/core/target/site/jacoco/index.html
```

---

## Scripts

| Script | Qué hace |
|---|---|
| `build.sh` | Compila el controlador (imagen Docker, incluye el core). |
| `up.sh [lab]` | Compila y levanta el controlador; `lab` lo conecta a la red del simulador. |
| `stop.sh` / `down.sh` | Detiene / baja el contenedor (el inventario se conserva en su volumen). |
| `test.sh [modulo]` | Corre los tests en Docker (todos, `core` o `engine`). |
| `api-demo.sh [key] [url]` | Recorre el API: `PUT/GET /sitio`, un `PUT` inválido, `INICIAR`, diagnóstico, `DETENER`, `401`. |
| `send-temp.sh [t] [topic] [broker]` | Publica una lectura §2 a mano. |
| `receive-temp.sh` | Sigue `logs/engine.log`. |

> Todos se ejecutan desde la **raíz del repositorio**.

---

## Licencias de terceros

- **Docker** — Apache 2.0. **Apache Maven** — Apache 2.0.
- **Spring Boot / Spring Framework** — Apache 2.0. Framework del `engine`.
- **Jackson** — Apache 2.0. JSON del `engine`.
- **Eclipse Paho (mqttv3)** — EPL-2.0. Cliente MQTT.
- **JUnit 5**, **JaCoCo** — EPL-2.0. Tests y cobertura. **Mockito** — MIT.
- **Logback** — EPL-1.0 / LGPL-2.1. Logging de Spring Boot.

Todas permiten su uso en un proyecto MIT. Ver [`LICENSE`](LICENSE).
