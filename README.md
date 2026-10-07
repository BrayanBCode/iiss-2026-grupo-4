# EcoWarm — Automatización de calefacción por losa radiante (IoT)

Software de domótica para el hogar: recibe lecturas de temperatura de termostatos
Shelly H&T (vía MQTT), las persiste en PostgreSQL asociadas a la habitación
correspondiente, y expone un **API REST** para administrar las habitaciones y operar un
**Controlador** que enciende/apaga los switches de calefacción según la temperatura esperada.
Sienta la base para automatizar la calefacción y el control de consumo eléctrico según
tarifas UTE multihorario.

Proyecto del curso **Taller IISS 2026** (Utec) — grupo 4.

Documento de Visión del producto: [`docs/vision.md`](docs/vision.md).

Historias de usuario, personas y escenarios:
[`Historias.md`](docs/Producto/Historias.md), [`Personas.md`](docs/Producto/Personas.md),
[`Caracteristicas.md`](docs/Producto/Caracteristicas.md), [`Escenario.md`](docs/Producto/Escenario.md).

Contrato del API (OpenAPI 3): [`docs/api/openapi.yaml`](docs/api/openapi.yaml).

Proyecto en Jira: [`Enlace`](https://estudiantes-grupo8-2026.atlassian.net/jira/software/projects/G1234/boards/3)

---

## Estructura del repositorio

```
.
├── docs/
│   ├── vision.md                 # Documento de visión del producto
│   ├── api/
│   │   └── openapi.yaml          # Especificación OpenAPI 3 del API REST (Iteración 3)
│   └── Producto/                 # Historias, personas, características y escenario
├── docker/
│   ├── docker-compose.yml        # Orquesta mosquitto, postgres, eventgenerator, engine y switch-stub
│   └── mosquitto.conf            # Config del broker (listener 1883, anónimo habilitado)
├── scripts/
│   ├── build.sh                  # Compila todo el sistema vía Docker
│   ├── up.sh                     # Compila y levanta el sistema completo
│   ├── down.sh                   # Baja y elimina los contenedores y la red (los datos de Postgres se conservan en el volumen pgdata)
│   ├── stop.sh                   # Detiene los contenedores sin eliminarlos
│   ├── test.sh                   # Corre los tests unitarios (mvn test) vía Docker, de todos los módulos o uno solo
│   ├── send-temp.sh              # Publica una lectura de prueba en el tópico MQTT real (curl)
│   ├── receive-temp.sh           # Sigue en vivo el archivo de log del engine (logs/engine.log)
│   └── api-demo.sh               # Recorre todos los endpoints del API con curl (incluye la API key)
├── modules/
│   ├── core/                      # Módulo Maven (Java puro, SIN Spring): LÓGICA DE DECISIÓN (Iteración 4). Solo depende de JUnit (test)
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/uy/edu/utec/iiss/core/   # Core, CriterioPrioridad, MayorDeficitPrimero y model/ (Accion, Decision, Estimulo, Sitio, ...)
│   │       └── test/java/uy/edu/utec/iiss/core/   # CoreTest (JUnit 5, TDD)
│   ├── eventGenerator/            # Módulo Maven: simula los termostatos Shelly publicando por MQTT
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/eventGenerator/
│   │       │   ├── AppEventGenerator.java # Main: crea 3 habitaciones simuladas y publica cada 10s
│   │       │   └── Habitacion.java        # Simula un termostato Shelly H&T (payload JSON con tC/tF/ts)
│   │       └── test/java/eventGenerator/  # Tests unitarios (JUnit 5 + Mockito) del payload/publicación simulada
│   ├── engine/                    # Módulo Maven (Spring Boot): recibe y persiste lecturas MQTT, API REST, Controlador y acciona los switches
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/uy/edu/utec/iiss/engine/
│   │       │   │   ├── EngineApplication.java # Main de Spring Boot
│   │       │   │   ├── mqtt/              # LecturaMqttListener: se suscribe al broker, guarda cada lectura y se la pasa al Controlador
│   │       │   │   ├── rest/              # TODOS los endpoints HTTP (nada fuera de acá importa de rest)
│   │       │   │   │   ├── controller/    # HabitacionController (CRUD + comandos) y ControladorController (iniciar/parar/estado)
│   │       │   │   │   ├── dto/           # Requests/responses del API (formato JSON)
│   │       │   │   │   ├── security/      # ApiKeyFilter (autenticación por API key)
│   │       │   │   │   └── error/         # ApiExceptionHandler y ErrorResponse (mapeo de errores a HTTP)
│   │       │   │   ├── service/           # HabitacionService (CRUD y comandos), LecturaService (guarda lecturas), ControladorService (termostato simple: accionar switch) y DatosHabitacion
│   │       │   │   ├── model/             # Entidades JPA Habitacion y Lectura, enum AccionSwitch (ON/OFF) y ReporteConsistencia
│   │       │   │   ├── repository/        # HabitacionRepository y LecturaRepository (Spring Data JPA)
│   │       │   │   ├── client/            # SwitchStubClient: cliente REST hacia el switch-stub
│   │       │   │   ├── config/            # RestTemplateConfig
│   │       │   │   └── exception/         # Excepciones de dominio (sin nada de HTTP)
│   │       │   └── resources/
│   │       │       ├── application.properties # Puerto, datasource, Flyway, broker MQTT, URL del stub y API key
│   │       │       └── db/migration/      # Migraciones Flyway: V1 esquema (habitaciones, lecturas), V2 habitaciones iniciales
│   └── switch-stub/               # Módulo Maven (Spring Boot): stub REST de un switch (solo loguea la acción)
│       ├── Dockerfile
│       ├── pom.xml
│       └── src/main/java/switchstub/  # SwitchController (POST /switch), SwitchAccionRequest y Accion
├── logs/                          # Log del engine y del switch-stub (montado como volumen; ignorado por git)
├── pom.xml                        # POM padre (agrupa los módulos core, eventGenerator, engine y switch-stub)
└── README.md
```

---

## Arquitectura y flujo de datos

```
eventGenerator ──(MQTT publish)──▶ mosquitto ──(MQTT subscribe)──▶ engine ──(JPA)──▶ PostgreSQL
  simula 3 termostatos              broker       tópico:           │   guarda lecturas; Flyway
  Shelly (living, dormitorio,     puerto 1883   +/status/          │   gestiona el esquema
  cocina), publica cada 10s                     temperature:0      │
                                                                   └──(REST)──▶ switch-stub
                                                     Controlador: ON/OFF según temperatura esperada
          cliente HTTP ──(REST + X-API-KEY)──▶ engine :8080
```

1. **`eventGenerator`** simula 3 termostatos Shelly H&T (`shellyhtg3-...`) y publica cada 10
   segundos un payload `{"id":0,"tC":21.4,"tF":70.5,"ts":1786840680.123}` al tópico
   `<deviceId>/status/temperature:0`.
2. **`mosquitto`** distribuye esos mensajes a cualquier suscriptor conectado al tópico
   `+/status/temperature:0`.
3. **`engine`** (clase `LecturaMqttListener`) está suscripto al tópico desde que arranca. Por cada
   mensaje busca a qué habitación pertenece el `deviceId` (tabla `habitaciones`, precargada
   por seed) y, si está asignado, guarda la lectura en la tabla `lecturas`. Si el dispositivo
   no está asignado a ninguna habitación, descarta el mensaje. Cada paso queda registrado
   (consola + archivo `logs/engine.log`). Las lecturas se guardan siempre, esté o no iniciado
   el Controlador.
4. **`engine`** (antes `api`, luego `core`) además expone el CRUD de habitaciones y los comandos del sistema
   por HTTP (puerto `8080`), protegido por API key. El esquema de la base lo crea y versiona
   **Flyway** al arrancar (`db/migration`); Hibernate no lo toca (`ddl-auto=none`).
5. **Controlador** (dentro del `engine`): cuando se lo inicia (`POST /controlador/iniciar`) empieza
   a reaccionar a cada lectura que le entrega el listener y se comporta como un **termostato
   simple**: compara la temperatura medida con la `temperaturaEsperada` de la habitación dueña
   del termostato — si la medida es **mayor**, apaga el switch (`OFF`); en otro caso, lo prende
   (`ON`) — y acciona el switch contra el `switch-stub`. Con `POST /controlador/parar` deja de
   accionar, pero las lecturas se siguen guardando.
6. **`switch-stub`** simula el switch físico: recibe `POST /switch` con `{"switchId": "...",
   "accion": "ON|OFF"}` y solo registra la acción en su log.

### Vocabulario: engine y core

Dos palabras que se usan con un sentido preciso (vienen de la letra y del estándar de la materia):

- **engine** (módulo `modules/engine`): todo lo que habla con el mundo exterior. Recibe MQTT, guarda en
  Postgres, expone el REST, dispara los eventos temporales y ejecuta las órdenes contra los switches.
  Traduce el mundo real a "estímulos" para el core, y las decisiones del core a comandos.
- **core** (módulo `modules/core`, paquete `uy.edu.utec.iiss.core`): la lógica de decisión pura. Recibe estímulos
  (`NuevaLectura`, `Tick`, `ConfiguracionActualizada`) y devuelve una decisión `ON`/`OFF` por habitación,
  respetando la potencia contratada, la tarifa punta y un criterio de prioridad no azaroso. No lee
  MQTT, ni REST, ni la base: por eso es un módulo Maven aparte, sin dependencias de producción (solo
  JUnit para los tests), así el compilador impide que importe algo del engine o de Spring.

Estado actual: el `engine` ya declara la dependencia al módulo `core`, pero el core **todavía no está
conectado** a su código. Hoy `ControladorService` usa un
termostato simple (más alta que la esperada apaga, más baja prende). Conectarlos requiere, entre
otras cosas, `potenciaKW` por habitación y los datos del sitio (potencia contratada y franja punta).

### Modelo de datos (Postgres)

- **`habitaciones`**: `id`, `nombre` (único), `termostato_id` (único), `switch_id` (único),
  `temperatura_objetivo`. Se precarga con 3 habitaciones de ejemplo (living, dormitorio, cocina)
  si la tabla está vacía — simula la configuración que haría el cliente al instalar los
  dispositivos. En el API, estas columnas se exponen como `nombre`, `idTermostato`, `idSwitch`
  y `temperaturaEsperada`.
- **`lecturas`**: `id`, `habitacion_id` (FK a `habitaciones`), `temperatura_c`, `temperatura_f`,
  `epoch_mili` (instante de la lectura en epoch milisegundos, UTC).

---

## Cómo compilar y levantar el sistema

No hace falta tener Java, Maven ni IntelliJ instalados — todo el build ocurre dentro de
contenedores Docker (multi-stage: build con Maven, ejecución con JRE).

```bash
./scripts/up.sh
```

Esto compila los cuatro módulos (`core`, `eventGenerator`, `engine` y `switch-stub`) y levanta
los 5 servicios definidos en `docker/docker-compose.yml`:

| Servicio | Rol | Puerto |
|---|---|---|
| `mosquitto` | Broker MQTT | `1883` |
| `postgres` | Base de datos (crea `iiss2026`, usuario/clave `root`/`root`) | `5432` |
| `eventgenerator` | Simula los 3 termostatos y publica lecturas cada 10s | — |
| `engine` | Recibe y persiste las lecturas MQTT, API REST (CRUD de habitaciones + comandos + Controlador) protegida con API key; loguea en `logs/engine.log` (montado como volumen) | `8080` |
| `switch-stub` | Stub REST del switch: recibe `POST /switch` y loguea la acción | `8081` |

Para ver en vivo lo que va recibiendo y persistiendo el engine (y las acciones del Controlador):
```bash
./scripts/receive-temp.sh
```
(sigue el archivo `logs/engine.log` en el host — no depende de que el contenedor siga vivo
en el momento de leerlo).

Para ver las acciones que el Controlador manda a los switches:
```bash
docker logs -f switch-stub
```

Para publicar manualmente una lectura de prueba:
```bash
./scripts/send-temp.sh 26.4
```

Para detener sin perder el build:
```bash
./scripts/stop.sh
```

Para bajar todo (contenedores + red; los datos de Postgres se conservan en el volumen `pgdata`):
```bash
./scripts/down.sh
```

### Variables de entorno
Configuradas en `docker/docker-compose.yml`, no requieren setup manual.

**`engine`**
- `DB_URL=jdbc:postgresql://postgres:5432/iiss2026`, `DB_USER=root`, `DB_PASSWORD=root`: conexión a Postgres.
- `MQTT_BROKER=tcp://mosquitto:1883`: broker al que se suscribe el engine para recibir las lecturas.
- `SWITCH_STUB_URL=http://switch-stub:8081`: dónde se accionan los switches.
- `API_KEY=ecowarm-grupo4-2026`: clave que deben enviar los clientes en el header `X-API-KEY`.
  Es una clave de desarrollo: para cualquier entorno real hay que cambiarla.
- `LOGGING_FILE_NAME=logs/engine.log`: archivo de log (dentro del contenedor, montado en `./logs` del host).

### Nota para quienes usan IntelliJ
Se puede abrir el proyecto en IntelliJ para editar y debuggear localmente. Esto es opcional y
solo para desarrollo — **no es necesario** compilar desde el IDE para que el sistema funcione;
`scripts/build.sh` es la vía oficial de build.

---

## API REST

El contrato completo está en [`docs/api/openapi.yaml`](docs/api/openapi.yaml). Base URL local:
`http://localhost:8080`.

### Autenticación
Todos los endpoints requieren el header `X-API-KEY` con el valor de la variable `API_KEY`. Sin
el header (o con un valor incorrecto) el API responde `401`.

```bash
curl -H "X-API-KEY: ecowarm-grupo4-2026" http://localhost:8080/habitaciones
```

### Endpoints

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/habitaciones` | Lista las habitaciones | `200` |
| `POST` | `/habitaciones` | Crea una habitación | `201` (+ `Location`), `400`, `409` |
| `GET` | `/habitaciones/{id}` | Consulta una habitación | `200`, `404` |
| `PUT` | `/habitaciones/{id}` | Modificación completa (todos los campos obligatorios) | `200`, `400`, `404`, `409` |
| `PATCH` | `/habitaciones/{id}` | Modificación parcial (solo los campos enviados) | `200`, `400`, `404`, `409` |
| `DELETE` | `/habitaciones/{id}` | Elimina una habitación | `204`, `404` |
| `GET` | `/habitaciones/validar` | Comando: valida que no haya `idTermostato`/`idSwitch` duplicados | `200` |
| `POST` | `/habitaciones/{id}/switch` | Comando: acciona a mano el switch de la habitación (`{"accion":"ON"}` u `OFF`) | `200`, `400`, `404`, `502` |
| `POST` | `/controlador/iniciar` | Comando: inicia el Controlador (empieza a accionar los switches según las lecturas) | `200`, `409` si ya estaba iniciado |
| `POST` | `/controlador/parar` | Comando: detiene el Controlador | `200`, `409` si no estaba iniciado |
| `GET` | `/controlador/estado` | Consulta si el Controlador está corriendo | `200` |

Todas las respuestas pueden devolver `401` si falta la API key. Los errores tienen siempre el
mismo formato (`ErrorResponse`: código HTTP + mensaje, y el detalle por campo en los `400`).
`409` indica nombre, `idTermostato` o `idSwitch` repetidos; `502` indica que el `switch-stub` no está disponible.

Body de ejemplo para crear una habitación:
```json
{
  "nombre": "Dormitorio principal",
  "temperaturaEsperada": 21.5,
  "idTermostato": "shellyhtg3-a1b2c3d4e5f6",
  "idSwitch": "shellypro1pm-30c6f780e918"
}
```

### Ejemplo completo
Con el sistema levantado (`./scripts/up.sh`), este script crea una habitación de prueba, recorre
todos los endpoints, la elimina y por último muestra el `401` sin credencial:
```bash
./scripts/api-demo.sh                # usa la API key por defecto
./scripts/api-demo.sh otra-clave     # si se cambió API_KEY en docker-compose.yml
```

Para ver el Controlador en acción: iniciarlo con `POST /controlador/iniciar` y mirar
`docker logs -f switch-stub`; cada lectura que publique el `eventGenerator` (o
`./scripts/send-temp.sh`) de un termostato asignado a una habitación produce un `ON`/`OFF`.

---

## Tests

Los tests unitarios (JUnit 5 + Mockito) cubren `Habitacion` del `eventGenerator`, y el core de decisión
(`core.Core`, módulo `core`) tiene sus tests en `CoreTest` (TDD). El resto del `engine` y `switch-stub` todavía no
tiene tests automatizados; se verifican con `scripts/api-demo.sh`. Los tests corren dentro de
Docker, sin necesitar Maven ni el JDK instalados en el host:

```bash
./scripts/test.sh                  # corre los tests de todos los módulos
./scripts/test.sh core             # corre solo los tests del core (CoreTest)
./scripts/test.sh engine           # corre solo los tests de engine
./scripts/test.sh eventGenerator   # corre solo los tests de eventGenerator
```

---

## Scripts

| Script | Qué hace | Uso |
|---|---|---|
| `build.sh` | Compila los módulos Maven dentro de Docker (sin depender de Java/Maven instalados en el host). | `./scripts/build.sh` |
| `up.sh` | Ejecuta `build.sh` y levanta los 5 servicios en segundo plano. | `./scripts/up.sh` |
| `down.sh` | Baja los servicios y elimina contenedores y red (el volumen `pgdata` se conserva). | `./scripts/down.sh` |
| `stop.sh` | Detiene los contenedores sin eliminarlos (se retoma con `up.sh`, sin recompilar). | `./scripts/stop.sh` |
| `test.sh` | Corre los tests unitarios (`mvn test`) vía Docker, de todos los módulos o de uno en particular. | `./scripts/test.sh [core\|engine\|eventGenerator]` |
| `receive-temp.sh` | Sigue en vivo `logs/engine.log` (log real del engine, leído del host). | `./scripts/receive-temp.sh` |
| `send-temp.sh` | Publica una lectura de prueba con `curl` al tópico real (`shellyhtg3-.../status/temperature:0`). | `./scripts/send-temp.sh [temperatura]` (default `22.5`) |
| `api-demo.sh` | Ejemplo de uso completo del API con `curl` (CRUD, comandos, Controlador y chequeo de autenticación). | `./scripts/api-demo.sh [api-key]` |

> Todos los scripts se ejecutan desde la **raíz del repositorio**.

---

## Licencias de terceros

Este proyecto usa software, herramientas de infraestructura y librerías de código abierto de
terceros, sujetas a sus respectivas licencias:

- **Docker (Docker Engine)** — Apache License 2.0. Contenedorización y despliegue.
- **Eclipse Paho (mqttv3)** — Eclipse Public License 2.0 (EPL-2.0). Cliente MQTT.
- **Apache Maven** — Apache License 2.0. Build y gestión de dependencias.
- **Eclipse Mosquitto** — EPL-2.0 / Eclipse Distribution License 1.0 (EDL-1.0). Broker MQTT.
- **PostgreSQL JDBC Driver (org.postgresql:postgresql)** — BSD 2-Clause License. Conexión a la
  base de datos.
- **org.json** — licencia JSON ("no usar para hacer el mal"). Armado de payloads MQTT en el `eventGenerator`.
- **Logback (logback-classic)** — Eclipse Public License 1.0 / GNU LGPL 2.1. Logging del
  `switch-stub` a consola y archivo.
- **Flyway (flyway-core)** — Apache License 2.0. Migraciones del esquema del `engine`.
- **Spring Boot (web, data-jpa, validation)** — Apache License 2.0. Framework del `engine` y del
  `switch-stub`.
- **Hibernate ORM** — GNU LGPL 2.1. Implementación de JPA usada por `spring-boot-starter-data-jpa`.
- **Jackson** — Apache License 2.0. Serialización JSON del `engine`.
- **JUnit 5 (junit-jupiter)** — Eclipse Public License 2.0. Framework de tests unitarios.
- **Mockito** — MIT License. Mocking en los tests unitarios.

Todas estas licencias permiten su uso en un proyecto MIT (las de copyleft débil, como LGPL y EPL,
solo aplican a modificaciones de la propia librería). Ver [`LICENSE`](LICENSE).
