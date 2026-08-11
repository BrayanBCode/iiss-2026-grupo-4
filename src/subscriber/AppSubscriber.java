package subscriber;

import org.eclipse.paho.client.mqttv3.*;

public class AppSubscriber {

    public static void main(String[] args) {
        // En Docker Compose, "mosquitto" es el nombre del servicio broker
        String brokerUrl = "tcp://mosquitto:1883";
        String clientId = "JavaSubscriberClient";
        String topic = "ingSoft/informa";

        try {
            // 1. Crear el cliente Paho
            MqttClient client = new MqttClient(brokerUrl, clientId);

            // 2. Configurar el escuchador (Callback)
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("Conexión perdida con el broker MQTT: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // 3. AQUÍ LLEGAN LOS MENSAJES QUE ESCUCHA
                    String contenido = new String(message.getPayload());
                    IO.println("Mensaje recibido en [" + topic + "]: " + contenido);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // No se usa en un suscriptor puro
                }
            });

            // Conectarse al broker y suscribirse
            System.out.println("Conectando al broker en " + brokerUrl + "...");
            client.connect();

            client.subscribe(topic);
            System.out.println("Suscrito con éxito al tópico: " + topic);
            System.out.println("Esperando mensajes de temperatura...");

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
