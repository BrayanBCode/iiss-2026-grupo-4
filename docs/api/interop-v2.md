# EcoWarm — Estándar de interoperabilidad v2

> Copia de referencia del estándar provisto por la cátedra (junto con
> [`controlador.openapi-v2.yaml`](controlador.openapi-v2.yaml) y
> [`simulador.openapi-v2.yaml`](simulador.openapi-v2.yaml)). El documento normativo es el
> original; esta copia está para que el repositorio sea autocontenido.

Contrato entre los componentes del sistema EcoWarm de control de calefacción eléctrica. Define el
inventario del sitio, los mensajes de los dispositivos y las operaciones que cada componente expone,
de modo que implementaciones independientes sean intercambiables.

Las operaciones REST de este documento están además descritas en OpenAPI 3.0, una por rol:
[`controlador.openapi-v2.yaml`](controlador.openapi-v2.yaml) (lo que un controlador debe ofrecer, §5) y
[`simulador.openapi-v2.yaml`](simulador.openapi-v2.yaml) (lo que ofrece un entorno: §1, §3, §4, §6). Son
la forma ejecutable de lo que acá se define en prosa; **ante una diferencia, manda este documento**.
Lo que no entra en OpenAPI —el mensaje MQTT del termostato (§2)— vive solo acá.

## Componentes y canales

```
  termostato ──MQTT: temperatura──▶ controlador ──REST: comandos──▶ switch
                                         │                            │
                                         └──────REST: consulta────────▶
```

| Componente | Rol |
|---|---|
| **Termostato** | Mide la temperatura del ambiente de una habitación y la publica por MQTT. No recibe comandos. |
| **Switch** | Enciende y apaga la calefacción de una habitación. Se comanda y se consulta **solo por REST**. |
| **Controlador** | Decide qué habitaciones reciben energía. Consume MQTT, comanda switches por REST, y expone su propio API. |

**Un componente es conforme** si cumple las secciones que le corresponden: el termostato §2; el
switch §3 y §4; el controlador §2 como consumidor, §3, §4 y §5. La §6 es opcional: aplica solo si
el entorno ofrece un registro, y en ese caso es obligatoria para el controlador.

---

## 1. Inventario del sitio

Documento completo del sitio. Sirve como semilla, como export/import entre implementaciones, y como
cuerpo de `PUT /sitio` (§5.1).

```json
{
  "schemaVersion": "2.0",
  "sitio": {
    "id": "casa-rodriguez",
    "nombre": "Casa Rodríguez",
    "potenciaContratadaKW": 3.7,
    "tarifa": {
      "punta": { "desde": "17:00", "hasta": "23:00", "dias": "HABILES" }
    }
  },
  "habitaciones": [
    {
      "id": "room1",
      "nombre": "Living",
      "temperaturaEsperada": 21.5,
      "potenciaKW": 1.2,
      "idTermostato": "ht-sim-room1",
      "topicTermostato": "ht-sim-room1/status/temperature:0",
      "idSwitch": "sw-sim-room1",
      "urlSwitch": "http://switches:8081"
    },
    {
      "id": "room2",
      "nombre": "Dormitorio principal",
      "temperaturaEsperada": 20.0,
      "potenciaKW": 0.8,
      "idTermostato": "ht-sim-room2",
      "topicTermostato": "ht-sim-room2/status/temperature:0",
      "idSwitch": "sw-sim-room2",
      "urlSwitch": "http://switches:8081"
    }
  ]
}
```

`schemaVersion` versiona este documento. Un consumidor debe **rechazar un major distinto**.

### Sitio

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `id` | string | sí | Identificador del sitio |
| `nombre` | string | sí | Etiqueta legible |
| `potenciaContratadaKW` | number > 0 | sí | **Potencia** contratada, en **kW** (ver nota de unidades) |
| `tarifa.punta` | franja | sí | Franja de energía cara. Todo el resto del tiempo es tarifa llana |

**Franja horaria:**

| Campo | Tipo | Regla |
|---|---|---|
| `desde` / `hasta` | string `HH:MM` | **Hora local**, 24 h. `desde` < `hasta`; una franja no cruza la medianoche (si hace falta, se parte en dos) |
| `dias` | enum `HABILES` / `TODOS` | `HABILES` = lunes a viernes |

**Nota de unidades.** `potenciaContratadaKW` y `potenciaKW` son **potencia** (kW), no energía (kWh).
El kWh mide consumo acumulado; lo contratado es el tope instantáneo: si la suma de lo encendido lo
supera, salta la llave. Es la magnitud que el control tiene que administrar.

### Habitación

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `id` | string | sí | Estable, elegido por el cliente. Único en el sitio. Si la implementación usa un serial en base, se serializa como string (`"1"`) |
| `nombre` | string (1..50) | sí | Etiqueta legible |
| `temperaturaEsperada` | number | sí | Grados Celsius, **1 decimal** |
| `potenciaKW` | number > 0 | sí | Potencia de la calefacción de esa habitación. Es lo que consume mientras su switch está en `ON` |
| `idTermostato` | string | sí | Identificador del dispositivo. Único en el sitio |
| `topicTermostato` | string | sí | Topic MQTT donde ese termostato publica (§2) |
| `idSwitch` | string | sí | Identificador del dispositivo. Único en el sitio |
| `urlSwitch` | string (URL) | sí | Base URL donde se alcanza ese switch (§3, §4). Varias habitaciones pueden compartirla |

**Reglas de consistencia:**

- `id` único;
- `idTermostato` único y no vacío en todas las habitaciones;
- `idSwitch` único y no vacío en todas las habitaciones;
- `topicTermostato` y `urlSwitch` no vacíos;
- `potenciaKW` y `potenciaContratadaKW` mayores que cero;
- los horarios de la tarifa son `HH:MM` válidos y `desde` < `hasta`.

La suma de `potenciaKW` **puede** superar la potencia contratada: no es un error de consistencia,
es la situación que el controlador tiene que administrar encendiendo por turnos.

---

## 2. Mensaje del termostato (MQTT)

**Topic:** el `topicTermostato` declarado para la habitación. Por convención:

```
<idTermostato>/status/temperature:<indiceSensor>
ht-sim-room1/status/temperature:0
```

**Payload:**

```json
{"id":0,"tC":21.4,"tF":70.5,"ts":1786840680.123}
```

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `id` | integer | sí | **Índice del sensor dentro del dispositivo**, no el id de la habitación. Es siempre `0` |
| `tC` | number | sí | Celsius, 1 decimal. Es la temperatura del **ambiente** |
| `tF` | number | no | Fahrenheit. Opcional: un consumidor no puede depender de él |
| `ts` | number | sí | Instante de la medición: **epoch en segundos con 3 decimales**, en notación decimal (`1786840680.123`) |

**Reglas duras:**

- La habitación se resuelve por el **topic**, nunca por el `id` del payload.
- QoS 0 o 1, sin `retained`.

---

## 3. Comandar el switch (REST)

```
POST {urlSwitch}/switches/{idSwitch}/comandos
Content-Type: application/json
X-API-Key: <clave>
```

```json
{ "accion": "ON" }
```

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `accion` | enum `ON` / `OFF` | sí | Único campo del cuerpo. Otro valor → `400` |

**Respuesta `200`:**

```json
{
  "idSwitch": "sw-sim-room1",
  "estado": "ON",
  "cambio": true,
  "aplicadoEn": "2026-10-01T09:15:03.120Z"
}
```

`cambio: false` significa que ya estaba en ese estado. Switch inexistente → `404`.

---

## 4. Consultar el switch (REST)

```
GET {urlSwitch}/switches/{idSwitch}
X-API-Key: <clave>
```

**Respuesta `200`:**

```json
{
  "idSwitch": "sw-sim-room1",
  "estado": "ON",
  "actualizadoEn": "2026-10-01T09:15:03.120Z"
}
```

| Campo | Tipo | Regla |
|---|---|---|
| `idSwitch` | string | El de la URL |
| `estado` | enum `ON` / `OFF` / `DESCONOCIDO` | `DESCONOCIDO` mientras nadie lo accionó desde que arrancó |
| `actualizadoEn` | date-time ISO-8601 | `null` si `estado` es `DESCONOCIDO` |

El switch debe **recordar su último estado**, no solo registrar el comando.

El switch **no reporta** temperatura, potencia ni consumo: solo su estado. La potencia de cada
habitación se declara en el inventario (`potenciaKW`).

---

## 5. API del controlador

Lo mínimo que el controlador expone para ser operado desde afuera: cargar y consultar el sitio, y
arrancar y detener el control. Todo lo demás que un controlador ofrezca —ABM de habitaciones,
diagnósticos, tableros— queda a criterio de cada implementación y fuera de este estándar.

### 5.1 Recurso sitio

```
GET /sitio        → 200, el inventario completo (§1)
PUT /sitio        → 200, reemplaza el inventario completo
```

| Regla | Detalle |
|---|---|
| **Persistencia** | El inventario recibido se **graba localmente** y sobrevive al reinicio del controlador. |
| **`GET` no es un eco** | Devuelve lo que está **efectivamente persistido y en uso**, no el último cuerpo recibido. Es la forma de verificar que la persistencia funcionó. |
| **Atómico** | Un `PUT` inválido no deja el controlador a medio configurar: se valida y se aplica entero, o se rechaza con `400` y queda la configuración anterior. |
| **En caliente** | Un `PUT` con el control `EN_EJECUCION` es válido: el controlador adopta la configuración nueva sin reiniciar. |

Cuerpo inválido o inconsistente (§1) → `400`, con el detalle en `errores`.

### 5.2 Recurso control

```
GET  /control                   → 200, estado del control
POST /control/comandos          → 200
```

**Estado (`GET /control`):**

```json
{
  "estado": "EN_EJECUCION",
  "fechaHora": "2026-10-01T09:15:03.120Z",
  "factorTiempo": 1,
  "iniciadoEn": "2026-10-01T09:15:00.000Z"
}
```

| Campo | Tipo | Regla |
|---|---|---|
| `estado` | enum `EN_EJECUCION` / `DETENIDO` | Arranca en `DETENIDO` |
| `fechaHora` | date-time ISO-8601 | **Hora del control**, no la del sistema. `null` si `DETENIDO` |
| `factorTiempo` | number | Aceleración vigente. `null` si `DETENIDO` |
| `iniciadoEn` | date-time ISO-8601 | Hora real del último `INICIAR`. `null` si `DETENIDO` |

**Comandos (`POST /control/comandos`):**

```json
{ "accion": "INICIAR", "fechaHora": "2026-10-01T09:15:03.120Z", "factorTiempo": 1 }
```

```json
{ "accion": "DETENER" }
```

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `accion` | enum `INICIAR` / `DETENER` | sí | Otro valor → `400` |
| `fechaHora` | date-time ISO-8601 | sí en `INICIAR` | Instante al que el control **sincroniza su noción del tiempo** |
| `factorTiempo` | number > 0 | no | Aceleración respecto del tiempo real. Ausente ⇒ `1` |

**Semántica del tiempo.** Tras `INICIAR`, el controlador deja de usar el reloj del sistema para todo
lo que dependa de la hora —en particular **la franja tarifaria**— y usa el reloj sincronizado:

```
ahora = fechaHora + (tiempoRealTranscurrido × factorTiempo)
```

Un controlador que no soporte aceleración debe **rechazar con `400`** un `factorTiempo` distinto de
`1`, nunca ignorarlo en silencio.

`INICIAR` sobre un control ya en ejecución **re-sincroniza** (no es error). `DETENER` sobre uno
detenido es idempotente. Al detenerse, el controlador **deja de comandar switches**; no se le exige
apagar lo que dejó encendido.

---

## 6. Registro ante el entorno *(opcional)*

Aplica **solo cuando el entorno ofrece un registro** —por ejemplo un simulador de integración—. Una
instalación real no lo tiene: ahí el controlador conoce su broker y sus dispositivos por
configuración, y esta sección no corre.

**Cuando el entorno sí lo ofrece, registrarse es obligatorio**: es lo que le dice al entorno dónde
alcanzar al controlador y con qué credencial. Sin eso, nadie puede cargarle el sitio (§5.1) ni darle
`INICIAR` (§5.2), y el lazo nunca arranca.

A cambio, el controlador se configura con **una sola dirección** —la del entorno— y recibe el resto:

```
POST {entorno}/controladores
Content-Type: application/json
X-API-Key: <clave del entorno>
```

```json
{
  "etiqueta": "control-casa-rodriguez",
  "baseUrl": "http://control-casa:8080",
  "apiKey": "la-clave-que-exige-mi-api"
}
```

| Campo | Tipo | Obligatorio | Regla |
|---|---|---|---|
| `etiqueta` | string | sí | Identifica al controlador lógico. Estable entre reinicios |
| `baseUrl` | string (URL) | sí | Dónde alcanzar el API del controlador (§5) |
| `apiKey` | string | sí | La credencial que **el entorno debe enviar** al llamar al controlador |

**Respuesta `201`:**

```json
{
  "id": "control-casa-rodriguez",
  "etiqueta": "control-casa-rodriguez",
  "baseUrl": "http://control-casa:8080",
  "registradoEn": "2026-10-01T09:14:58.000Z",
  "mqtt": { "host": "mosquitto", "puerto": 1883 }
}
```

**Reglas:**

- **Idempotente por `etiqueta`.** Un re-registro con la misma etiqueta **reemplaza** el anterior: un
  componente que cambia de dirección al recrearse no debe dejar entradas muertas.
- El controlador debe **reintentar**: el entorno puede tardar en levantar.
- El `mqtt` devuelto es el broker al que conectarse. **No se hardcodea.**

Un entorno con registro puede además servir el inventario en `GET {entorno}/sitio`, con el mismo
documento de §1. Son dos vías equivalentes para configurarse: que el entorno lo empuje
(`PUT /sitio`, §5.1) o que el controlador lo consulte. Soportar una, la otra o las dos queda a
criterio de la implementación.

---

## Convenciones transversales

- **Autenticación:** header `X-API-Key` en todas las operaciones REST. El controlador exige su clave
  a quien lo llame; el switch exige la suya. Declarado en OpenAPI como
  `securitySchemes: ApiKeyAuth`. Falta o no coincide → `401`. Cuando hay registro (§6), el
  controlador declara ahí su clave y el entorno la usa; si no, la provisión queda fuera de este
  estándar.
- **Instantes:** ISO-8601 con milisegundos (`2026-10-01T09:15:03.120Z`). La única excepción es `ts`
  del payload MQTT (§2).
- **Horas de la tarifa:** `HH:MM` en **hora local** del sistema. No hay conversión de zona horaria.
- **Temperaturas:** siempre Celsius con 1 decimal, y siempre del ambiente.
- **Potencias:** siempre kW.
- **Errores:** cuerpo uniforme.

  ```json
  { "timestamp": "2026-10-01T09:15:03.120Z", "status": 404, "mensaje": "No existe la habitación room9", "errores": null }
  ```

- **Códigos:** `200` consulta y comando, `201` + `Location` en alta, `204` en borrado,
  `400` payload inválido, `401` sin credencial, `404` inexistente, `409` identificador duplicado.
- **Idioma:** castellano en el dominio (`nombre`, `temperaturaEsperada`, `accion`, `estado`) y los
  nombres originales del sensor en el payload MQTT (`tC`, `tF`, `ts`).

## Fuera de alcance

El API interno de cada controlador (ABM de habitaciones, validaciones, diagnósticos), el histórico
de lecturas, la medición de consumo real, los precios de la tarifa y el calendario de feriados.
