package uy.edu.utec.iiss.engine.mqtt;

import jakarta.annotation.PreDestroy;
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

/**
 * Consumidor MQTT de los termostatos (estándar v2, §2).
 *
 * <ul>
 *   <li>Se suscribe EXACTAMENTE a los {@code topicTermostato} del inventario en
 *       uso, y re-suscribe en caliente cuando cambia el inventario.</li>
 *   <li>La habitación se resuelve por el topic, nunca por el {@code id} del payload.</li>
 *   <li>El broker no se hardcodea: lo informa el registro ante el entorno (§6)
 *       o, si no hay registro, la propiedad {@code mqtt.broker.url}.</li>
 *   <li>Si el broker no está o se cae, reintenta cada 5 s; al reconectar
 *       restablece las suscripciones (la sesión es limpia).</li>
 * </ul>
 */
@Component
public class LecturaMqttListener implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(LecturaMqttListener.class);
    private static final long SEGUNDOS_ENTRE_REINTENTOS = 5;
    private static final int QOS = 1;

    private final ControlService controlService;
    private final String clientIdBase;
<<<<<<< Updated upstream
    private final ScheduledExecutorService hilo = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mqtt-conexion");
        t.setDaemon(true);
        return t;
    });
=======
    private final int reconexionMaxMs;
    private final ScheduledExecutorService hilo =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "mqtt-conexion");
                        t.setDaemon(true);
                        return t;
                    });
>>>>>>> Stashed changes

    private final Object candado = new Object();
    private volatile Map<String, String> habitacionPorTopic = Map.of();
    private final Set<String> suscritos = new HashSet<>();
    private String brokerUrl;
    private MqttClient client;
    private long generacion;   // invalida reintentos de una conexión anterior al cambiar de broker

<<<<<<< Updated upstream
    public LecturaMqttListener(ControlService controlService,
                               @Value("${controlador.etiqueta:ecowarm-grupo4}") String etiqueta) {
=======
    public LecturaMqttListener(
            ControlService controlService,
            @Value("${controlador.etiqueta:ecowarm-grupo4}") String etiqueta,
            @Value("${mqtt.reconexion-max-segundos:5}") int reconexionMaxSegundos) {
>>>>>>> Stashed changes
        this.controlService = controlService;
        this.clientIdBase = etiqueta;
        this.reconexionMaxMs = reconexionMaxSegundos * 1000;
    }

    /** Conecta (o reconecta) al broker indicado. Si no está disponible, reintenta en segundo plano. */
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
            hilo.execute(() -> intentarConexion(esta));
        }
    }

    private void intentarConexion(long esta) {
        synchronized (candado) {
            if (esta != generacion) {
                return;
            }
            MqttClient nuevo = null;
            try {
                nuevo = new MqttClient(brokerUrl, clientIdBase + "-" + UUID.randomUUID(), new MemoryPersistence());
                MqttConnectOptions opciones = new MqttConnectOptions();
                opciones.setCleanSession(true);
                opciones.setAutomaticReconnect(true);
                // Paho duplica la espera entre reintentos hasta 128 s: tras una caída larga el
                // broker podía volver y el engine tardar 2 min en enterarse. Se acota.
                opciones.setMaxReconnectDelay(reconexionMaxMs);
                opciones.setConnectionTimeout(5);
                nuevo.setCallback(this);
                nuevo.connect(opciones);
                client = nuevo;
                log.info("Conectado al broker MQTT en {}.", brokerUrl);
                sincronizarSuscripciones();
            } catch (MqttException | IllegalArgumentException e) {
                log.error("No se pudo conectar al broker MQTT en {}: {}. Reintento en {} s.",
                        brokerUrl, e.getMessage(), SEGUNDOS_ENTRE_REINTENTOS);
                cerrar(nuevo);
                programar(() -> intentarConexion(esta), SEGUNDOS_ENTRE_REINTENTOS);
            }
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
        programar(() -> {
            synchronized (candado) {
                sincronizarSuscripciones();
            }
        }, 0);
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
                    log.info("Suscrito a '{}' (habitación '{}').", topic, habitacionPorTopic.get(topic));
                }
            }
        } catch (MqttException e) {
            log.error("Falló la suscripción MQTT: {}. Se reintenta en {} s.", e.getMessage(), SEGUNDOS_ENTRE_REINTENTOS);
            programar(() -> {
                synchronized (candado) {
                    sincronizarSuscripciones();
                }
            }, SEGUNDOS_ENTRE_REINTENTOS);
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
            c.close();
        } catch (MqttException e) {
            log.warn("Error al cerrar el cliente MQTT: {}", e.getMessage());
        }
    }

    public boolean conectado() {
        MqttClient c = client;
        return c != null && c.isConnected();
    }

    // --- Callbacks de Paho ---

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (!reconnect) {
            return;   // la primera conexión ya suscribe en intentarConexion()
        }
        // Este método corre en el hilo de callbacks de Paho: suscribir desde acá
        // espera una confirmación que procesa ESE MISMO hilo (deadlock). Se delega.
        log.info("Reconectado al broker MQTT ({}). Se restablecen las suscripciones.", serverURI);
        programar(() -> {
            synchronized (candado) {
                suscritos.clear();
                sincronizarSuscripciones();
            }
        }, 0);
    }

    @Override
    public void connectionLost(Throwable causa) {
        log.error("Conexión perdida con el broker MQTT ({}): {}. Paho intenta reconectar.",
                brokerUrl, causa == null ? "?" : causa.getMessage());
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
            log.debug("Mensaje de un topic que no está en el inventario ('{}'), se descarta.", topic);
            return;
        }
        PayloadTermostato.parsear(payload).ifPresentOrElse(
                p -> {
                    log.info("[TEMPERATURA] '{}' (habitación '{}'): {} °C", topic, idHabitacion, p.temperaturaC());
                    controlService.alRecibirLectura(idHabitacion, p.temperaturaC());
                },
                () -> log.warn("Payload inválido en '{}', se descarta: {}", topic,
                        new String(payload, StandardCharsets.UTF_8)));
    }
}
