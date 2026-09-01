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
            // crea y puebla las tablas de estar vacias (Se supone el cliente ya ingreso los shelly al sistema)
            habitacionDAO.crearTablaSiNoExiste();
            habitacionDAO.seedearSiVacia();
            lecturaDAO.crearTablaSiNoExiste();
            System.out.println("🗄️ Tablas 'habitaciones' y 'lecturas' listas.");

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
                    System.out.println("Conexión perdida con el broker: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String contenido = new String(message.getPayload());
                    System.out.println("[MENSAJE RECIBIDO] Tópico: " + topic + " -> " + contenido);

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
                            System.out.println("Termostato '" + deviceId + "' no está asignado a ninguna habitación — se descarta el mensaje.");
                            return;
                        }

                        // Persistimos los datos en la tabla lecturas
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
