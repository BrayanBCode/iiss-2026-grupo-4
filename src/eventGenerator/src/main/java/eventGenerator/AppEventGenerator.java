package eventGenerator;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AppEventGenerator {
    public static void main(String[] args) throws Exception{
        MqttClient client = new MqttClient("tcp://mosquitto:1883", MqttClient.generateClientId());
        client.connect();

        List<Habitacion> habitaciones = List.of(
                new Habitacion("habitacion1", client),
                new Habitacion("habitacion2", client),
                new Habitacion("habitacion3", client)
        );

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            for (Habitacion h : habitaciones) {
                try {
                    h.generarEvento();
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 10, TimeUnit.SECONDS);
    }
}
