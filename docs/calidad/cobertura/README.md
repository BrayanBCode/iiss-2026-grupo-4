# Cobertura del core — Iteración 5

Medida con JaCoCo 0.8.14 al correr `mvn verify` el 2026-10-27 sobre la rama `implementacion5`.
El objetivo de la iteración es **≥ 85 % de líneas en el core**: se cumple con margen.

Reporte navegable: [`core-jacoco/index.html`](core-jacoco/index.html) (también
[`jacoco.csv`](core-jacoco/jacoco.csv) y [`jacoco.xml`](core-jacoco/jacoco.xml)).

## Totales del módulo `core`

| Métrica | Cubierto | Total | % |
|---|---|---|---|
| Líneas | 237 | 238 | **99,6 %** |
| Ramas | 118 | 130 | **90,8 %** |
| Instrucciones | 1182 | 1185 | 99,7 % |
| Métodos | 46 | 47 | 97,9 % |

## Por clase (las que tienen lógica)

| Clase | Líneas | Ramas |
|---|---|---|
| `Core` | 138 / 139 | 80 / 86 |
| `MayorDeficitPrimero` | 22 / 22 | 11 / 14 |
| `FranjaHoraria` | 10 / 10 | 13 / 14 |
| `ParametrosControl` | 11 / 11 | 9 / 10 |
| `Estimulo.Sincronizacion` | 5 / 5 | 3 / 4 |
| `Sitio` | 7 / 7 | 2 / 2 |
| `CoreClient` | 10 / 10 | — |

El resto del modelo (`Decision`, `Motivo`, `Accion`, `EstadoHabitacion`, demás estímulos) está
cubierto al 100 % y no tiene ramas.

Las 12 ramas sin cubrir se pueden ver, marcadas en amarillo, en el reporte HTML de cada clase.
Respecto de la iteración 4 (226/227 líneas, 99 %) se agregaron 11 líneas al core, todas cubiertas.

## Cómo se regenera

```bash
mvn -pl modules/core verify      # o ./scripts/test.sh core
# reporte en modules/core/target/site/jacoco/index.html
```
