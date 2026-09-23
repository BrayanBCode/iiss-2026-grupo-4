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
│   ├── docker-compose.yml        # Orquesta mosquitto, postgres, subscriber, eventgenerator, api y switch-stub
│   └── mosquitto.conf            # Config del broker (listener 1883, anónimo habilitado)
├── scripts/
│   ├── build.sh                  # Compila todo el sistema vía Docker
│   ├── up.sh                     # Compila y levanta el sistema completo
│   ├── down.sh                   # Baja y elimina los contenedores y la red (los datos de Postgres se conservan en el volumen pgdata)
│   ├── stop.sh                   # Detiene los contenedores sin eliminarlos
│   ├── test.sh                   # Corre los tests unitarios (mvn test) vía Docker, de todos los módulos o uno solo
│   ├── send-temp.sh              # Publica una lectura de prueba en el tópico MQTT real (curl)
│   ├── receive-temp.sh           # Sigue en vivo el archivo de log del subscriber (logs/subscriber.log)
│   └── api-demo.sh               # Recorre todos los endpoints del API con curl (incluye la API key)
├── modules/
│   ├── subscriber/                # Módulo Maven: consume MQTT y persiste en Postgres
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/subscriber/
│   │       │   ├── AppController.java     # Main: conecta al broker, se suscribe y persiste lecturas (loggea con SLF4J/Logback)
│   │       │   ├── ConexionDB.java        # Conexión JDBC a Postgres (singleton, vía variables de entorno)
│   │       │   ├── Habitacion.java        # Record: id, nombre, temperaturaObjetivo
│   │       │   ├── HabitacionDAO.java     # Tabla "habitaciones": creación, seed y búsqueda por termostato_id
│   │       │   └── LecturaDAO.java        # Tabla "lecturas": alta y guardado de cada lectura recibida
│   │       ├── main/resources/
│   │       │   └── logback.xml            # Config de logging: consola + archivo logs/subscriber.log
│   │       └── test/java/subscriber/      # Tests unitarios (JUnit 5 + Mockito) de Habitacion, HabitacionDAO y LecturaDAO
│   ├── eventGenerator/            # Módulo Maven: simula los termostatos Shelly publicando por MQTT
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/eventGenerator/
│   │       │   ├── AppEventGenerator.java # Main: crea 3 habitaciones simuladas y publica cada 10s
│   │       │   └── Habitacion.java        # Simula un termostato Shelly H&T (payload JSON con tC/tF/ts)
│   │       └── test/java/eventGenerator/  # Tests unitarios (JUnit 5 + Mockito) del payload/publicación simulada
│   ├── api/                       # Módulo Maven (Spring Boot): API REST + Controlador (Iteración 3)
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/main/
│   │       ├── java/api/
│   │       │   ├── ApiApplication.java    # Main de Spring Boot
│   │       │   ├── controller/            # HabitacionController (CRUD + comandos) y ControladorController (iniciar/parar/estado)
│   │       │   ├── service/               # HabitacionService: lógica de negocio del CRUD y de los comandos
│   │       │   ├── controlador/           # ControladorService: termostato simple (suscripción MQTT + accionar switch)
│   │       │   ├── client/                # SwitchStubClient: cliente REST hacia el switch-stub
│   │       │   ├── config/                # ApiKeyFilter (autenticación por API key) y RestTemplateConfig
│   │       │   ├── model/                 # Entidad JPA Habitacion y enum AccionSwitch (ON/OFF)
│   │       │   ├── repository/            # HabitacionRepository (Spring Data JPA)
│   │       │   ├── dto/                   # Requests/responses del API
│   │       │   └── exception/             # Excepciones de dominio y ApiExceptionHandler (mapeo a HTTP)
│   │       └── resources/
│   │           └── application.properties # Puerto, datasource, broker MQTT, URL del stub y API key
│   └── switch-stub/               # Módulo Maven (Spring Boot): stub REST de un switch (solo loguea la acción)
│       ├── Dockerfile
│       ├── pom.xml
│       └── src/main/java/switchstub/  # SwitchController (POST /switch), SwitchAccionRequest y Accion
├── logs/                          # Logs del subscriber (montado como volumen; ignorado por git)
├── pom.xml                        # POM padre (agrupa los módulos subscriber, eventGenerator, api y switch-stub)
└── README.md
```

---

## Arquitectura y flujo de datos

```
                          ┌──────────────────────── persistencia (Iteración 2) ────────────────────────┐
eventGenerator ──(MQTT publish)──▶ mosquitto ──(MQTT subscribe)──▶ subscriber ──▶ PostgreSQL
  simula 3 termostatos              broker            tópico:        persiste en   ▲
  Shelly (living, dormitorio,     puerto 1883    +/status/temperature:0   habitaciones / lecturas   │
  cocina), publica cada 10s             │                                                            │
                                        │                                                            │
                                        └──(MQTT subscribe)──▶ api (Controlador) ──(REST)──▶ switch-stub
                                                                  │  ON/OFF según temperatura esperada
                                                                  └──(JPA)─────────────────────────────┘
                                          cliente HTTP ──(REST + X-API-KEY)──▶ api :8080
```

1. **`eventGenerator`** simula 3 termostatos Shelly H&T (`shellyhtg3-...`) y publica cada 10
   segundos un payload `{"id":0,"tC":21.4,"tF":70.5,"ts":1786840680.123}` al tópico
   `<deviceId>/status/temperature:0`.
2. **`mosquitto`** distribuye esos mensajes a cualquier suscriptor conectado al tópico
   `+/status/temperature:0`.
3. **`subscriber`** (clase `AppController`) recibe el mensaje, busca a qué habitación
   pertenece el `deviceId` (tabla `habitaciones`, precargada por seed) y, si está asignado,
   guarda la lectura en la tabla `lecturas`. Si el dispositivo no está asignado a ninguna
   habitación, descarta el mensaje. Cada paso queda registrado (consola + archivo) vía
   SLF4J/Logback. Además, es quien crea las tablas al arrancar.
4. **`api`** expone el CRUD de habitaciones y los comandos del sistema por HTTP (puerto `8080`),
   protegido por API key. Lee y escribe sobre la misma tabla `habitaciones` que usa el
   `subscriber` (no toca el esquema: `ddl-auto=none`).
5. **Controlador** (dentro del `api`): cuando se lo inicia (`POST /controlador/iniciar`) se
   suscribe al mismo tópico MQTT y se comporta como un **termostato simple**: por cada lectura,
   compara la temperatura medida con la `temperaturaEsperada` de la habitación dueña del
   termostato — si la medida es **mayor**, apaga el switch (`OFF`); en otro caso, lo prende
   (`ON`) — y acciona el switch contra el `switch-stub`. Si el termostato no está asignado a
   ninguna habitación (o la habitación no tiene temperatura esperada), descarta la lectura.
6. **`switch-stub`** simula el switch físico: recibe `POST /switch` con `{"switchId": "...",
   "accion": "ON|OFF"}` y solo registra la acción en su log.

### Modelo de datos (Postgres)

- **`habitaciones`**: `id`, `nombre` (único), `termostato_id` (único), `switch_id` (único),
  `temperatura_objetivo`. Se precarga con 3 habitaciones de ejemplo (living, dormitorio, cocina)
  si la tabla está vacía — simula la configuración que haría el cliente al instalar los
  dispositivos. En el API, estas columnas se exponen como `nombre`, `idTermostato`, `idSwitch`
  y `temperaturaEsperada`.
- **`lecturas`**: `id`, `habitacion_id` (FK a `habitaciones`), `temperatura_c`, `temperatura_f`,
  `fecha_hora`.

---

## Cómo compilar y levantar el sistema

No hace falta tener Java, Maven ni IntelliJ instalados — todo el build ocurre dentro de
contenedores Docker (multi-stage: build con Maven, ejecución con JRE).

```bash
./scripts/up.sh
```

Esto compila los cuatro módulos (`subscriber`, `eventGenerator`, `api` y `switch-stub`) y levanta
los 6 servicios definidos en `docker/docker-compose.yml`:

| Servicio | Rol | Puerto |
|---|---|---|
| `mosquitto` | Broker MQTT | `1883` |
| `postgres` | Base de datos (crea `iiss2026`, usuario/clave `root`/`root`) | `5432` |
| `eventgenerator` | Simula los 3 termostatos y publica lecturas cada 10s | — |
| `subscriber` | Se suscribe, persiste en Postgres, loguea en `logs/subscriber.log` (montado como volumen) | — |
| `api` | API REST (CRUD de habitaciones + comandos + Controlador), protegido con API key | `8080` |
| `switch-stub` | Stub REST del switch: recibe `POST /switch` y loguea la acción | `8081` |

Para ver en vivo lo que va recibiendo y persistiendo el subscriber:
```bash
./scripts/receive-temp.sh
```
(sigue el archivo `logs/subscriber.log` en el host — no depende de que el contenedor siga vivo
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

**`subscriber`**
- `DB_URL=jdbc:postgresql://postgres:5432/iiss2026`
- `DB_USER=root`
- `DB_PASSWORD=root`

**`api`**
- `DB_URL`, `DB_USER`, `DB_PASSWORD`: los mismos que el `subscriber` (comparten base).
- `MQTT_BROKER=tcp://mosquitto:1883`: broker al que se suscribe el Controlador.
- `SWITCH_STUB_URL=http://switch-stub:8081`: dónde se accionan los switches.
- `API_KEY=ecowarm-grupo4-2026`: clave que deben enviar los clientes en el header `X-API-KEY`.
  Es una clave de desarrollo: para cualquier entorno real hay que cambiarla.

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
| `POST` | `/controlador/iniciar` | Comando: inicia el Controlador (se suscribe a MQTT) | `200`, `409` si ya estaba iniciado, `502` |
| `POST` | `/controlador/parar` | Comando: detiene el Controlador | `200`, `409` si no estaba iniciado |
| `GET` | `/controlador/estado` | Consulta si el Controlador está corriendo | `200` |

Todas las respuestas pueden devolver `401` si falta la API key. Los errores tienen siempre el
mismo formato (`ErrorResponse`: código HTTP + mensaje, y el detalle por campo en los `400`).
`409` indica nombre, `idTermostato` o `idSwitch` repetidos; `502` indica que el broker MQTT o el
`switch-stub` no están disponibles.

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

Los tests unitarios (JUnit 5 + Mockito) cubren `Habitacion`, `HabitacionDAO` y `LecturaDAO` del
`subscriber`, y `Habitacion` del `eventGenerator`. Los módulos `api` y `switch-stub` todavía no
tienen tests automatizados; se verifican con `scripts/api-demo.sh`. Los tests corren dentro de
Docker, sin necesitar Maven ni el JDK instalados en el host:

```bash
./scripts/test.sh                  # corre los tests de todos los módulos
./scripts/test.sh subscriber       # corre solo los tests de subscriber
./scripts/test.sh eventGenerator   # corre solo los tests de eventGenerator
```

---

## Scripts

| Script | Qué hace | Uso |
|---|---|---|
| `build.sh` | Compila los módulos Maven dentro de Docker (sin depender de Java/Maven instalados en el host). | `./scripts/build.sh` |
| `up.sh` | Ejecuta `build.sh` y levanta los 6 servicios en segundo plano. | `./scripts/up.sh` |
| `down.sh` | Baja los servicios y elimina contenedores y red (el volumen `pgdata` se conserva). | `./scripts/down.sh` |
| `stop.sh` | Detiene los contenedores sin eliminarlos (se retoma con `up.sh`, sin recompilar). | `./scripts/stop.sh` |
| `test.sh` | Corre los tests unitarios (`mvn test`) vía Docker, de todos los módulos o de uno en particular. | `./scripts/test.sh [subscriber\|eventGenerator]` |
| `receive-temp.sh` | Sigue en vivo `logs/subscriber.log` (log real del subscriber, leído del host). | `./scripts/receive-temp.sh` |
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
- **org.json** — licencia JSON ("no usar para hacer el mal"). Parseo de payloads MQTT.
- **Logback (logback-classic)** — Eclipse Public License 1.0 / GNU LGPL 2.1. Logging del
  `subscriber` a consola y archivo.
- **Spring Boot (web, data-jpa, validation)** — Apache License 2.0. Framework del `api` y del
  `switch-stub`.
- **Hibernate ORM** — GNU LGPL 2.1. Implementación de JPA usada por `spring-boot-starter-data-jpa`.
- **Jackson** — Apache License 2.0. Serialización JSON del `api`.
- **JUnit 5 (junit-jupiter)** — Eclipse Public License 2.0. Framework de tests unitarios.
- **Mockito** — MIT License. Mocking en los tests unitarios.

Todas estas licencias permiten su uso en un proyecto MIT (las de copyleft débil, como LGPL y EPL,
solo aplican a modificaciones de la propia librería). Ver [`LICENSE`](LICENSE).
