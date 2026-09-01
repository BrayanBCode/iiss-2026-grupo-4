package eventGenerator;
import org.eclipse.paho.client.mqttv3.*;

/**
Simula un termostato (sensor Shelly H&T Gen3) publicando su temperatura*
Topic:   <deviceId>/status/temperature:0
Payload: {"id":0,"tC":21.4,"tF":70.5,"ts":1786840680.123}*
El "id" del payload es el índice del componente sensor dentro DEL PROPIO
dispositivo (los Shelly reales pueden tener más de un sensor del mismo tipo
 en un solo device) — NO es el identificador de la habitación. El H&T Gen3
tiene un solo sensor de temperatura, así que ese id siempre es 0 acá (temperature:0 <- id).

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
