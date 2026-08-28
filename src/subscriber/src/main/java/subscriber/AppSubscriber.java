package subscriber;
import org.eclipse.paho.client.mqttv3.*;

public class AppSubscriber {

    public static void main(String[] args) {
        // En Docker Compose, "mosquitto" es el nombre del servicio broker
        String broker = "tcp://mosquitto:1883";
        String clientId = "JavaSubscriberModule";
        String topic = "casa/habitacion1/temperatura"; // Ajusta al tópico que uses en tus scripts

        try {
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

        } catch (MqttException e) {
            System.err.println("Error en el cliente MQTT: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
