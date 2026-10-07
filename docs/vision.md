# Documento de Visión del Producto — v2

## 1. Declaración de Visión (Plantilla de Moore)

| | |
|---|---|
| **PARA** | Familias y personas propietarias de una vivienda que utilizan calefacción por losa radiante. |
| **QUE** | Buscan mantener su hogar a una temperatura confortable sin tener que intervenir manualmente, y quieren visibilidad y control sobre el consumo eléctrico que eso genera. |
| **EL** | EcoWarm |
| **ES UN** | Sistema de domótica IoT para automatización de calefacción por losa radiante y gestión del consumo eléctrico del hogar. |
| **QUE** | Ofrece encendido/ajuste automático de la calefacción por habitación según la temperatura ambiente y el pronóstico climático del día siguiente, con la posibilidad de que el usuario ajuste la temperatura estándar a su sensación térmica; además genera alertas de consumo eléctrico anormal y permite comparar registros semanales de temperatura, consumo/gasto y horas de uso por habitación. |
| **A DIFERENCIA DE** | Los termostatos manuales o sistemas de calefacción que no se adaptan al clima, no distinguen por habitación, y no dan visibilidad del consumo eléctrico asociado. |
| **NUESTRO PRODUCTO** | Combina sensores de temperatura (Shelly H&T Gen3) y actuadores de switch con medición de consumo (Shelly Pro 1PM) por habitación, con reglas de automatización que consideran tanto la lectura ambiental como el pronóstico climático y las preferencias del usuario, junto con historial y alertas de consumo. |

## 2. Contexto y oportunidad

EcoWarm nace dentro de la iniciativa del Taller IISS 2026 de explorar el uso de
dispositivos IoT de propósito general (Shelly) para construir una oferta de valor
diferenciada, en un esquema de desarrollo exploratorio: no hay un alcance de producto
cerrado de antemano, sino que se define a partir de lo que la tecnología permite y de
las oportunidades detectadas.

Para esta iteración se optó por el **hogar con calefacción por losa radiante** como
nicho: hoy quien tiene este tipo de calefacción, o bien la deja fija en una temperatura
todo el año, o la enciende/apaga manualmente sin considerar el clima del día siguiente
ni el consumo eléctrico que está generando. Esto genera dos problemas concretos:

- **Confort inconsistente y gasto evitable**: sin ajuste automático según el clima
  previsto, se calienta de más en días templados (gasto innecesario) o de menos en
  días fríos (pérdida de confort), y nadie se entera hasta ver la factura de UTE.
- **Sin visibilidad de consumo por equipo/habitación**: aunque el usuario quiera
  ahorrar, hoy no tiene forma simple de saber qué habitación o equipo está generando
  un consumo anormal, ni de comparar semana a semana si sus hábitos o ajustes
  mejoraron algo.

EcoWarm ataca ambos problemas combinando sensado continuo de temperatura por
habitación con capacidad de actuación automática sobre la calefacción, apoyándose en
los dos dispositivos Shelly definidos como punto de partida para todos los equipos del
taller.

### 2.1 A quién apunta (personas)

El detalle completo de historias de usuario y personas está en
[`docs/Producto/Personas.pdf`](Producto/Personas.pdf) e
[`docs/Producto/Historias.pdf`](Producto/Historias.pdf). En resumen, se identificaron
tres perfiles con necesidades distintas:

- **Laura Caraballo (65, jubilada)** — poco manejo de informática. Quiere ver y
  optimizar sus gastos mensuales de equipos eléctricos, con una interfaz simple,
  amigable e intuitiva.
- **Facundo Rodríguez (34, técnico en aires acondicionados)** — manejo medio de
  aplicaciones, mucho conocimiento técnico de acondicionamiento de ambientes. Necesita
  datos correctos y entendibles para diagnosticar lo que está pasando, ya sea para
  pruebas o uso cotidiano.
- **Mateo Beledo (40, trabajador con informática básica)** — quiere que su casa esté
  acondicionada cuando llega del trabajo, sin tener que operar nada manualmente, y de
  paso poder ver sus gastos y consumos de la semana.

Esta diversidad de perfiles es la razón por la que EcoWarm necesita ser tanto
**automático por defecto** (para Mateo) como **transparente en sus datos** (para
Laura y Facundo).

## 3. Análisis de capacidades de los dispositivos

### 3.1 Shelly H&T Gen3 — sensor de temperatura y humedad

Rol en EcoWarm: **sensado ambiental** de cada habitación de la vivienda.

- Sensor de temperatura y humedad con pantalla e-paper, conectividad Wi-Fi (802.11
  b/g/n) y Bluetooth 4.2.
- Alimentación por 4 pilas AA (hasta ~1 año de autonomía) o USB-C para alimentación
  continua — relevante para decidir dónde instalar cada unidad (zonas de difícil
  acceso a pilas vs. puntos con alimentación disponible).
- Rango operativo documentado: 0 °C a 40 °C y 30–70% HR recomendado — cubre bien el
  rango habitual de una vivienda.
- Es **operado a batería, no permanentemente conectado al broker**: en modo batería se
  despierta cada 1 minuto para medir, pero solo publica por MQTT si la temperatura
  varió más de 0.5 °C o la humedad más de 5%, con un reporte de estado incondicional
  cada 2 horas como máximo si no hubo cambios. En modo USB reporta cada 5 minutos.
  Esto implica que el sistema debe **tolerar intervalos irregulares entre lecturas**, y
  no puede asumir un streaming continuo tipo "cada N segundos".
- Publica vía MQTT usando notificaciones RPC (`events/rpc`), con un Client ID/topic
  prefix único por dispositivo (formato `shellyhtg3-<MAC>`).

### 3.2 Shelly Pro 1PM — switch con medición de consumo

Rol en EcoWarm: **actuación automática de la calefacción por losa radiante +
monitoreo de consumo** de ese equipo por habitación.

- Relé de 1 canal, montaje DIN rail, hasta 16 A de salida, con medición de potencia
  integrada (consumo en tiempo real).
- Conectividad simultánea Wi-Fi y LAN (con LAN como respaldo), pensado para escenarios
  donde la conectividad debe ser confiable.
- Protecciones de sobrepotencia y sobrevoltaje con corte automático de salida y
  notificación inmediata — una capacidad de seguridad que EcoWarm puede aprovechar
  directamente como señal para las alertas de consumo anormal, sin tener que
  reimplementarla.
- Soporta MQTT (con TLS/certificados para entornos que lo requieran), lo que permite
  tanto **leer** el consumo como **accionar** el switch (encender/apagar la
  calefacción) de forma remota vía mensajes.

### 3.3 Qué habilita esta combinación para EcoWarm

La combinación de ambos dispositivos sostiene la propuesta de valor del "NUESTRO
PRODUCTO" de la plantilla de Moore: el H&T Gen3 aporta la lectura ambiental de cada
habitación y el Pro 1PM aporta tanto el dato de consumo del equipo de calefacción como
la capacidad de accionarlo remotamente. Un evento típico que el producto debería poder
cerrar de punta a punta: *temperatura de una habitación por debajo del umbral (ajustado
según pronóstico del día siguiente y preferencia del usuario) → EcoWarm decide activar
la calefacción de esa habitación → se acciona el Shelly Pro 1PM correspondiente → se
verifica el consumo resultante para confirmar que efectivamente se encendió*.

Una limitación a tener presente desde ya en el diseño: al ser el H&T Gen3 un
dispositivo de reporte no continuo (event-driven, con gaps de minutos u horas), el
"tiempo real" de la propuesta de valor es real dentro de esos márgenes — no hay lectura
instantánea permanente, y el diseño de las notificaciones/alertas debe contemplar esa
latencia.

## 4. Alcance de esta iteración (v1)

Esta versión se apoya en el prototipo del taller compuesto por dos módulos Maven:

- **`eventGenerator`**: simula 3 termostatos Shelly H&T (living, dormitorio, cocina)
  publicando lecturas de temperatura por MQTT cada 10 segundos.
- **`subscriber`**: se suscribe al broker Mosquitto, recibe esas lecturas, identifica a
  qué habitación pertenecen y las persiste en PostgreSQL (tablas `habitaciones` y
  `lecturas`).

Todavía **no** incluye: integración real con dispositivos Shelly físicos, consulta al
pronóstico climático, lógica de automatización (encendido/apagado según umbral),
comparación de registros semanales, ni alertas de consumo anormal — eso es objeto de
iteraciones posteriores, a medida que se defina el modelo de reglas de automatización
de EcoWarm.

## 5. Próximos pasos

- Integrar una fuente de pronóstico climático del día siguiente como entrada de la
  regla de automatización (temperatura estándar por defecto, ajustable por el
  usuario).
- Implementar la lógica de automatización umbral → acción sobre el Shelly Pro 1PM de
  cada habitación, como primer caso de uso end-to-end.
- Definir y almacenar el consumo eléctrico por lectura (no solo temperatura), para
  poder calcular tiempo de calentado/enfriado, horas de uso y comparaciones semanales
  de gasto.
- Diseñar la regla de detección de consumo anormal y el formato de la alerta al
  usuario (equipo, gasto, consumo que la disparó).
- Evaluar qué otros dispositivos Shelly podrían sumarse a futuro según los hallazgos de
  esta etapa exploratoria.
