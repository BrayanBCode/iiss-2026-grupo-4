# EcoWarm — Automatización de calefacción por losa radiante (IoT)

Software de domótica para el hogar: recibe lecturas de temperatura de termostatos
Shelly H&T (vía MQTT), las persiste en PostgreSQL asociadas a la habitación
correspondiente, y sienta la base para automatizar la calefacción y el control de
consumo eléctrico según tarifas UTE multihorario.

Proyecto del curso **Taller IISS 2026** (Utec) — grupo 4.

Documento de Visión del producto: [`docs/vision.md`](docs/vision.md) — *nota: aún
describe la iteración anterior del proyecto ("AgriFlow", monitoreo agrícola); pendiente
de actualizar a la visión actual de EcoWarm (ver sección [Pendientes](#pendientes-de-documentación)).*

Historias de usuario, personas y escenarios: [`Historias.pdf`](docs/Producto/Historias.pdf),
[`Personas.pdf`](docs/Producto/Personas.pdf), [`Caracteristicas.pdf`](docs/Producto/Caracteristicas.pdf),
[`Escenario.pdf`](docs/Producto/Escenario.pdf), [`Prueba de escenarios.txt`](Prueba%20de%20escenarios.txt).

Proyecto en Jira: [`Enlace`](https://estudiantes-grupo8-2026.atlassian.net/jira/software/projects/G1234/boards/3) *(verificar: el link apunta a "grupo8")*

---

## Estructura del repositorio

```
.
├── docs/
│   ├── vision.md                 # Documento de visión (desactualizado, ver Pendientes)
│   └── Producto/                 # Documentación del producto (historias, personas, escenarios)
│       ├── Caracteristicas.pdf
│       ├── Escenario.pdf
│       ├── Historias.pdf
│       └── Personas.pdf
├── docker/
│   ├── docker-compose.yml        # Orquesta mosquitto, postgres, subscriber y eventgenerator
│   └── mosquitto.conf            # Config del broker (listener 1883, anónimo habilitado)
├── scripts/
│   ├── build.sh                  # Compila todo el sistema vía Docker
│   ├── up.sh                     # Compila y levanta el sistema completo
│   ├── down.sh                   # Baja y elimina contenedores, red y named volumes referenciados en el compose
│   ├── stop.sh                   # Detiene los contenedores sin eliminarlos
│   ├── send-temp.sh               # (desactualizado, ver Pendientes)
│   └── receive-temp.sh            # Muestra en vivo los logs del subscriber Java
├── src/
│   ├── subscriber/                # Módulo Maven: consume MQTT y persiste en Postgres
│   │   ├── Dockerfile
│   │   ├── pom.xml
│   │   └── src/main/java/subscriber/
│   │       ├── AppSubscriber.java     # Main: conecta al broker, se suscribe y persiste lecturas
│   │       ├── ConexionDB.java        # Conexión JDBC a Postgres (singleton, vía variables de entorno)
│   │       ├── Habitacion.java        # Record: id, nombre, temperaturaObjetivo
│   │       ├── HabitacionDAO.java     # Tabla "habitaciones": alta/seed y búsqueda por termostato_id
│   │       └── LecturaDAO.java        # Tabla "lecturas": alta y guardado de cada lectura recibida
│   └── eventGenerator/            # Módulo Maven: simula los termostatos Shelly publicando por MQTT
│       ├── Dockerfile
│       ├── pom.xml
│       └── src/main/java/eventGenerator/
│           ├── AppEventGenerator.java # Main: crea 3 habitaciones simuladas y publica cada 10s
│           └── Habitacion.java        # Simula un termostato Shelly H&T (payload JSON con tC/tF/ts)
├── lib/
│   └── org.eclipse.paho.client.mqttv3-1.2.5.jar   # Librería local (referenciada por IntelliJ; el build real usa Maven)
├── pom.xml                        # POM padre (agrupa los módulos subscriber y eventGenerator)
└── README.md
```

---

## Arquitectura y flujo de datos

```
eventGenerator ──(MQTT publish)──▶ mosquitto ──(MQTT subscribe)──▶ subscriber ──▶ PostgreSQL
  simula 3 termostatos              broker            tópico:        persiste en
  Shelly (living, dormitorio,     puerto 1883    +/status/temperature:0   habitaciones / lecturas
  cocina), publica cada 10s
```

1. **`eventGenerator`** simula 3 termostatos Shelly H&T (`shellyhtg3-...`) y publica cada 10
   segundos un payload `{"id":0,"tC":21.4,"tF":70.5,"ts":1786840680.123}` al tópico
   `<deviceId>/status/temperature:0`.
2. **`mosquitto`** distribuye esos mensajes a cualquier suscriptor conectado al tópico
   `+/status/temperature:0`.
3. **`subscriber`** recibe el mensaje, busca a qué habitación pertenece el `deviceId` (tabla
   `habitaciones`, precargada por seed) y, si está asignado, guarda la lectura en la tabla
   `lecturas`. Si el dispositivo no está asignado a ninguna habitación, descarta el mensaje.

### Modelo de datos (Postgres)

- **`habitaciones`**: `id`, `nombre` (único), `termostato_id` (único), `switch_id` (único),
  `temperatura_objetivo`. Se precarga con 3 habitaciones de ejemplo (living, dormitorio, cocina)
  si la tabla está vacía — simula la configuración que haría el cliente al instalar los
  dispositivos.
- **`lecturas`**: `id`, `habitacion_id` (FK a `habitaciones`), `temperatura_c`, `temperatura_f`,
  `fecha_hora`.

---

## Cómo compilar y levantar el sistema

No hace falta tener Java, Maven ni IntelliJ instalados — todo el build ocurre dentro de
contenedores Docker (multi-stage: build con Maven, ejecución con JRE).

```bash
./scripts/up.sh
```

Esto compila ambos módulos (`subscriber` y `eventGenerator`) y levanta 4 servicios definidos en
`docker/docker-compose.yml`:

| Servicio | Rol | Puerto |
|---|---|---|
| `mosquitto` | Broker MQTT | `1883` |
| `postgres` | Base de datos (crea `iiss2026`, usuario/clave `root`/`root`) | `5432` |
| `eventgenerator` | Simula los 3 termostatos y publica lecturas cada 10s | — |
| `subscriber` | Se suscribe, persiste en Postgres | — |

Para ver en vivo lo que va recibiendo y persistiendo el subscriber:
```bash
./scripts/receive-temp.sh
```

Para detener sin perder el build:
```bash
./scripts/stop.sh
```

Para bajar todo (contenedores + red):
```bash
./scripts/down.sh
```

### Variables de entorno del `subscriber`
Configuradas en `docker/docker-compose.yml`, no requieren setup manual:
- `DB_URL=jdbc:postgresql://postgres:5432/iiss2026`
- `DB_USER=root`
- `DB_PASSWORD=root`

### Nota para quienes usan IntelliJ
Se puede abrir el proyecto en IntelliJ para editar y debuggear localmente (la librería de Paho
en `lib/` está configurada como dependencia del módulo). Esto es opcional y solo para
desarrollo — **no es necesario** compilar desde el IDE para que el sistema funcione;
`scripts/build.sh` es la vía oficial de build.

---

## Scripts

| Script | Qué hace | Uso |
|---|---|---|
| `build.sh` | Compila ambos módulos Maven dentro de Docker (sin depender de Java/Maven instalados en el host). | `./scripts/build.sh` |
| `up.sh` | Ejecuta `build.sh` y levanta los 4 servicios en segundo plano. | `./scripts/up.sh` |
| `down.sh` | Baja los servicios y elimina contenedores y red. | `./scripts/down.sh` |
| `stop.sh` | Detiene los contenedores sin eliminarlos (se retoma con `up.sh`, sin recompilar). | `./scripts/stop.sh` |
| `receive-temp.sh` | Muestra en vivo los logs del subscriber Java (lecturas recibidas y persistidas). | `./scripts/receive-temp.sh` |
| `send-temp.sh` | *(desactualizado — ver [Pendientes](#pendientes-de-documentación))* | — |

> Todos los scripts se ejecutan desde la **raíz del repositorio**.

---

## Pendientes de documentación

- `docs/vision.md` describe la versión anterior del producto ("AgriFlow", monitoreo agrícola);
  falta reescribirla para reflejar EcoWarm (domótica hogareña / calefacción por losa radiante).
- `scripts/send-temp.sh` publica al tópico `ingSoft/informa` con `curl`, que no coincide con el
  tópico real que escucha el `subscriber` (`+/status/temperature:0`, en formato JSON). Como
  `eventGenerator` ya publica automáticamente lecturas simuladas, quizás este script ya no haga
  falta — o haya que actualizarlo para publicar en el formato/tópico correcto.
- El link a Jira apunta a un proyecto de "grupo8"; confirmar si es el correcto para grupo 4.

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

Todas estas licencias son permisivas y compatibles con la licencia MIT del proyecto (ver
[`LICENSE`](LICENSE)).
