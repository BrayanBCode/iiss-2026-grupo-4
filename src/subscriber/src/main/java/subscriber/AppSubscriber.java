package subscriber;

import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;

import java.time.Instant;

public class AppSubscriber {

    public static void main(String[] args) {
        // En Docker Compose, "mosquitto" es el nombre del servicio broker
        String broker = "tcp://mosquitto:1883";
        String clientId = "JavaSubscriberModule";
        String topic = "casa/+/temperatura"; // el + sirve como "comodin" por nivel

        LecturaDAO dao = new LecturaDAO();

        try {
            // 0. Crear la tabla en la BD antes de arrancar a escuchar
            dao.crearTablaSiNoExiste();
            System.out.println("🗄️ Tabla 'lecturas' lista.");

            // 1. Crear el cliente MQTT con la librería Eclipse Paho
            MqttClient client = new MqttClient(broker, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            // 2. Definir qué hacer cuando lleguen mensajes
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("❌ Conexión perdida con el broker: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String contenido = new String(message.getPayload());
                    System.out.println("📩 [MENSAJE RECIBIDO] Tópico: " + topic + " -> " + contenido);

                    try {
                        JSONObject json = new JSONObject(contenido);
                        String habitacion = json.getString("id");
                        double temperatura = json.getDouble("temperatura");
                        Instant timestamp = Instant.parse(json.getString("timestamp"));

                        dao.guardar(habitacion, temperatura, timestamp);
                        System.out.println("💾 Guardado en BD: " + habitacion + " -> " + temperatura + "°C");
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