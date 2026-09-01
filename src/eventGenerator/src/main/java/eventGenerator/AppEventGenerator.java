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

        // Creamos
        List<Habitacion> habitaciones = List.of(
                new Habitacion("shellyhtg3-a1b2c3d4e5f6", client), // living
                new Habitacion("shellyhtg3-b2c3d4e5f6a1", client), // dormitorio
                new Habitacion("shellyhtg3-c3d4e5f6a1b2", client)  // cocina
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
