package eventGenerator;
import org.eclipse.paho.client.mqttv3.*;

/**
 * Simula un termostato (sensor Shelly H&T Gen3) publicando su temperatura
 * por MQTT, con el formato de mensaje definido en la letra de la Iteración 2:
 *
 *   Topic:   <deviceId>/status/temperature:0
 *   Payload: {"id":0,"tC":21.4,"tF":70.5,"ts":1786840680.123}
 *
 * El "id" del payload es el índice del componente sensor dentro DEL PROPIO
 * dispositivo (los Shelly reales pueden tener más de un sensor del mismo tipo
 * en un solo device) — NO es el identificador de la habitación. El H&T Gen3
 * tiene un solo sensor de temperatura, así que ese id siempre es 0 acá.
 *
 * A propósito, esta clase no sabe en qué "habitación" está instalada —
 * eso es exactamente lo que un Shelly real tampoco sabe: el dispositivo
 * solo conoce su propio deviceId de fábrica. Qué habitación corresponde a
 * qué deviceId es una asignación que hace el cliente y que vive en la base
 * de datos del subscriber, no acá.
 */
public class Habitacion {
    private static final int SENSOR_COMPONENT_ID = 0;

    private final String deviceId;
    private final MqttClient client;
    private double temperaturaC = 21.0;

    public Habitacion(String deviceId, MqttClient client) {
        this.deviceId = deviceId;
        this.client = client;
    }

    public void generarEvento() throws MqttException {
        temperaturaC += (Math.random() - 0.5);

        double temperaturaF = (temperaturaC * 9.0 / 5.0) + 32.0;
        double ts = System.currentTimeMillis() / 1000.0; // epoch en segundos, con decimales de milisegundo

        String payload = """
            {"id":%d,"tC":%.1f,"tF":%.1f,"ts":%.3f}""".formatted(
                SENSOR_COMPONENT_ID, temperaturaC, temperaturaF, ts);

        String topic = deviceId + "/status/temperature:" + SENSOR_COMPONENT_ID;
        client.publish(topic, new MqttMessage(payload.getBytes()));
    }
}
