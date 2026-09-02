package subscriber;

import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

public class AppController {

    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    public static void main(String[] args) {
        String broker = "tcp://mosquitto:1883";
        String clientId = "ControllerApp";
        String topic = "+/status/temperature:0";

        LecturaDAO lecturaDAO = new LecturaDAO();
        HabitacionDAO habitacionDAO = new HabitacionDAO();

        try {
            // crea y puebla las tablas de estar vacias (Se supone el cliente ya ingreso los shelly al sistema)
            habitacionDAO.crearTablaSiNoExiste();
            habitacionDAO.seedearSiVacia();
            lecturaDAO.crearTablaSiNoExiste();
            log.info("Tablas 'habitaciones' y 'lecturas' listas.");

            // Crear el cliente MQTT
            MqttClient client = new MqttClient(broker, clientId);

            // Utilizamos la configuracion default + "CleanSession" (Crea la conexión como si fuera nueva y no
            // intente recuperar una session anterior)
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            // Creamos un eventListener con una clase anónima que implementa la interface mqttCallBack
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.error("Conexión perdida con el broker: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String contenido = new String(message.getPayload());
                    log.info("[MENSAJE RECIBIDO] Tópico: " + topic + " -> " + contenido);

                    try {
                        // Obtenemos el ID del shelly desde el topico
                        String deviceId = topic.substring(0, topic.indexOf('/'));

                        // Convertimos el String a Json para obtener los datos por clave
                        JSONObject json = new JSONObject(contenido);
                        double temperaturaC = json.getDouble("tC");
                        double temperaturaF = json.getDouble("tF");
                        double epochSegundos = json.getDouble("ts");
                        Instant timestamp = Instant.ofEpochMilli((long) (epochSegundos * 1000));

                        // Preguntamos si el Shelly remitente fue ingresado por el cliente (Osea esta en la BD)
                        Optional<Habitacion> habitacion = habitacionDAO.buscarPorTermostatoId(deviceId);
                        if (habitacion.isEmpty()) {
                            log.warn("Dispositivo '" + deviceId + "' no está asignado a ninguna habitación — se descarta el mensaje.");
                            return;
                        }

                        // Persistimos los datos en la tabla lecturas
                        lecturaDAO.guardar(habitacion.get().id(), temperaturaC, temperaturaF, timestamp);
                    } catch (Exception e) {
                        log.error("Error al persistir el mensaje: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // No se usa en un suscriptor
                }
            });

            client.connect(options);
            log.info("Conectando al broker MQTT en: " + broker);
            log.info("✅ Conectado con éxito.");

            client.subscribe(topic);
            log.info("🎧 Suscrito al tópico: " + topic + ". Esperando mensajes...");

        } catch (MqttException | java.sql.SQLException e) {
            log.error("Error al iniciar el subscriber: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
