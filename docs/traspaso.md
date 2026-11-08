# Guía de traspaso

Para el equipo que continúe el desarrollo del controlador EcoWarm. En una tarde se puede levantar
el proyecto, correr los tests, verlo controlar contra el verificador de la cátedra y saber dónde
tocar cada cosa. El estado al cerrar la iteración 6 está en el [README](../README.md); lo que falta
y por dónde seguir, en [limitaciones y roadmap](arquitectura/roadmap.md).

## 1. El proyecto en un párrafo

Un controlador de calefacción eléctrica por habitación. Recibe la temperatura de cada termostato por
MQTT, decide qué habitaciones reciben energía (sin superar la potencia contratada, cortando en la
franja punta y con prioridad para la que tiene más déficit) y comanda los switches por REST. Habla el
[estándar de interoperabilidad v2](api/interop-v2.md), así que funciona con el simulador de la
cátedra, con el verificador `ecowarm-check` o con dispositivos reales. Son dos módulos Maven:
`core` (la decisión, Java puro) y `engine` (todo lo demás, Spring Boot).

## 2. El primer día

| # | Paso | Qué tiene que pasar |
|---|---|---|
| 1 | Instalar Docker (con Compose v2) y, para trabajar en el código, JDK 25 y Maven 3.9 | `docker --version`, `java -version` (25), `mvn -version` |
| 2 | Clonar el repo y, desde la raíz, `mvn clean verify` | `BUILD SUCCESS`: formato, todos los tests y cobertura del core ≥ 85 %. Tarda unos minutos por los tests de integración |
| 3 | Instalar en IntelliJ el plugin de google-java-format (estilo AOSP) | [`calidad/estandares-codificacion.md`](calidad/estandares-codificacion.md) |
| 4 | En una terminal: `java -jar ecowarm-check.jar --anunciar host.docker.internal --durante 240` | El checker espera que el controlador se registre |
| 5 | En otra: `./scripts/up.sh` | El contenedor `ecowarm-grupo4` arranca, se registra y empieza a comandar switches |
| 6 | A los 240 s, el veredicto del checker | `8/8 · el controlador se comunica como manda el estándar` |
| 7 | Mientras corre: `docker logs -f ecowarm-grupo4` y `curl -H "X-API-Key: ecowarm-grupo4-2026" localhost:8080/diagnostico` | Decisiones con su motivo, comandos a switches y un resumen cada 30 s |

Si algo de esto no pasa, la [sección 6](#6-problemas-conocidos-y-cómo-resolverlos) tiene las causas
que ya encontramos.

## 3. Mapa del código: dónde tocar cada cosa

| Si hay que cambiar… | Dónde | Tests que lo cubren |
|---|---|---|
| Qué habitación prende y por qué | `core/Core.decidir` | `DecidirTest` |
| El criterio de prioridad | `core/MayorDeficitPrimero` (implementa `CriterioPrioridad`) | `MayorDeficitPrimeroTest` |
| Histéresis, vigencia, rango válido de temperatura | `core/model/ParametrosControl` y `application.properties` | `DecidirTest` |
| Cómo se aplica una lectura, un tick, un INICIAR | `engine/adaptador/CoreAdapter.procesar` | `CoreAdapterEstimulosTest` |
| Cuándo y cómo se comandan los switches | `engine/adaptador/CoreAdapter` (pasadas con los sanos primero, OFF antes que ON, reconciliación) y `engine/switches/ClienteSwitchRest` (timeouts escalados con el factor) | `CoreAdapterTest`, `FallaSwitchTest`, `ClienteSwitchRestTest`, `EstresTest` |
| La hora del control, el `ts`, el tick | `engine/control/RelojControl`, `ControlService` | `RelojControlTest`, `ControlServiceTest`, `TiempoAceleradoTest` |
| La conexión MQTT y el parseo de las lecturas | `engine/mqtt/LecturaMqttListener`, `PayloadTermostato` | `FallaBrokerMqttTest`, `PerdidaMensajesTest`, `PayloadTermostatoTest` |
| El registro ante el entorno (§6) | `engine/registro/RegistroEntorno` | `RegistroEntornoTest` |
| El API REST y la clave | `engine/rest/` (controllers, `ApiKeyFilter`, errores) | `ApiEstandarTest` |
| El inventario, sus reglas y su persistencia | `engine/inventario/` | `ValidadorInventarioTest`, `RepositorioInventarioTest` |
| Llamadas a los switches | `engine/switches/ClienteSwitchRest` | `CoreAdapterTest`, `FallaSwitchTest` |

Los diagramas de cómo encajan estas piezas (clases, secuencias, hilos, despliegue) están en el
[4+1](arquitectura/4mas1.md); el porqué de cada diseño, en [`decisiones.md`](arquitectura/decisiones.md).

## 4. Cómo hacer un cambio

1. Tomar la tarjeta del [Jira](https://estudiantes-grupo8-2026.atlassian.net/jira/software/projects/G1234/boards/3)
   y crear una rama `<iteración>/<id>-<tema>` desde la rama de la iteración.
2. Escribir primero el test que muestra el problema o el comportamiento nuevo. Para la decisión, en
   `DecidirTest`; para algo de punta a punta, un test de integración que extienda `IntegracionBase`
   (levanta el engine con un broker MQTT y switches falsos dentro del proceso).
3. `mvn spotless:apply` y `mvn clean verify` desde la raíz.
4. Actualizar la documentación afectada (4+1, decisiones, roadmap, README) en la misma rama.
5. PR hacia la rama de la iteración; la CI corre `mvn -B verify`. Lo revisa otro integrante.

El método completo (tablero, límites de trabajo en curso, definición de hecho) está en
[`planificacion/metodologia-elegida.md`](planificacion/metodologia-elegida.md).

## 5. Probar contra el simulador de la cátedra

### Con `ecowarm-run` (escenarios firmados, en la misma máquina)

El simulador oficial por comando: trae el simulador, el broker y los switches, corre escenarios
firmados contra el controlador y deja un reporte en `ecowarm-reporte/reporte.md` (necesita Java 21 o
más nuevo). El `ecowarm-run.jar` lo entrega la cátedra: no está en el repo. Solo corre escenarios
firmados; los propios se prueban con los tests de integración (`EscenariosSimuladorTest`).

```bash
# 1. el controlador, apuntando al simulador local (clave del simulador: iiss-2026)
ENTORNO_URL=http://localhost:9090 ENTORNO_API_KEY=iiss-2026 CONTROLADOR_BASE_URL=http://localhost:8080 java -jar modules/engine/target/engine.jar
# 2. los escenarios (todos tardan unos 18 min; --mqtt 1884 si el 1883 está ocupado)
java -jar ecowarm-run.jar todos
java -jar ecowarm-run.jar listar     # qué trae
java -jar ecowarm-run.jar ver E11    # el YAML de un escenario: sitio, fallas y expectativas
```

Si el controlador corre en Docker, agregar `--host host.docker.internal`. Sale con 0 si pasan todos.
La última corrida (9 de 9) y cómo se corrigió lo que fallaba están en
[`tests/corrida-simulador/2026-11-06-ecowarm-run/`](tests/corrida-simulador/2026-11-06-ecowarm-run/README.md).

### Con el simulador del laboratorio (otra máquina)

1. Averiguar la IP del simulador, su clave y la IP de **esta** máquina en esa red (`ipconfig`).
2. Levantar el controlador con esas tres:
   ```bash
   ENTORNO_URL=http://<ip-simulador>:<puerto> ENTORNO_API_KEY=<clave> CONTROLADOR_BASE_URL=http://<mi-ip>:8080 docker compose -f docker/docker-compose.yml up -d --build
   ```
   O en la red Docker del simulador: `ENTORNO_API_KEY=<clave> ./scripts/up.sh lab`.
3. El broker no se configura: lo informa el simulador al registrarse.
4. Capturar la evidencia de cada escenario con `./scripts/captura-simulador.sh <escenario>` y
   compararla con lo esperado en [`tests/corrida-simulador/`](tests/corrida-simulador/README.md),
   donde también están las corridas anteriores y qué test cubre cada escenario.

## 6. Problemas conocidos y cómo resolverlos

Todos aparecieron en corridas reales del equipo.

| Síntoma | Causa | Qué hacer |
|---|---|---|
| El registro falla con `401 Unauthorized` | Falta la clave del entorno | `ENTORNO_API_KEY=<clave del simulador>` |
| Se registra pero nunca llega el `PUT /sitio` ni el INICIAR | `CONTROLADOR_BASE_URL` no es alcanzable desde el entorno (`localhost`, otra IP o una IP mal escrita) | Poner la IP de esta máquina en la red del simulador. Con `localhost` o una IP inválida el controlador lo avisa al arrancar |
| Con el controlador en Docker y el checker, errores de conexión a `localhost:1884` o a `localhost:9090/switches` | El checker anunció `localhost`, que dentro del contenedor es el propio contenedor | Correr el checker con `--anunciar host.docker.internal` |
| El checker no arranca: el puerto 1883 está ocupado | Otro broker (por ejemplo, un Mosquitto local) | `--broker 1884` |
| Con el checker no prende nada aunque haga frío | El checker manda la hora real; entre las 17:00 y las 23:00 de un día hábil es franja punta (`TARIFA_PUNTA`) | Es correcto. Para ver ON y OFF, probar fuera de ese horario |
| Todas las lecturas se descartan como `TS_VENCIDO` | El `ts` llega en una escala que no se reconoce | Desde T24b se aceptan las dos escalas (hora real y hora del control). Si vuelve a pasar, mirar en el log la línea del descarte: muestra el `ts` y la hora del control |
| Errores de switch con `text/plain`, `respuesta sin 'estado' válido` o timeouts | El escenario de switch caído del simulador | Es esperado: se reintenta a los 5 s y la reconciliación corrige. Buscar `volvió a responder` en el log |
| A ×60, un switch que sí responde aparece como caído (timeouts de 0,5 s en el log) | Tarda más de medio segundo real en contestar: el timeout se divide por el factor (A19) | Con factor 60, 0,5 s reales son 30 s de control: un switch así está lento de verdad. Si la red es lenta, subir `switch.timeout-*` no alcanza (el piso es 0,5 s): bajar el factor o mejorar la red. La línea `Timeouts de los switches con factor …` del log, en cada INICIAR, muestra los valores en uso |
| Todos los tests del engine dan 500 | Se corrió `mvn test -pl modules/engine` sin `-am` y se usó un `core` viejo de `~/.m2` | Correr desde la raíz o con `-am` |
| El contenedor no toma un cambio del código o de `application.properties` | La imagen se reusa: `--force-recreate` no recompila | `docker compose -f docker/docker-compose.yml up -d --build` |
| El log se llena de lecturas `[TEMPERATURA]` | El nivel DEBUG está activado (o la imagen es anterior a T16) | Comentar la línea de DEBUG en `application.properties` y reconstruir con `--build` |

Las limitaciones de diseño (lo que el controlador no hace a propósito) están en el
[roadmap](arquitectura/roadmap.md#1-limitaciones-conocidas), cada una con su impacto.

## 7. Qué leer, en orden

1. [README](../README.md): qué hace, cómo se corre, API, variables de entorno.
2. [4+1](arquitectura/4mas1.md): el diseño, con diagramas; el §4 explica el tiempo del control, que es
   lo más sutil.
3. [`decisiones.md`](arquitectura/decisiones.md): por qué está hecho así.
4. [`roadmap.md`](arquitectura/roadmap.md): qué falta y en qué orden conviene hacerlo.
5. [`interop-v2.md`](api/interop-v2.md): el contrato con el simulador y los dispositivos.
6. [`tests/criterio-tests.md`](tests/criterio-tests.md) y [`tests/resultados.md`](tests/resultados.md): qué
   se prueba y por qué alcanza.
