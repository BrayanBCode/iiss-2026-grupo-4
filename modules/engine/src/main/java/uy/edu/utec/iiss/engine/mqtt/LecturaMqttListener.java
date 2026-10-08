package uy.edu.utec.iiss.engine.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import uy.edu.utec.iiss.engine.model.Lectura;
import uy.edu.utec.iiss.engine.service.ControladorService;
import uy.edu.utec.iiss.engine.service.LecturaService;

/**
 * Reemplaza al módulo subscriber: desde que la aplicación arranca queda
 * suscripta por MQTT a las lecturas de temperatura
 * ("{idTermostato}/status/temperature:0") y, por cada mensaje:
 *
 *   1. guarda la lectura en la base (siempre, esté o no iniciado el Controlador);
 *   2. se la pasa al Controlador, que solo actúa si fue iniciado con
 *      POST /controlador/iniciar.
 *
 * Es la única conexión MQTT del engine: el Controlador ya no abre la suya.
 */
@Component
public class LecturaMqttListener implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(LecturaMqttListener.class);

    static final String TOPICO_TEMPERATURA = "+/status/temperature:0";
    private static final long SEGUNDOS_ENTRE_REINTENTOS = 5;

    private final LecturaService lecturaService;
    private final ControladorService controladorService;
    private final String brokerUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService reintentos = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mqtt-reconexion");
        t.setDaemon(true);
        return t;
    });

    private volatile MqttClient client;

    public LecturaMqttListener(LecturaService lecturaService,
                               ControladorService controladorService,
                               @Value("${mqtt.broker.url}") String brokerUrl) {
        this.lecturaService = lecturaService;
        this.controladorService = controladorService;
        this.brokerUrl = brokerUrl;
    }

    /** Se conecta recién cuando la aplicación terminó de arrancar (base lista, migraciones aplicadas). */
    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        conectar();
    }

    private void conectar() {
        MqttClient nuevo = null;
        try {
            nuevo = new MqttClient(brokerUrl, "engine-lecturas-" + UUID.randomUUID(), new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            // Una vez conectado, Paho reconecta solo si el broker se cae; como la
            // sesión es limpia hay que re-suscribirse (ver connectComplete).
            options.setAutomaticReconnect(true);

            nuevo.setCallback(this);
            nuevo.connect(options);
            nuevo.subscribe(TOPICO_TEMPERATURA);

            this.client = nuevo;
            log.info("Conectado al broker MQTT en {}. Suscrito a '{}'.", brokerUrl, TOPICO_TEMPERATURA);
        } catch (MqttException e) {
            log.error("No se pudo conectar/suscribir al broker MQTT en {}: {}. Reintento en {} s.",
                    brokerUrl, e.getMessage(), SEGUNDOS_ENTRE_REINTENTOS);
            cerrarSilencioso(nuevo);
            reintentos.schedule(this::conectar, SEGUNDOS_ENTRE_REINTENTOS, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    void detener() {
        reintentos.shutdownNow();
        cerrarSilencioso(client);
        client = null;
    }

    private void cerrarSilencioso(MqttClient c) {
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

    // --- Callbacks de Paho ---

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        // La primera conexión ya suscribe en conectar(); acá solo la reconexión automática.
        if (!reconnect) {
            return;
        }
        // OJO: este método corre en el hilo de callbacks de Paho. MqttClient.subscribe()
        // bloquea esperando la confirmación del broker, y esa confirmación la procesa
        // ESE MISMO hilo: si se suscribe desde acá queda trabado para siempre (deadlock)
        // y el engine deja de recibir mensajes. Por eso se delega a otro hilo.
        try {
            reintentos.execute(() -> resuscribir(serverURI));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            log.debug("Reconexión durante el apagado del engine: no se re-suscribe.");
        }
    }

    private void resuscribir(String serverURI) {
        try {
            client.subscribe(TOPICO_TEMPERATURA);
            log.info("Reconectado al broker MQTT ({}). Suscripción restablecida.", serverURI);
        } catch (MqttException e) {
            log.error("Reconectado al broker pero falló la re-suscripción: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.error("Conexión perdida con el broker MQTT ({}): {}. Paho intentará reconectar.",
                brokerUrl, cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // Nunca dejar escapar una excepción: Paho cierra la conexión si el callback lanza.
        try {
            procesarMensaje(topic, message.getPayload());
        } catch (Exception e) {
            log.error("Error inesperado al procesar el mensaje del tópico '{}': {}", topic, e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // No se usa: el engine solo se suscribe, no publica.
    }

    // --- Lógica de un mensaje (visible en el paquete para poder probarla sin broker) ---

    void procesarMensaje(String topic, byte[] payload) {
        String contenido = new String(payload, StandardCharsets.UTF_8);
        log.info("[MENSAJE RECIBIDO] Tópico: {} -> {}", topic, contenido);

        try {
            // El tópico es "{idTermostato}/status/temperature:0"
            int barra = topic.indexOf('/');
            if (barra <= 0) {
                log.warn("Tópico sin id de dispositivo ('{}'), se descarta el mensaje.", topic);
                return;
            }
            String idTermostato = topic.substring(0, barra);

            JsonNode json = objectMapper.readTree(contenido);
            double temperaturaC = json.path("tC").asDouble();
            double temperaturaF = json.path("tF").asDouble();
            long epochMili = json.path("ts").asLong();

            Optional<Lectura> lectura = lecturaService.registrar(idTermostato, temperaturaC, temperaturaF, epochMili);
            if (lectura.isEmpty()) {
                log.warn("Dispositivo '{}' no está asignado a ninguna habitación, se descarta el mensaje.",
                        idTermostato);
                return;
            }

            controladorService.alRecibirLectura(lectura.get());
        } catch (Exception e) {
            log.error("Error al procesar el mensaje del tópico '{}': {}", topic, e.getMessage());
        }
    }

}