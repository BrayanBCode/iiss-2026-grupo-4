# Documento de Visión del Producto — v1

## 1. Declaración de Visión (Plantilla de Moore)

| | |
|---|---|
| **PARA** | Empresas del sector agrícola y productores hortícolas. |
| **QUE** | Buscan supervisar sus instalaciones de forma continua y prevenir pérdidas por variaciones climáticas o fallas en sus equipos. |
| **EL** | AgriFlow |
| **ES UN** | Sistema de monitoreo IoT y automatización agrícola basado en sensores físicos. |
| **QUE** | Ofrece mayor control sobre lo que pasa en tu huerta mediante notificaciones en tiempo real sobre el ambiente (temperatura y humedad) y la posibilidad de disparar eventos automáticos en respuesta a lecturas críticas del entorno o del consumo de los equipos. |
| **A DIFERENCIA DE** | Los métodos de revisión manual con termómetros/higrómetros tradicionales o sistemas de monitoreo pasivo que no permiten la automatización. |
| **NUESTRO PRODUCTO** | Integra la recolección de datos en tiempo real mediante sensores físicos directos en la huerta con la automatización activa de equipos para responder al instante ante cualquier contingencia. |

## 2. Contexto y oportunidad

AgriFlow nace dentro de la iniciativa de IoTEste de explorar el uso de dispositivos IoT
de propósito general (Shelly) para construir una oferta de valor diferenciada, en un
esquema de desarrollo exploratorio: no hay un alcance de producto cerrado de antemano,
sino que se define a partir de lo que la tecnología permite y de las oportunidades de
negocio detectadas.

Para esta primera iteración se optó por el **sector agrícola/hortícola** como nicho:
un productor que hoy revisa temperatura y humedad de forma manual (o no las monitorea
en absoluto) tiene dos problemas concretos:
- **Pérdidas por reacción tardía**: una helada, un pico de calor o un corte de energía
  en el sistema de riego/calefacción puede detectarse horas después de ocurrido, cuando
  el daño al cultivo ya está hecho.
- **Sin capacidad de respuesta automática**: aunque detecten el problema a tiempo, los
  sistemas de monitoreo pasivo no pueden accionar nada por sí mismos — alguien tiene que
  intervenir manualmente.

AgriFlow ataca ambos problemas combinando sensado continuo con capacidad de actuación
automática, apoyándose en los dos dispositivos Shelly definidos como punto de partida
para todos los equipos del taller.

## 3. Análisis de capacidades de los dispositivos

### 3.1 Shelly H&T Gen3 — sensor de temperatura y humedad

Rol en AgriFlow: **sensado ambiental** de la huerta/invernadero.

- Sensor de temperatura y humedad con pantalla e-paper, conectividad Wi-Fi (802.11
  b/g/n) y Bluetooth 4.2.
- Alimentación por 4 pilas AA (hasta ~1 año de autonomía) o USB-C para alimentación
  continua — relevante para decidir dónde instalar cada unidad (zonas de difícil
  acceso a pilas vs. puntos con alimentación disponible).
- Rango operativo documentado: 0 °C a 40 °C y 30–70% HR recomendado — una limitación a
  tener en cuenta si se apunta a invernaderos con climas más extremos.
- Es **operado a batería, no permanentemente conectado al broker**: en modo batería se
  despierta cada 1 minuto para medir, pero solo publica por MQTT si la temperatura
  varió más de 0.5 °C o la humedad más de 5%, con un reporte de estado incondicional
  cada 2 horas como máximo si no hubo cambios. En modo USB reporta cada 5 minutos.
  Esto implica que el sistema debe **tolerar intervalos irregulares entre lecturas**, y
  no puede asumir un streaming continuo tipo "cada N segundos".
- Publica vía MQTT usando notificaciones RPC (`events/rpc`), con un Client ID/topic
  prefix único por dispositivo (formato `shellyhtg3-<MAC>`).

### 3.2 Shelly Pro 1PM — switch con medición de consumo

Rol en AgriFlow: **actuación automática + monitoreo de consumo** de equipos eléctricos
de la instalación (por ejemplo, bombas de riego, calefacción por losa radiante,
ventilación).

- Relé de 1 canal, montaje DIN rail, hasta 16 A de salida, con medición de potencia
  integrada (consumo en tiempo real).
- Conectividad simultánea Wi-Fi y LAN (con LAN como respaldo), pensado para escenarios
  donde la conectividad debe ser confiable — relevante en un contexto agrícola donde el
  Wi-Fi puede ser inestable.
- Protecciones de sobrepotencia y sobrevoltaje con corte automático de salida y
  notificación inmediata — una capacidad de seguridad que AgriFlow puede aprovechar
  directamente como señal de alarma, sin tener que reimplementarla.
- Soporta MQTT (con TLS/certificados para entornos que lo requieran), lo que permite
  tanto **leer** el consumo como **accionar** el switch de forma remota vía mensajes.

### 3.3 Qué habilita esta combinación para AgriFlow

La combinación de ambos dispositivos es justamente lo que sostiene la propuesta de
valor del "NUESTRO PRODUCTO" de la plantilla de Moore: el H&T Gen3 aporta la lectura
del ambiente y el Pro 1PM aporta tanto el dato de consumo de los equipos como la
capacidad de actuar sobre ellos. Un evento típico que el producto debería poder cerrar
de punta a punta: *temperatura por debajo de un umbral → AgriFlow decide activar la
calefacción → se acciona el Shelly Pro 1PM → se verifica el consumo resultante para
confirmar que efectivamente se encendió*.

Una limitación a tener presente desde ya en el diseño: al ser el H&T Gen3 un
dispositivo de reporte no continuo (event-driven, con gaps de minutos u horas), el
"tiempo real" de la propuesta de valor es real dentro de esos márgenes — no hay lectura
instantánea permanente, y el diseño de las notificaciones/alertas debe contemplar esa
latencia.

## 4. Alcance de esta iteración (v1)

Esta primera versión de la visión se apoya en el prototipo mínimo de la Iteración 1:
un broker MQTT (Mosquitto) y un suscriptor Java que recibe e imprime en consola los
mensajes de un tópico, simulando la llegada de lecturas de temperatura. No incluye
todavía integración real con los dispositivos Shelly ni lógica de automatización — eso
es objeto de iteraciones posteriores, a medida que se defina el modelo de datos y las
reglas de automatización de AgriFlow.

## 5. Próximos pasos

- Definir el modelo de mensajes/eventos (formato JSON) que replique la semántica real
  de los tópicos MQTT de los dispositivos Shelly.
- Explorar reglas de automatización simples (umbral → acción) como primer caso de uso
  end-to-end.
- Evaluar qué otros dispositivos Shelly podrían sumarse a futuro según los hallazgos de
  esta etapa exploratoria.