package eventGenerator;
import org.eclipse.paho.client.mqttv3.*;


public class Habitacion {
    private final String id;
    private final MqttClient client;
    private double temperatura = 21.0;

    public Habitacion(String id, MqttClient client) {
        this.id = id;
        this.client = client;
    }

    public void generarEvento() throws MqttException {
        temperatura += (Math.random() - 0.5);
        String payload = """
            {"id":"%s","temperatura":%.1f,"timestamp":"%s"}""".formatted(id, temperatura, java.time.Instant.now());
        client.publish("casa/" + id + "/temperatura", new MqttMessage(payload.getBytes()));
    }
}