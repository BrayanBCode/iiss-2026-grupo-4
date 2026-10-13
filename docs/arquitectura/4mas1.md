# Arquitectura del controlador EcoWarm — modelo 4+1

Se documenta en forma incremental con la técnica 4+1 de Kruchten. En la **Iteración 4** se
presentan la **vista lógica** y los **diagramas de secuencia** del manejo de eventos; las vistas
de procesos, desarrollo y física, y los escenarios completos se agregan en las iteraciones 5 y 6.

| Vista | Estado | Dónde |
|---|---|---|
| Lógica | ✅ Iteración 4 | §1 |
| Escenarios (+1) — secuencias de eventos | ✅ Iteración 4 (parcial) | §2 |
| Procesos | ⏳ Iteración 5 (adelanto en §3) | §3 |
| Desarrollo | ⏳ Iteración 5–6 (adelanto en §4) | §4 |
| Física | ⏳ Iteración 6 | — |

Los diagramas están en Mermaid (GitHub los dibuja).

---

## 1. Vista lógica

### 1.1 Contexto

El controlador está entre los termostatos (MQTT), los switches (REST) y quien lo opera (REST).
En integración, el **entorno** (simulador de la cátedra o `ecowarm-check`) juega todos esos
papeles y además ofrece el registro (§6 del estándar).

```mermaid
flowchart LR
    T["Termostatos<br/>(por habitación)"] -- "MQTT §2<br/>topicTermostato" --> B[(Broker MQTT)]
    B --> C
    subgraph C["Controlador EcoWarm (grupo 4)"]
        direction TB
        E["engine<br/>(Spring Boot)"] --> K["core<br/>(Java puro)"]
    end
    C -- "REST §3 comandar<br/>REST §4 consultar" --> S["Switches<br/>(por habitación)"]
    O["Operador / entorno"] -- "REST §5<br/>PUT /sitio · POST /control/comandos" --> C
    C -- "REST §6<br/>POST /controladores" --> O
```

### 1.2 Módulos

| Módulo | Responsabilidad | Depende de |
|---|---|---|
| **core** | Decidir qué habitación recibe energía, con un modelo interno del estado, los estímulos y las decisiones. Sin comunicación externa, sin reloj del sistema, sin azar. | nada (solo JUnit en test) |
| **engine** | Conectar el core con el mundo: MQTT, REST entrante y saliente, persistencia, registro, reloj del control y eventos temporales. | core, Spring Boot, Paho |

La dirección de dependencia la impone Maven: `core` no tiene dependencias de producción, así que
el compilador impide que importe algo del engine.

### 1.3 Core — diagrama de clases

```mermaid
classDiagram
    direction LR
    class Core {
      -Sitio sitio
      -Map~String,ConfiguracionHabitacion~ habitaciones
      -Map~String,Double~ temperaturas
      -Map~String,Instant~ instanteLectura
      -Set~String~ encendidas
      -Instant ahora
      -double factorTiempo
      +procesar(Estimulo) List~Decision~
      +estadoDe(id) EstadoHabitacion
      -decidir() List~Decision~
    }
    class Estimulo {
      <<sealed interface>>
    }
    class NuevaLectura {
      idHabitacion
      temperaturaC
      ts
    }
    class Tick {
      ts
    }
    class ConfiguracionActualizada {
      sitio
      habitaciones
    }
    class Sincronizacion {
      ahora
      factorTiempo
    }
    Estimulo <|.. NuevaLectura
    Estimulo <|.. Tick
    Estimulo <|.. ConfiguracionActualizada
    Estimulo <|.. Sincronizacion

    class Decision {
      idHabitacion
      accion
      motivo
    }
    class Accion {
      <<enum>>
      ON
      OFF
    }
    class Motivo {
      <<enum>>
      CALEFACCIONANDO
      SATISFECHA
      SIN_LECTURA
      LECTURA_VENCIDA
      TARIFA_PUNTA
      SIN_POTENCIA_DISPONIBLE
      EXCEDE_CONTRATADA
    }
    Decision --> Accion
    Decision --> Motivo

    class Sitio {
      id
      potenciaContratadaKW
      zona
    }
    class FranjaHoraria {
      desde
      hasta
      dias
      +incluye(dia, hora)
    }
    class ConfiguracionHabitacion {
      id
      nombre
      temperaturaEsperada
      potenciaKW
    }
    class EstadoHabitacion {
      idHabitacion
      temperaturaActual
      ultimaLectura
      encendida
    }
    class ParametrosControl {
      histeresisC
      vigenciaLectura
      rango válido
    }
    Sitio --> FranjaHoraria : puntaTarifa

    class CriterioPrioridad {
      <<interface>>
      +ordenarCandidatas(candidatas, estado)
    }
    class MayorDeficitPrimero {
      -margenIntercambioC
    }
    CriterioPrioridad <|.. MayorDeficitPrimero

    class ICoreClient {
      <<interface>>
      nuevaTemperatura()
      tick()
      actualizarSitio()
      sincronizar()
      estadoById()
    }
    class CoreClient
    ICoreClient <|.. CoreClient
    CoreClient --> Core

    Core --> CriterioPrioridad
    Core --> ParametrosControl
    Core --> Sitio
    Core --> ConfiguracionHabitacion
    Core ..> Estimulo : procesa
    Core ..> Decision : produce
    Core ..> EstadoHabitacion : expone
```

**La función de decisión** (`Core.decidir`) se recalcula entera ante cada estímulo, en tres pasos:

1. **Necesidad** de cada habitación, en este orden: sin lectura → `SIN_LECTURA`; lectura más vieja
   que la vigencia (escalada por el factor de tiempo) → `LECTURA_VENCIDA`; temperatura ≥ umbral →
   `SATISFECHA` (umbral = esperada si está encendida, esperada − histéresis si está apagada);
   potencia > contratada → `EXCEDE_CONTRATADA`; si no, **tiene déficit**.
2. **Tarifa**: si la hora del control cae en la franja punta, todas las que tienen déficit →
   `TARIFA_PUNTA`.
3. **Reparto**: las que tienen déficit se ordenan con el `CriterioPrioridad` y se les asigna la
   potencia contratada en ese orden (`CALEFACCIONANDO`); la que no entra → `SIN_POTENCIA_DISPONIBLE`
   y se sigue con la siguiente (una grande no bloquea a una chica que sí entra).

**Criterio de prioridad** (`MayorDeficitPrimero`): mayor déficit (esperada − actual) primero; la que
ya está encendida suma un margen de 0.5 °C (anti-oscilación: se turnan en vez de alternar en cada
lectura); desempate por id. Es no azaroso y justificable: "la que más lejos está de su objetivo es la
que más necesita la energía ahora".

### 1.4 Engine — diagrama de clases

```mermaid
classDiagram
    direction TB
    class SitioController {
      GET /sitio
      PUT /sitio
    }
    class ControlController {
      GET /control
      POST /control/comandos
    }
    class HabitacionController {
      ABM /habitaciones
    }
    class DiagnosticoController {
      GET /diagnostico
    }
    class ApiKeyFilter {
      X-API-Key → 401
    }
    class ApiExceptionHandler {
      400/404/409 uniformes
    }

    class InventarioService {
      +reemplazar(Inventario)
      +actual()
      ABM
    }
    class ValidadorInventario {
      +validar(Inventario) List~String~
    }
    class RepositorioInventario {
      +guardar()
      +cargar()  «JSON atómico»
    }
    class InventarioAplicado {
      <<evento>>
    }

    class ControlService {
      +iniciar(fechaHora, zona, factor)
      +detener()
      +estado()
      +alRecibirLectura()
      tick()
      reconciliar()
    }
    class RelojControl {
      fechaHora
      factorTiempo
      iniciadoEn
      zona
      +ahora(relojReal)
    }

    class CoreAdapter {
      +configurar()
      +alLlegarLectura()
      +alTick()
      +sincronizar()
      +habilitar()
      +deshabilitar()
      +reconciliar()
    }
    class ClienteSwitch {
      <<interface>>
      +comandar(url, id, accion)
      +consultar(url, id)
    }
    class ClienteSwitchRest

    class LecturaMqttListener {
      +conectar(broker)
      suscribe topicTermostato
    }
    class PayloadTermostato {
      tC
      ts
    }
    class RegistroEntorno {
      POST /controladores con reintentos
      GET /sitio (pull)
    }

    SitioController --> InventarioService
    HabitacionController --> InventarioService
    ControlController --> ControlService
    DiagnosticoController --> CoreAdapter
    InventarioService --> ValidadorInventario
    InventarioService --> RepositorioInventario
    InventarioService ..> InventarioAplicado : publica
    InventarioAplicado ..> ControlService : escucha
    InventarioAplicado ..> LecturaMqttListener : escucha
    ControlService --> RelojControl
    ControlService --> CoreAdapter
    CoreAdapter --> CoreClient : ICoreClient
    CoreAdapter --> ClienteSwitch
    ClienteSwitch <|.. ClienteSwitchRest
    LecturaMqttListener --> ControlService
    LecturaMqttListener ..> PayloadTermostato
    RegistroEntorno --> LecturaMqttListener : broker informado
    RegistroEntorno --> InventarioService
```

Decisiones de diseño relevantes:

- **Un solo punto de contacto con el core** (`CoreAdapter`), que serializa las llamadas: el core no es
  thread-safe y le llegan eventos de MQTT, del temporizador y de REST.
- **Estado deseado vs estado conocido.** El adaptador recuerda la última decisión de cada habitación y
  el último estado *conocido* de su switch (por la respuesta del comando o por la consulta §4), y solo
  comanda cuando difieren. La reconciliación periódica corrige un switch que reinició
  (`DESCONOCIDO`), que alguien movió a mano o cuyo comando se perdió.
- **El reloj lo pone el engine.** Las lecturas se estampan con la hora del control al llegar (no con
  el `ts` del payload), así el core decide siempre con el reloj sincronizado (§5.2), también con
  tiempo acelerado.
- **Inventario como única fuente de verdad**, persistido en un archivo JSON con escritura atómica. El
  ABM de habitaciones opera sobre el mismo inventario.
- **Desacople por evento** (`InventarioAplicado`): el inventario no conoce a quién le interesa; el
  control y el listener MQTT lo adoptan en caliente.

---

## 2. Escenarios (+1): secuencias del manejo de eventos

### 2.1 Arranque, registro y puesta en marcha (§6, §5.1, §5.2)

```mermaid
sequenceDiagram
    autonumber
    participant Eng as engine (RegistroEntorno)
    participant Env as Entorno (simulador / checker)
    participant Inv as InventarioService
    participant Mq as LecturaMqttListener
    participant Ctl as ControlService
    participant Ad as CoreAdapter
    participant Core as core
    participant Sw as Switch (REST)

    Eng->>Inv: cargarGuardado() (si hay inventario persistido)
    loop hasta 201 (espera 2, 4, 8 … 30 s)
        Eng->>Env: POST /controladores {etiqueta, baseUrl, apiKey}
    end
    Env-->>Eng: 201 {mqtt: {host, puerto}}
    Eng->>Mq: conectar(tcp://host:puerto)
    Env->>Inv: PUT /sitio (inventario §1)
    Inv->>Inv: validar → grabar (atómico) → en uso
    Inv-->>Ctl: evento InventarioAplicado
    Ctl->>Ad: configurar(sitio, habitaciones)
    Ad->>Core: ConfiguracionActualizada
    Inv-->>Mq: evento InventarioAplicado → subscribe(topicTermostato…)
    Env->>Ctl: POST /control/comandos {INICIAR, fechaHora, factorTiempo}
    Ctl->>Ctl: RelojControl(fechaHora, factor, ahoraReal, zona)
    Ctl->>Ad: sincronizar(fechaHora, factor)
    Ad->>Core: Sincronizacion
    Ctl->>Ad: habilitar()
    loop cada habitación
        Ad->>Sw: GET /switches/{id}  (§4)
        Sw-->>Ad: DESCONOCIDO
        Ad->>Sw: POST /switches/{id}/comandos {OFF}  (§3)
    end
    Ctl->>Ctl: arranca temporizador (tick 1 s, reconciliación 15 s)
```

### 2.2 Llega una lectura de temperatura (evento MQTT)

```mermaid
sequenceDiagram
    autonumber
    participant T as Termostato
    participant B as Broker
    participant Mq as LecturaMqttListener
    participant Ctl as ControlService
    participant Ad as CoreAdapter
    participant Core as core
    participant Sw as Switch

    T->>B: publish ht-room1/status/temperature:0 {"tC":19.3,"ts":…}
    B->>Mq: messageArrived(topic, payload)
    Mq->>Mq: habitación = mapa[topic] (nunca por el id del payload)
    Mq->>Mq: PayloadTermostato.parsear → tC (inválido ⇒ se descarta)
    Mq->>Ctl: alRecibirLectura("room1", 19.3)
    alt control DETENIDO
        Ctl-->>Mq: se ignora
    else EN_EJECUCION
        Ctl->>Ad: alLlegarLectura("room1", 19.3, relojControl.ahora())
        Ad->>Core: procesar(NuevaLectura)
        Core->>Core: descarta si es inválida / fuera de orden
        Core->>Core: decidir(): necesidad → tarifa → reparto
        Core-->>Ad: [room1 ON CALEFACCIONANDO, room2 OFF SIN_POTENCIA_DISPONIBLE]
        loop decisiones distintas del estado conocido del switch
            Ad->>Sw: POST /switches/sw-room1/comandos {ON}
            alt responde 200
                Sw-->>Ad: {estado: ON}
                Ad->>Ad: estadoConocido[room1] = ON
            else falla / timeout
                Ad->>Ad: estado desconocido, no reintentar por 5 s<br/>(no bloquea a los demás switches)
            end
        end
    end
```

### 2.3 Evento temporal: entrada y salida de la franja punta

```mermaid
sequenceDiagram
    autonumber
    participant Tm as Temporizador (cada 1 s real)
    participant Ctl as ControlService
    participant Ad as CoreAdapter
    participant Core as core
    participant Sw as Switches

    Tm->>Ctl: tick()
    Ctl->>Ad: alTick(relojControl.ahora())  [p. ej. lunes 17:00:00]
    Ad->>Core: procesar(Tick)
    Core->>Core: enPunta() = true → con déficit ⇒ TARIFA_PUNTA
    Core->>Core: lecturas vencidas ⇒ LECTURA_VENCIDA
    Core-->>Ad: todas OFF
    Ad->>Sw: POST …/comandos {OFF} (solo las que estaban ON)
    Note over Tm,Sw: … 23:00:00 (fin de la franja, exclusivo) …
    Tm->>Ctl: tick()
    Ctl->>Ad: alTick(23:00:00)
    Ad->>Core: procesar(Tick)
    Core-->>Ad: las que tienen déficit vuelven a ON (respetando la contratada)
    Ad->>Sw: POST …/comandos {ON}
```

### 2.4 Cambio de configuración en caliente (`PUT /sitio` con el control en ejecución)

```mermaid
sequenceDiagram
    autonumber
    participant Op as Operador
    participant API as SitioController
    participant Inv as InventarioService
    participant Ctl as ControlService
    participant Ad as CoreAdapter
    participant Core as core
    participant Mq as LecturaMqttListener

    Op->>API: PUT /sitio (potenciaContratadaKW más baja)
    API->>Inv: reemplazar(inventario)
    alt inválido
        Inv-->>API: InventarioInvalidoException
        API-->>Op: 400 {errores: [...]} (queda el anterior)
    else válido
        Inv->>Inv: grabar (temporal + rename atómico)
        Inv-->>Ctl: InventarioAplicado
        Ctl->>Ad: configurar(...)
        Ad->>Core: ConfiguracionActualizada
        Core-->>Ad: decisiones con el tope nuevo
        Ad->>Ad: apaga de inmediato lo que ya no entra
        Inv-->>Mq: InventarioAplicado → (des)suscribe topics
        API-->>Op: 200 {sitio, habitaciones}
    end
```

---

## 3. Vista de procesos (adelanto — se completa en la Iteración 5)

| Hilo | Origen | Qué hace |
|---|---|---|
| Tomcat (HTTP) | requests REST | `PUT /sitio`, `INICIAR`/`DETENER`, consultas |
| Paho callback | broker MQTT | `messageArrived` → `ControlService.alRecibirLectura` |
| `mqtt-conexion` | propio | conexión, reintentos cada 5 s y (re)suscripciones (nunca desde el hilo de Paho: deadlock) |
| `control-temporizador` | propio | tick cada 1 s real y reconciliación cada 15 s |
| `registro-entorno` | propio | registro §6 con reintentos exponenciales |

Todos convergen en `CoreAdapter` (métodos `synchronized`), que es el único que toca el core.
**Fallas previstas en el diseño** (endurecimiento y pruebas en It. 5): broker caído al arrancar o
durante la ejecución (reintento + re-suscripción), entorno que tarda en levantar (reintento del
registro), switch caído o lento (timeouts cortos, espera entre reintentos, reconciliación),
termostato muerto (vigencia de lecturas en el core), reinicio del controlador (inventario persistido).

## 4. Vista de desarrollo (adelanto)

```
modules/
├── core/      uy.edu.utec.iiss.core          Core, CriterioPrioridad, MayorDeficitPrimero
│              uy.edu.utec.iiss.core.model    Estimulo, Decision, Motivo, Sitio, ...
│              uy.edu.utec.iiss.core.cliente  ICoreClient / CoreClient (fachada)
└── engine/    uy.edu.utec.iiss.engine
               ├── inventario/  §1 y §5.1: modelo, validación, persistencia
               ├── control/     §5.2: reloj del control, INICIAR/DETENER, temporizador
               ├── adaptador/   CoreAdapter (único puente con el core)
               ├── switches/    §3/§4: cliente REST de los switches
               ├── mqtt/        §2: consumidor de los termostatos
               ├── registro/    §6: registro ante el entorno y arranque
               ├── rest/        controllers, filtro X-API-Key, errores uniformes
               └── config/      armado del core y del adaptador
```
