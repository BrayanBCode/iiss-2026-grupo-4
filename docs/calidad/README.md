# Calidad — Iteración 6

| Qué | Dónde | Resultado |
|---|---|---|
| Estándar de codificación (Google Java Style, AOSP) | [`estandares-codificacion.md`](estandares-codificacion.md) | — |
| Verificación del formato (`mvn spotless:check`) | [`spotless-check.txt`](spotless-check.txt) | ✅ 75 archivos, 0 a corregir |
| Cobertura del core (JaCoCo) | [`cobertura/`](cobertura/README.md) | ✅ 100 % líneas, 94,4 % ramas (el build exige ≥ 85 %) |
| Ejecución de los tests | [`../tests/resultados.md`](../tests/resultados.md) | ✅ 281 tests, 0 fallas |

Última corrida: 2026-11-08, `mvn clean verify` sobre `implementacion6` con todo lo de la iteración 6 (cierre, T30).
