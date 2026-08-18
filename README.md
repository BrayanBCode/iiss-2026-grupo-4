# AgriFlow — Prototipo v1 (MQTT + Suscriptor Java)

Sistema de monitoreo IoT y automatización agrícola. Esta primera iteración levanta
un broker MQTT (Mosquitto) y un suscriptor Java mínimo que se conecta al broker,
se suscribe a un tópico e imprime en consola los mensajes recibidos.

Documento de Visión del producto: [`docs/vision.md`](docs/vision.md).
Proyecto en Jira: [`Enlace`](https://estudiantes-grupo8-2026.atlassian.net/jira/software/projects/G1234/boards/3?filter=&groupBy=none&atlOrigin=eyJpIjoiNjkxZmM3YjIyNjU2NGUyZmEwN2RiZjExMzc5MjEyMGYiLCJwIjoiaiJ9)

---

## Estructura de los fuentes

```
.
├── docs/
│   └── vision.md              # Visión del producto v1 (plantilla Moore + análisis de dispositivos)
├── docker/
│   ├── docker-compose.yml     # Orquesta el broker Mosquitto y el subscriber Java
│   ├── Dockerfile             # Build multi-stage del subscriber (compila   git add -A
   git commit -m "Iteración 1: vision.md, README, build reproducible"
   git tag version1
   git push origin master --tags con javac dentro de Docker)
│   └── mosquitto.conf         # Configuración del broker (listener 1883, anónimo habilitado)
├── scripts/
│   ├── build.sh                # Compila todo el sistema vía Docker (sin JDK/Maven/IntelliJ local)
│   ├── up.sh                   # Compila (build.sh) y levanta el sistema
│   ├── down.sh                 # Baja y elimina contenedores/red
│   ├── stop.sh                 # Detiene los contenedores sin eliminarlos
│   ├── send-temp.sh            # Publica una temperatura en el tópico MQTT (curl)
│   └── receive-temp.sh         # Muestra en vivo los mensajes recibidos por el subscriber Java
├── src/
│   └── subscriber/
│       └── AppSubscriber.java   # Suscriptor MQTT: se conecta al broker, se suscribe e imprime en consola
├── lib/
│   └── org.eclipse.paho.client.mqttv3-1.2.5.jar   # Dependencia del cliente MQTT (Eclipse Paho)
└── README.md
```

El proyecto **no usa Maven**: la dependencia de Paho es un `.jar` local en `lib/`
(referenciado también como librería del módulo de IntelliJ, para poder editar y
correr el código desde el IDE). La compilación real para levantar el sistema, sin
embargo, ocurre siempre vía Docker — ver la sección siguiente.

---

## Scripts: qué hace cada uno y cómo se usa

| Script | Qué hace | Uso |
|---|---|---|
| `build.sh` | Compila el subscriber Java **dentro de Docker** (etapa `build` del `Dockerfile`, con `javac`), sin depender de Java/Maven/IntelliJ instalados en el host. | `./scripts/build.sh` |
| `up.sh` | Ejecuta `build.sh` y luego levanta ambos servicios (`mosquitto` + `subscriber`) en segundo plano. | `./scripts/up.sh` |
| `down.sh` | Baja los servicios y elimina contenedores y red. | `./scripts/down.sh` |
| `stop.sh` | Detiene los contenedores sin eliminarlos (se retoma con `up.sh`, sin recompilar). | `./scripts/stop.sh` |
| `send-temp.sh` | Publica una temperatura en el tópico `ingSoft/informa` usando `curl` (soporte MQTT nativo de curl ≥ 7.85). | `./scripts/send-temp.sh [temperatura]` (default: `22.5`) |
| `receive-temp.sh` | Muestra en vivo los logs del subscriber Java, es decir, los mensajes que efectivamente está recibiendo del broker. | `./scripts/receive-temp.sh` |

> Todos los scripts se ejecutan desde la **raíz del repositorio** (usan rutas
> relativas tipo `docker/docker-compose.yml`).

---

## Cómo compilar y levantar el suscriptor Java

No hace falta tener Java, Maven ni IntelliJ instalados — todo el build ocurre dentro
de contenedores Docker (multi-stage: una etapa compila con `javac`, la otra corre
solo con el JRE).

```bash
./scripts/up.sh
```

Esto compila el subscriber y levanta:
- **mosquitto**: broker MQTT en el puerto `1883`.
- **subscriber**: el suscriptor Java, ya conectado y esperando mensajes en el tópico
  `ingSoft/informa`.

Para ver los logs del suscriptor en vivo:
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

### Nota para quienes usan IntelliJ
Podés seguir abriendo el proyecto en IntelliJ para editar y debuggear el código
localmente (la librería de Paho ya está configurada en `.idea/`). Eso es opcional y
solo para desarrollo — **no es necesario** compilar desde el IDE para que el sistema
funcione; `scripts/build.sh` es la única vía oficial de build del proyecto.

---

## Cómo ver el sistema en funcionamiento (cliente MQTT)

**Opción 1 — con los scripts del repo:**

Terminal 1 (dejar corriendo, muestra lo que recibe el subscriber Java):
```bash
./scripts/receive-temp.sh
```

Terminal 2 (publica un mensaje):
```bash
./scripts/send-temp.sh 26.4
```
Deberías ver en la Terminal 1 algo como:
```
📩 [MENSAJE RECIBIDO] Tópico: ingSoft/informa -> Temperatura actual: 26.4 °C
```

**Opción 2 — con un cliente MQTT externo (verificación cruzada), ej. MQTT Explorer:**

1. Descargar [MQTT Explorer](http://mqtt-explorer.com/).
2. Conectar a `localhost:1883` (sin usuario/contraseña, conexión anónima habilitada).
3. Suscribirse al tópico `ingSoft/informa`.
4. Publicar con `./scripts/send-temp.sh <valor>` y ver el mensaje llegar tanto en
   MQTT Explorer como en los logs del subscriber Java — confirma que el broker
   distribuye correctamente a todos los suscriptores.

---

## Proyecto Jira

https://estudiantes-grupo8-2026.atlassian.net/jira/software/projects/G1234/boards/3
