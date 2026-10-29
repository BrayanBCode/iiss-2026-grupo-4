# Cobertura del core — Iteración 5

Medida con JaCoCo 0.8.14 al correr `mvn clean verify` el 2026-10-27 sobre la rama
`implementacion5`, después de dejar el core sin estado (tarjetas T4/T5). El objetivo de la
iteración es **≥ 85 % en el core**, y el build lo exige para **líneas y ramas** (ver abajo).

Reporte navegable: [`core-jacoco/index.html`](core-jacoco/index.html) (también
[`jacoco.csv`](core-jacoco/jacoco.csv) y [`jacoco.xml`](core-jacoco/jacoco.xml)).

## Totales del módulo `core`

| Métrica | Cubierto | Total | % |
|---|---|---|---|
| Líneas | 150 | 150 | **100 %** |
| Ramas | 79 | 84 | **94,0 %** |
| Instrucciones | 755 | 755 | 100 % |
| Métodos | 31 | 31 | 100 % |

El core es más chico que en la versión anterior (238 líneas, 130 ramas) porque el manejo de
estímulos y el estado (`procesar`, `CoreClient`, `validar`) pasaron al engine; ahora queda la
función `decidir`, `lecturaValida`, el criterio y el modelo. Los tests de esa parte que se fue
siguen corriendo en el engine (`CoreAdapterEstimulosTest`).

## Por clase (las que tienen ramas)

| Clase | Líneas | Ramas |
|---|---|---|
| `Core` | 61 / 61 | 40 / 40 |
| `MayorDeficitPrimero` | 22 / 22 | 11 / 14 |
| `FranjaHoraria` | 10 / 10 | 13 / 14 |
| `ParametrosControl` | 11 / 11 | 9 / 10 |
| `Estimulo.Sincronizacion` | 5 / 5 | 4 / 4 |
| `Sitio` | 7 / 7 | 2 / 2 |

El resto del modelo (`Decision`, `Motivo`, `Accion`, `EstadoHabitacion`, demás estímulos) no
tiene ramas y está cubierto al 100 %.

Las 5 ramas sin cubrir (marcadas en amarillo en el reporte HTML) son una combinación de una
condición compuesta (`||`/`&&`) que ningún test ejercita:
- `ParametrosControl` y `MayorDeficitPrimero` (constructores): el caso de histéresis o margen
  **infinito** (sí se prueban negativos y NaN);
- `MayorDeficitPrimero.ordenarCandidatas`: una candidata **sin entrada** en el mapa de estados (dos
  ramas; `decidir` nunca le pasa una con déficit sin estado);
- `FranjaHoraria.incluye`: una de las combinaciones de día hábil / fin de semana.

## El umbral se exige en el build

`modules/core/pom.xml` tiene la meta `check` de JaCoCo en la fase `verify`, con dos reglas sobre
todo el módulo: **líneas ≥ 85 %** y **ramas ≥ 85 %**. Si la cobertura baja, `mvn verify` falla, y
con él la CI (`.github/workflows/maven.yml`). Prueba de que funciona, corriendo un solo test:

```
$ mvn clean verify -pl modules/core -Dtest=ModeloTest -Dsurefire.failIfNoSpecifiedTests=false
[WARNING] Rule violated for bundle core: lines covered ratio is 0.10, but expected minimum is 0.85
[WARNING] Rule violated for bundle core: branches covered ratio is 0.09, but expected minimum is 0.85
[INFO] BUILD FAILURE
```

Ojo: JaCoCo acumula datos en `target/jacoco.exec` entre corridas; para medir de cero, usar
`mvn clean verify`.

## Cómo se regenera

```bash
mvn -pl modules/core verify      # o ./scripts/test.sh core
# reporte en modules/core/target/site/jacoco/index.html
```
