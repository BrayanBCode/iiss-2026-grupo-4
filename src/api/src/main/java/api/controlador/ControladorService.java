package api.controlador;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import api.client.SwitchStubClient;
import api.exception.ControladorException;
import api.exception.ControladorNoIniciadoException;
import api.exception.ControladorYaIniciadoException;
import api.model.AccionSwitch;
import api.model.Habitacion;
import api.repository.HabitacionRepository;
import jakarta.annotation.PreDestroy;

/**
 * El "Controlador" pedido en la Iteración 3: se comporta como un termostato
 * simple (el de un calefón). Mientras está iniciado, queda suscripto por
 * MQTT a las lecturas de temperatura (mismo tópico que usa el módulo
 * subscriber: "{idTermostato}/status/temperature:0") y, por cada lectura:
 *
 *   - la compara con la temperaturaEsperada de la habitación dueña de ese
 *     termostato;
 *   - si la medida está por encima de la esperada, apaga su switch (OFF);
 *   - si está por debajo, lo prende (ON);
 *   - acciona ese switch contra el stub, vía {@link SwitchStubClient}.
 *
 * Los comandos iniciar()/parar() son los que expone ControladorController
 * (POST /controlador/iniciar y /controlador/parar) y solo abren/cierran
 * esta suscripción; no hay lógica de negocio en el controller REST.
 */
@Service
public class ControladorService {

    private static final Logger log = LoggerFactory.getLogger(ControladorService.class);

    /** Mismo tópico que suscribe subscriber.AppController en la Iteración 2. */
    private static final String TOPICO_TEMPERATURA = "+/status/temperature:0";

    private final HabitacionRepository habitacionRepository;
    private final SwitchStubClient switchStubClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String brokerUrl;

    private final AtomicBoolean corriendo = new AtomicBoolean(false);
    private MqttClient mqttClient;

    public ControladorService(HabitacionRepository habitacionRepository,
                               SwitchStubClient switchStubClient,
                               @Value("${mqtt.broker.url}") String brokerUrl) {
        this.habitacionRepository = habitacionRepository;
        this.switchStubClient = switchStubClient;
        this.brokerUrl = brokerUrl;
    }

    /** Comando "iniciar controlador": abre la suscripción MQTT. */
    public synchronized void iniciar() {
        if (corriendo.get()) {
            throw new ControladorYaIniciadoException();
        }
        try {
            String clientId = "api-controlador-" + UUID.randomUUID();
            MqttClient client = new MqttClient(brokerUrl, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.error("Controlador: conexión perdida con el broker MQTT ({}): {}",
                            brokerUrl, cause.getMessage());
                    corriendo.set(false);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    procesarLectura(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // El controlador solo se suscribe, no publica.
                }
            });

            client.connect(options);
            client.subscribe(TOPICO_TEMPERATURA);

            this.mqttClient = client;
            corriendo.set(true);
            log.info("Controlador iniciado: suscripto a '{}' en {}", TOPICO_TEMPERATURA, brokerUrl);
        } catch (MqttException e) {
            throw new ControladorException("No se pudo iniciar el controlador: " + e.getMessage(), e);
        }
    }

    /** Comando "parar controlador": cierra la suscripción MQTT. */
    public synchronized void parar() {
        if (!corriendo.get() || mqttClient == null) {
            throw new ControladorNoIniciadoException();
        }
        try {
            mqttClient.disconnect();
            mqttClient.close();
            log.info("Controlador detenido.");
        } catch (MqttException e) {
            throw new ControladorException("No se pudo detener el controlador: " + e.getMessage(), e);
        } finally {
            mqttClient = null;
            corriendo.set(false);
        }
    }

    public boolean estaCorriendo() {
        return corriendo.get();
    }

    /** Por las dudas: si el contenedor se apaga, no dejar el cliente MQTT colgado. */
    @PreDestroy
    void alDestruir() {
        if (corriendo.get()) {
            try {
                parar();
            } catch (Exception e) {
                log.warn("Controlador: error al detenerlo durante el shutdown: {}", e.getMessage());
            }
        }
    }

    private void procesarLectura(String topic, MqttMessage message) {
        try {
            // El tópico es "{idTermostato}/status/temperature:0"
            String idTermostato = topic.substring(0, topic.indexOf('/'));

            JsonNode json = objectMapper.readTree(message.getPayload());
            double temperaturaMedida = json.get("tC").asDouble();

            Optional<Habitacion> habitacionOpt = habitacionRepository.findByIdTermostato(idTermostato);
            if (habitacionOpt.isEmpty()) {
                log.warn("Controlador: termostato '{}' no está asignado a ninguna habitación, se descarta la lectura.",
                        idTermostato);
                return;
            }

            Habitacion habitacion = habitacionOpt.get();
            Double esperada = habitacion.getTemperaturaEsperada();
            if (esperada == null) {
                log.warn("Controlador: la habitación '{}' no tiene temperatura esperada configurada, se descarta la lectura.",
                        habitacion.getNombre());
                return;
            }

            // Termostato simple: por encima de lo esperado, apaga; por debajo, prende.
            AccionSwitch accion = temperaturaMedida > esperada ? AccionSwitch.OFF : AccionSwitch.ON;

            log.info("Controlador: habitación '{}' medida={}ºC esperada={}ºC -> switch '{}' {}",
                    habitacion.getNombre(), temperaturaMedida, esperada, habitacion.getIdSwitch(), accion);

            switchStubClient.accionar(habitacion.getIdSwitch(), accion);
        } catch (Exception e) {
            // Una lectura mal formada o un switch caído no puede tumbar la suscripción MQTT.
            log.error("Controlador: error al procesar lectura del tópico '{}': {}", topic, e.getMessage());
        }
    }
}
