# Características

## Gestión inteligente de tarifas UTE multihorario
**Descripción:**
El sistema optimiza el encendido y apagado de la losa radiante aprovechando la inercia térmica de los ambientes y los tramos de tarifa económica de UTE, reduciendo el consumo durante el horario pico.

## Simulación de capacidad y potencia contratada
**Descripción:**
Permite probar distintas configuraciones de uso frente al límite de potencia contratado con UTE para evaluar la viabilidad del encendido de habitaciones y planificar estrategias de uso escalonado.

## Interacción y diagnóstico mediante GenAI / LLM
**Descripción:**
Asistente conversacional en lenguaje natural que permite al usuario consultar el estado del sistema y entender el rendimiento térmico de su hogar sin necesidad de interpretar datos o gráficos complejos.

## Análisis histórico de gestión pasada (tiempos de calentamiento y enfriamiento)
**Descripción:**
Registro y evaluación de datos persistidos de telemetría (temperatura vs. tiempo) para medir la velocidad de calentamiento y enfriamiento de cada habitación, facilitando la detección de pérdidas de aislamiento.

## Integración con pronóstico meteorológico (48 hs)
**Descripción:**
Consulta automatizada de predicciones del tiempo y datos de temperatura/humedad exterior para ajustar preventivamente las curvas de calefacción ante cambios climáticos bruscos.

---

## Estado en el prototipo (Iteración 4)

| Característica | Estado | Dónde |
|---|---|---|
| Gestión de tarifas multihorario | ✅ Parcial: corta la calefacción al entrar en la franja punta y la restituye al salir (franja y días configurables en el sitio). No anticipa el encendido aprovechando la inercia térmica. | `core` (`FranjaHoraria`, `Core.decidir`) |
| Potencia contratada | ✅ Nunca se supera la contratada: se reparte entre las habitaciones con déficit por **mayor déficit primero**. No hay todavía un modo de simulación separado. | `core` (`Core.decidir`, `MayorDeficitPrimero`) |
| Diagnóstico mediante GenAI / LLM | ⏳ No implementado. Existe `GET /diagnostico`, que muestra por habitación la decisión y su motivo. | `engine` (`DiagnosticoController`) |
| Análisis histórico | ⏳ No implementado: no se persiste la telemetría, solo el inventario del sitio. | — |
| Pronóstico meteorológico | ⏳ No implementado. | — |
