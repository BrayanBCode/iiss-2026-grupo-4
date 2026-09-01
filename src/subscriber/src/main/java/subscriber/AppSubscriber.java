package subscriber;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Optional;

// Cambiar nombre a Controller o similar
public class AppSubscriber {

    public static void main(String[] args) {
        String broker = "tcp://mosquitto:1883";
        String clientId = "JavaSubscriberModule";
        String topic = "+/status/temperature:0";

        LecturaDAO lecturaDAO = new LecturaDAO();
        HabitacionDAO habitacionDAO = new HabitacionDAO();

        try {
            // 0. Preparar la base: tabla de habitaciones (+ asignación inicial
            //    del cliente) y tabla de lecturas, antes de arrancar a escuchar
            habitacionDAO.crearTablaSiNoExiste();
            habitacionDAO.seedearSiVacia();
            lecturaDAO.crearTablaSiNoExiste();
            System.out.println("🗄️ Tablas 'habitaciones' y 'lecturas' listas.");

            // 1. Crear el cliente MQTT con la librería Eclipse Paho
            MqttClient client = new MqttClient(broker, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            // 2. Definir qué hacer cuando lleguen mensajes
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("Conexión perdida con el broker: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String contenido = new String(message.getPayload());
                    System.out.println("[MENSAJE RECIBIDO] Tópico: " + topic + " -> " + contenido);

                    try {
                        // El deviceId es el primer segmento del topic:
                        // "shellyhtg3-a1b2c3d4e5f6/status/temperature:0" -> "shellyhtg3-a1b2c3d4e5f6"
                        String deviceId = topic.substring(0, topic.indexOf('/'));

                        JSONObject json = new JSONObject(contenido);
                        double temperaturaC = json.getDouble("tC");
                        double temperaturaF = json.getDouble("tF");
                        double epochSegundos = json.getDouble("ts");
                        Instant timestamp = Instant.ofEpochMilli((long) (epochSegundos * 1000));

                        // Resolver a qué habitación corresponde este termostato,
                        // según la asignación que ya hizo el cliente. Si el
                        // dispositivo no está asignado a ninguna habitación,
                        // NO inventamos una: se descarta el mensaje.
                        Optional<Habitacion> habitacion = habitacionDAO.buscarPorTermostatoId(deviceId);
                        if (habitacion.isEmpty()) {
                            System.out.println("Termostato '" + deviceId + "' no está asignado a ninguna habitación — se descarta el mensaje.");
                            return;
                        }

                        lecturaDAO.guardar(habitacion.get().id(), temperaturaC, temperaturaF, timestamp);
                        System.out.println("Guardado en BD: " + habitacion.get().nombre() + " -> " + temperaturaC + "°C");
                    } catch (Exception e) {
                        System.err.println("Error al persistir el mensaje: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // No se usa en un suscriptor
                }
            });

            // 3. Conectar al broker y suscribirse
            System.out.println("Conectando al broker MQTT en: " + broker);
            client.connect(options);
            System.out.println("✅ Conectado con éxito.");

            client.subscribe(topic);
            System.out.println("🎧 Suscrito al tópico: " + topic + ". Esperando mensajes...");

        } catch (MqttException | java.sql.SQLException e) {
            System.err.println("Error al iniciar el subscriber: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
