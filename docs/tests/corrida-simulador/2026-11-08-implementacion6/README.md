# Corrida con el simulador oficial `ecowarm-run` — 2026-11-08 (`implementacion6` integrado)

Primera corrida sobre `implementacion6` con todo lo de la iteración 6 mergeado (PR #22 a #35: T24b–T24e,
T26, T27, T29, T31–T37). Las corridas anteriores eran sobre una rama cada una; esta confirma que
juntas no rompen nada.

**Resultado: 9 de 9 pasan** ([`reporte.md`](reporte.md)). La serie y las interacciones de cada
escenario (el detalle crudo de `ecowarm-run`) no se guardan en el repo: se regeneran corriendo la
herramienta. El log del controlador de esta corrida no se guardó.

## Cómo se corrió

```bash
# controlador: implementacion6 en 36843a5 (merge de #35)
mvn clean verify     # BUILD SUCCESS: core 56 tests, engine 225, 0 fallas
ENTORNO_URL=http://localhost:9090 ENTORNO_API_KEY=iiss-2026 CONTROLADOR_BASE_URL=http://localhost:8085 API_KEY=clave-grupo4 SERVER_PORT=8085 java -jar modules/engine/target/engine.jar
# simulador (el 1883 está ocupado por un Mosquitto local)
java --sun-misc-unsafe-memory-access=allow -jar ecowarm-run.jar todos --mqtt 1884
```

## Resultados, comparados con las corridas anteriores

| Escenario | Qué prueba | 2026-11-06 (T24e) | 2026-11-08 (T37) | 2026-11-08 (`implementacion6`) |
|---|---|---|---|---|
| E02 | 1 habitación, entra todo | pasa | pasa | pasa |
| E04 | 2 habitaciones que no entran juntas | pasa | pasa | pasa |
| E11 | Entrar en punta (17:00) | pasa | pasa | pasa |
| E14 | Subir la temperatura esperada en caliente | pasa | pasa | pasa |
| E18 | Bajar la contratada en caliente | pasa · mejora: aprovechamiento 78,8 % | pasa · igual | pasa · igual (78,8 %) |
| E19 | Un switch que arranca encendido | pasa | pasa | pasa |
| F01 | El broker cae y vuelve | pasa | pasa | pasa |
| F03 | Un termostato deja de publicar | pasa | pasa | pasa |
| F05 | Se pierden los OFF | pasa · mejora: aprovechamiento 76,4 % | pasa · igual | pasa · igual (76,4 %) |

El aprovechamiento de E18 y F05 es una expectativa de nivel "mejora" y no hace fallar el escenario.
Los porcentajes son los mismos de las corridas anteriores, con las mismas causas: en F05 se retiene
un ON mientras el OFF no se confirma; en E18 influye la histéresis. El detalle está en la
[corrida del 2026-11-06](../2026-11-06-ecowarm-run/README.md#lo-que-queda-como-mejora-no-hace-fallar).

## Qué no cubre

- Los escenarios nuevos del docente (T28) y la red del laboratorio (T13): esta corrida es en la
  misma máquina.
- Lo que el simulador no prueba (varios switches colgados cruzando la punta, ×600 y ×3600,
  50 habitaciones, fallas combinadas) lo cubre `EstresTest`, que pasa en el `verify` de arriba.
