# Calidad — Iteración 6

| Qué | Dónde | Resultado |
|---|---|---|
| Estándar de codificación (Google Java Style, AOSP) | [`estandares-codificacion.md`](estandares-codificacion.md) | — |
| Verificación del formato (`mvn spotless:check`) | [`spotless-check.txt`](spotless-check.txt) | ✅ 75 archivos, 0 a corregir |
| Cobertura del core (JaCoCo) | [`cobertura/`](cobertura/README.md) | ✅ 100 % líneas, 94,6 % ramas (el build exige ≥ 85 %) |
| Ejecución de los tests | [`../tests/resultados.md`](../tests/resultados.md) | ✅ 289 tests, 0 fallas |

Última corrida: 2026-11-09, `mvn clean verify` sobre la rama `iter6/t38-t40-casos-extremos` (base `implementacion6`).
