# Criterio de adecuación del conjunto de tests — Iteración 4

Este documento explica **por qué** el conjunto de tests unitarios del core se considera adecuado,
cómo se construyó con TDD y cómo se ejecuta. Los tests están en
[`modules/core/src/test/java`](../../modules/core/src/test/java/uy/edu/utec/iiss/core).

## 1. Qué se prueba

El objeto bajo prueba es la **función de decisión del core**, `Core.decidir(sitio, habitaciones,
estados, ahora, factorTiempo) → List<Decision>`: dada una situación (potencia contratada, franja
punta, hora del control y, por cada habitación, su temperatura esperada, su potencia, su última
temperatura, cuándo se midió y si está encendida), qué habitaciones reciben energía y **por qué**
(cada `Decision` lleva un `Motivo`).

`decidir` es pública y recibe por parámetro todo lo que usa: no depende de lo que el core haya
procesado antes. Por eso hay dos niveles de tests, todos sin mocks:

- **`DecidirTest`**: llama a `decidir` directamente con una situación completa y verifica cuáles
  prenden. Ejemplo: *"5 habitaciones con déficit de 1 kW cada una y 3 kW contratados → prenden las
  3 de mayor déficit"*, *"la encendida con déficit 3.0 conserva la energía ante otra con 3.3"*,
  *"lunes 18:00, 3 con déficit → ninguna prende, tarifa punta"*.
- **`CoreTest`**: arma la situación con estímulos (`NuevaLectura`, `Tick`,
  `ConfiguracionActualizada`, `Sincronizacion`) a través de `Core.procesar`, que actualiza el
  estado y llama a `decidir`. Cubre lo que no es la decisión en sí: lecturas descartadas, reloj,
  configuración atómica y secuencias largas.

## 2. Criterio de adecuación

Se usan **cuatro criterios complementarios**. El conjunto se considera adecuado cuando los cuatro
se cumplen; los cuatro se cumplen hoy.

### C1 — Cobertura de requisitos (caja negra)

Cada característica que pide la letra para el core, y cada regla del estándar v2 que afecta la
decisión, tiene al menos un test que la verifica en positivo **y** uno en negativo.

Los grupos de `CoreTest` de la tabla tienen su contraparte por situación en `DecidirTest`
(`UnaHabitacion`, `TarifaPunta`, `RepartoDePotencia`, `TopeDePotencia`, `Criterio`, `Histeresis`,
`LecturasVencidas`).

| Requisito (letra / estándar) | Grupo en `CoreTest` | Ejemplos de casos |
|---|---|---|
| Decidir qué habitación recibe energía | 1. Decisión básica | bajo la esperada → ON; en la esperada → OFF; sin lectura → OFF; sin configuración → nada |
| Cortar al entrar en punta y restituir al salir | 2. Tarifa punta | 16:59 ON / 17:00 OFF; 23:00 restituye; HABILES vs TODOS; viernes / sábado / domingo; zona del sitio vs zona de la JVM |
| Nunca superar la potencia contratada | 3. Consumo máximo | suma = contratada permitida (incluso 0.1+0.2=0.3); habitación > contratada nunca; la que no entra no bloquea a las siguientes; bajar la contratada en caliente |
| Criterio no azaroso y justificable | 4. Criterio + `MayorDeficitPrimeroTest` | mayor déficit primero; desempate por id independiente del orden de llegada; turnos (ninguna queda sin energía para siempre) |
| Actualizar la configuración (§5.1: reemplazo completo, atómico, en caliente) | 5. Configuración | habitación quitada deja de decidir; se conservan temperaturas de las que siguen; configuración inválida se rechaza entera y queda la anterior |
| Tiempo del control (§5.2: INICIAR re-sincroniza, factorTiempo) | 9. Reloj del control | re-sincronizar hacia atrás; factor ≤ 0 rechazado; ticks atrasados no retroceden el reloj |

### C2 — Particiones de equivalencia y valores límite

Para cada variable de entrada de la decisión se identificaron sus clases y se probaron sus bordes:

| Variable | Clases / límites probados |
|---|---|
| Temperatura vs esperada | muy por debajo · dentro de la banda de histéresis (esperada − 0.3, esperada) · exactamente la esperada · por encima |
| Hora vs franja punta `[desde, hasta)` | 16:59 (antes) · 17:00 (límite inclusivo) · 18:00 (dentro) · 22:59 (último minuto) · 23:00 (límite exclusivo) |
| Día | hábil (lunes, viernes) · sábado · domingo, con `HABILES` y con `TODOS` |
| Potencia | suma < · = · > contratada; habitación sola > contratada; error de redondeo binario |
| Antigüedad de la lectura | dentro de la vigencia · exactamente en el límite (10 min) · vencida (11 min); escalada con factor ×60 |
| Calidad de la lectura | válida · NaN · ±∞ · < −40 °C · > 85 °C · fuera de orden · de habitación desconocida |
| Configuración | válida · vacía · potencia 0/negativa · contratada 0/NaN · id vacío · id duplicado · temperatura NaN |

### C3 — Escenarios de robustez (el entorno falla)

La letra pone el énfasis en los aspectos no funcionales. El core tiene que **adaptarse** a un
entorno defectuoso sin decidir mal. Cada escenario tiene su grupo de tests:

| Escenario del entorno | Comportamiento esperado | Grupo |
|---|---|---|
| Termostato muerto (deja de publicar) | la habitación se apaga (`LECTURA_VENCIDA`) y su potencia pasa a otra; se recupera cuando vuelve | 8 |
| Sensor roto / mensaje corrupto | la lectura se descarta, se conserva el último dato bueno | 7 |
| Mensajes MQTT desordenados (QoS 0/1) | una lectura más vieja que la conocida se descarta | 7 |
| Ticks del temporizador atrasados | el reloj no retrocede (no "sale" de la punta por error) | 9 |
| Re-INICIAR con otra fecha / acelerado | el reloj se mueve, la vigencia se escala con el factor, no se apaga todo | 8, 9 |
| Lecturas que oscilan en torno a la esperada | histéresis: el relé no conmuta en cada lectura | 6 |
| Dos habitaciones con déficit parecido y potencia para una | margen anti-oscilación: se turnan en vez de alternar en cada lectura | 4 |
| Configuración inválida enviada en caliente | se rechaza entera, sigue la anterior | 5 |

### C4 — Invariantes sobre secuencias arbitrarias + cobertura estructural

- **Invariantes** (grupo 10): una secuencia seudoaleatoria de 3000 estímulos (semilla fija → reproducible)
  verifica tras **cada** paso que (a) la potencia encendida nunca supera la contratada, (b) nunca
  hay nada encendido en punta, (c) siempre hay una decisión por habitación. Otra prueba verifica
  **determinismo**: la misma secuencia produce exactamente las mismas decisiones.
- **Cobertura estructural** (JaCoCo, `mvn -pl modules/core test`, reporte en
  `modules/core/target/site/jacoco/index.html`), resultado actual del módulo core:

  | Métrica | Cubierto | Total | % |
  |---|---|---|---|
  | Líneas | 226 | 227 | 99 % |
  | Ramas | 118 | 130 | 91 % |

  Las ramas sin cubrir son comprobaciones defensivas (p. ej. `null` en campos que el engine nunca
  envía, combinaciones `NaN`/`∞` ya cubiertas por otra rama de la misma condición). El umbral que
  nos fijamos es **≥ 90 % de líneas y ≥ 85 % de ramas en el core**: por debajo de eso se agregan tests.

**Por qué alcanza:** C1 asegura que no falta ningún requisito; C2 que cada requisito se probó en
sus bordes, que es donde aparecen los errores (el límite 17:00/23:00, la suma igual a la
contratada); C3 que el core se adapta a las fallas del entorno que menciona la letra; C4 que las
propiedades de seguridad (tope de potencia, punta) se mantienen para *cualquier* secuencia, no solo
para las que se nos ocurrieron, y que no queda código sin ejercitar.

## 3. Cómo se aplicó TDD

Cada característica se agregó con el ciclo rojo → verde → refactor:

1. Se escribió el test del grupo correspondiente (falla: el comportamiento no existe).
2. Se implementó lo mínimo en `Core` / `MayorDeficitPrimero` para pasarlo.
3. Se refactorizó manteniendo todo en verde.

En esta iteración, los tests de los grupos 6 a 10 se escribieron antes de su implementación
(histéresis, lecturas defectuosas y vencidas, reloj, invariantes). Al implementarlos, los tests
encontraron dos defectos reales del core anterior:

- el criterio recibía el estado de las habitaciones **después** de limpiarlo, así que nunca sabía
  cuál estaba encendida (corregido; lo cubre `diferenciaChica_noProvocaIntercambio`);
- una lectura de una habitación desconocida quedaba guardada y "revivía" si después se agregaba
  esa habitación (corregido; lo cubre `lecturaDeHabitacionDesconocida_seIgnora`).

También obligaron a revisar dos escenarios viejos que pasaban "por casualidad" con datos de 30
minutos a 6 horas de antigüedad: con la vigencia de lecturas, un termostato que no publica en ese
lapso se considera caído, que es el comportamiento correcto.

## 4. Fuera del core

El engine tiene sus propios tests (no son TDD del core, pero dan confianza en la integración):

| Clase de test | Qué verifica |
|---|---|
| `CoreAdapterTest` (21) | traducción decisión → comando, no repetir comandos, consulta §4 al habilitar, reconciliación (switch reiniciado o movido a mano), switch caído no bloquea a los demás, espera entre reintentos |
| `ApiEstandarTest` (13) | contexto Spring completo: §5.1 (persistencia, GET no es eco, atómico, en caliente), §5.2 (estados, INICIAR/DETENER, todos los 400), 401, ABM |
| `ValidadorInventarioTest` (9) | reglas de consistencia del §1 |
| `RepositorioInventarioTest` (4) | persistencia sobrevive al reinicio, archivo corrupto, escritura atómica |
| `RelojControlTest` (4), `PayloadTermostatoTest` (3), `RegistroEntornoTest` (2) | reloj acelerado §5.2, payload §2 (ts en segundos), broker del registro §6 |

## 5. Ejecución y resultados

```bash
./scripts/test.sh core      # solo el core (Docker, sin Java local)
./scripts/test.sh           # todos los módulos
```

Resultado de la última ejecución (2026-10-14):

| Módulo | Tests | Fallas | Errores |
|---|---|---|---|
| core | 116 | 0 | 0 |
| engine | 56 | 0 | 0 |

Los reportes quedan en `modules/*/target/surefire-reports/`. El detalle por clase de test está en
[`resultados.md`](resultados.md).
