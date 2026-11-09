# Corrida con el simulador oficial `ecowarm-run` contra la imagen Docker — 2026-11-09

Las corridas anteriores fueron con `java -jar`. Esta corre la **imagen Docker y el
`docker/docker-compose.yml` que se entregan**, como se levanta el controlador en el laboratorio. Es
sobre `implementacion6` con T38–T40 (rama `iter6/t41-revision-entrega`).

**Resultado: 9 de 9 pasan** ([`reporte.md`](reporte.md)). El log completo del controlador dentro del
contenedor está en [`engine.log`](engine.log). El detalle crudo por escenario no se guarda (ver el
[índice](../README.md)).

## Cómo se corrió

```bash
# el controlador, con el compose que se entrega (se construye la imagen con el Dockerfile del engine)
ENTORNO_API_KEY=iiss-2026 docker compose -f docker/docker-compose.yml up -d --build
# el simulador en el anfitrión; el controlador lo alcanza por host.docker.internal
java --sun-misc-unsafe-memory-access=allow -jar ecowarm-run.jar todos --host host.docker.internal --mqtt 1884
```

- Se mantuvieron los valores del compose: `ENTORNO_URL=http://host.docker.internal:9090` y
  `CONTROLADOR_BASE_URL=http://localhost:8080` (el puerto publicado).
- Para no pisar otro contenedor de la misma máquina se usó otro nombre de proyecto y de contenedor
  (`-p ecowarm-t41` y un override de `container_name`). La imagen y el compose son los mismos.
- El broker va en el 1884 porque el 1883 está ocupado por un Mosquitto local.
- Herramienta: `ecowarm-run` `c95d037`. JVM del contenedor: Java 25.0.5.

## Resultados

| Escenario | Qué prueba | `java -jar` (2026-11-08) | Docker (2026-11-09) |
|---|---|---|---|
| E02 | 1 habitación, entra todo | pasa | pasa |
| E04 | 2 habitaciones que no entran juntas | pasa | pasa |
| E11 | Entrar en punta (17:00) | pasa | pasa |
| E14 | Subir la temperatura esperada en caliente | pasa | pasa |
| E18 | Bajar la contratada en caliente | pasa · mejora: aprovechamiento 78,8 % | pasa · igual (78,8 %) |
| E19 | Un switch que arranca encendido | pasa | pasa |
| F01 | El broker cae y vuelve | pasa | pasa |
| F03 | Un termostato deja de publicar | pasa | pasa |
| F05 | Se pierden los OFF | pasa · mejora: aprovechamiento 76,4 % | pasa · igual (76,4 %) |

## Qué muestra el log

- Los 12 `ERROR` son los esperados:
  - 8 de MQTT en F01: conexión perdida y reintentos mientras el broker está caído;
  - 4 de F05: una consulta por switch mientras el simulador no los deja responder.
- En cada INICIAR: `Timeouts de los switches con factor 60.0: 500 ms de conexión, 500 ms de
  respuesta` (A19).
- No hay ninguna línea `Anticipación de la punta`: los switches del simulador responden en
  milisegundos, así que la anticipación queda en el minuto configurado (A21 no cambia nada en una
  red normal).

## Qué no cubre

Sigue siendo la misma máquina. La red del laboratorio y los escenarios nuevos del docente siguen sin
probarse (roadmap L19).
