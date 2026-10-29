package uy.edu.utec.iiss.engine.mqtt;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import uy.edu.utec.iiss.engine.control.ControlService;
import uy.edu.utec.iiss.engine.inventario.Inventario;
import uy.edu.utec.iiss.engine.inventario.InventarioAplicado;

/**
 * Consumidor MQTT de los termostatos (estándar v2, §2).
 *
 * <ul>
 *   <li>Se suscribe EXACTAMENTE a los {@code topicTermostato} del inventario en uso, y re-suscribe
 *       en caliente cuando cambia el inventario.
 *   <li>La habitación se resuelve por el topic, nunca por el {@code id} del payload.
 *   <li>El broker no se hardcodea: lo informa el registro ante el entorno (§6) o, si no hay
 *       registro, la propiedad {@code mqtt.broker.url}.
 *   <li>Si el broker no está o se cae, reintenta cada {@code mqtt.reconexion-max-segundos} (5 s por
 *       defecto) con un cliente nuevo; al reconectar restablece las suscripciones (la sesión es
 *       limpia).
 * </ul>
 *
 * <p>No se usa la reconexión automática de Paho: su espera entre reintentos es un campo estático
 * (la comparten todos los clientes de la JVM y se duplica en cada intento fallido), y un cliente
 * que se cierra en medio de un reintento no se cierra ("conexión ya en curso") y sigue
 * reconectándose para siempre. Reconectando desde el hilo propio, el cliente perdido ya está
 * desconectado cuando se cierra y al apagar no queda nada vivo.
 */
@Component
public class LecturaMqttListener implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(LecturaMqttListener.class);
    private static final int QOS = 1;

    private final ControlService controlService;
    private final String clientIdBase;
    private final long segundosEntreReintentos;
    private final ScheduledExecutorService hilo =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "mqtt-conexion");
                        t.setDaemon(true);
                        return t;
                    });

    private final Object candado = new Object();
    private volatile Map<String, String> habitacionPorTopic = Map.of();
    private final Set<String> suscritos = new HashSet<>();
    private String brokerUrl;
    private MqttClient client;
    private long generacion; // invalida reintentos de una conexión anterior al cambiar de broker

    public LecturaMqttListener(
            ControlService controlService,
            @Value("${controlador.etiqueta:ecowarm-grupo4}") String etiqueta,
            @Value("${mqtt.reconexion-max-segundos:5}") int reconexionMaxSegundos) {
        this.controlService = controlService;
        this.clientIdBase = etiqueta;
        this.segundosEntreReintentos = reconexionMaxSegundos;
    }

    /**
     * Conecta (o reconecta) al broker indicado. Si no está disponible, reintenta en segundo plano.
     */
    public void conectar(String nuevoBrokerUrl) {
        synchronized (candado) {
            if (nuevoBrokerUrl.equals(brokerUrl) && client != null && client.isConnected()) {
                return;
            }
            cerrar(client);
            client = null;
            suscritos.clear();
            brokerUrl = nuevoBrokerUrl;
            long esta = ++generacion;
            hilo.execute(() -> intentarConexion(esta, false));
        }
    }

    private void intentarConexion(long esta, boolean reconexion) {
        synchronized (candado) {
            if (esta != generacion || client != null) {
                return;
            }
            MqttClient nuevo = null;
            try {
                nuevo =
                        new MqttClient(
                                brokerUrl,
                                clientIdBase + "-" + UUID.randomUUID(),
                                new MemoryPersistence());
                MqttConnectOptions opciones = new MqttConnectOptions();
                opciones.setCleanSession(true);
                opciones.setAutomaticReconnect(false); // se reconecta desde el hilo propio
                opciones.setConnectionTimeout(5);
                nuevo.setCallback(this);
                nuevo.connect(opciones);
                client = nuevo;
                if (reconexion) {
                    log.info(
                            "Reconectado al broker MQTT ({}). Se restablecen las suscripciones.",
                            brokerUrl);
                } else {
                    log.info("Conectado al broker MQTT en {}.", brokerUrl);
                }
                sincronizarSuscripciones();
            } catch (MqttException | IllegalArgumentException e) {
                log.error(
                        "No se pudo conectar al broker MQTT en {}: {}. Reintento en {} s.",
                        brokerUrl,
                        e.getMessage(),
                        segundosEntreReintentos);
                cerrar(nuevo);
                programar(() -> intentarConexion(esta, reconexion), segundosEntreReintentos);
            }
        }
    }

    /** Se perdió la conexión: se descarta el cliente caído y se reintenta con uno nuevo. */
    private void reconectar() {
        synchronized (candado) {
            if (client == null || client.isConnected()) {
                return; // ya se reemplazó o no hay nada que reconectar
            }
            cerrar(client);
            client = null;
            suscritos.clear();
            long esta = generacion;
            programar(() -> intentarConexion(esta, true), segundosEntreReintentos);
        }
    }

    /** Nuevo inventario en uso: se ajustan las suscripciones a sus topics. */
    @EventListener
    public void alAplicarseInventario(InventarioAplicado evento) {
        Map<String, String> mapa = new HashMap<>();
        for (Inventario.Habitacion h : evento.inventario().habitaciones()) {
            mapa.put(h.topicTermostato(), h.id());
        }
        habitacionPorTopic = Map.copyOf(mapa);
        programar(
                () -> {
                    synchronized (candado) {
                        sincronizarSuscripciones();
                    }
                },
                0);
    }

    /** Llamar con el candado tomado y desde el hilo propio (nunca desde un callback de Paho). */
    private void sincronizarSuscripciones() {
        if (client == null || !client.isConnected()) {
            return;
        }
        Set<String> deseados = habitacionPorTopic.keySet();
        try {
            for (String topic : Set.copyOf(suscritos)) {
                if (!deseados.contains(topic)) {
                    client.unsubscribe(topic);
                    suscritos.remove(topic);
                    log.info("Desuscrito de '{}'.", topic);
                }
            }
            for (String topic : deseados) {
                if (!suscritos.contains(topic)) {
                    client.subscribe(topic, QOS);
                    suscritos.add(topic);
                    log.info(
                            "Suscrito a '{}' (habitación '{}').",
                            topic,
                            habitacionPorTopic.get(topic));
                }
            }
        } catch (MqttException e) {
            log.error(
                    "Falló la suscripción MQTT: {}. Se reintenta en {} s.",
                    e.getMessage(),
                    segundosEntreReintentos);
            programar(
                    () -> {
                        synchronized (candado) {
                            sincronizarSuscripciones();
                        }
                    },
                    segundosEntreReintentos);
        }
    }

    private void programar(Runnable tarea, long segundos) {
        try {
            hilo.schedule(tarea, segundos, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("El engine se está apagando: no se programa más trabajo MQTT.");
        }
    }

    @PreDestroy
    void detener() {
        hilo.shutdownNow();
        synchronized (candado) {
            generacion++;
            cerrar(client);
            client = null;
        }
    }

    private void cerrar(MqttClient c) {
        if (c == null) {
            return;
        }
        try {
            if (c.isConnected()) {
                c.disconnect();
            }
        } catch (MqttException e) {
            log.debug("Error al desconectar el cliente MQTT: {}", e.getMessage());
        }
        try {
            c.close();
        } catch (MqttException e) {
            // Tras un connect() fallido Paho puede quedar "conectando" y rechazar el close().
            try {
                c.disconnectForcibly(0, 0, false);
                c.close();
            } catch (MqttException otra) {
                log.warn("Error al cerrar el cliente MQTT: {}", otra.getMessage());
            }
        }
    }

    public boolean conectado() {
        MqttClient c = client;
        return c != null && c.isConnected();
    }

    // --- Callbacks de Paho ---

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        // Sin reconexión automática de Paho: intentarConexion() ya suscribe.
    }

    @Override
    public void connectionLost(Throwable causa) {
        log.error(
                "Conexión perdida con el broker MQTT ({}): {}. Reintento en {} s.",
                brokerUrl,
                causa == null ? "?" : causa.getMessage(),
                segundosEntreReintentos);
        // Corre en el hilo de Paho: cerrar el cliente desde acá puede trabarse. Se delega.
        programar(this::reconectar, 0);
    }

    @Override
    public void messageArrived(String topic, MqttMessage mensaje) {
        // Nunca dejar escapar una excepción: Paho cierra la conexión si el callback lanza.
        try {
            procesarMensaje(topic, mensaje.getPayload());
        } catch (Exception e) {
            log.error("Error inesperado al procesar el mensaje de '{}': {}", topic, e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // El engine no publica.
    }

    /** Lógica de un mensaje, visible en el paquete para probarla sin broker. */
    void procesarMensaje(String topic, byte[] payload) {
        String idHabitacion = habitacionPorTopic.get(topic);
        if (idHabitacion == null) {
            log.debug(
                    "Mensaje de un topic que no está en el inventario ('{}'), se descarta.", topic);
            return;
        }
        PayloadTermostato.parsear(payload)
                .ifPresentOrElse(
                        p -> {
                            // DEBUG: una línea por mensaje tapa el resto del log (≈95 % en una
                            // corrida contra el simulador). Se sube en application.properties.
                            log.debug(
                                    "[TEMPERATURA] '{}' (habitación '{}'): {} °C",
                                    topic,
                                    idHabitacion,
                                    p.temperaturaC());
                            controlService.alRecibirLectura(idHabitacion, p.temperaturaC(), p.ts());
                        },
                        () ->
                                log.warn(
                                        "Payload inválido en '{}', se descarta: {}",
                                        topic,
                                        new String(payload, StandardCharsets.UTF_8)));
    }
}
