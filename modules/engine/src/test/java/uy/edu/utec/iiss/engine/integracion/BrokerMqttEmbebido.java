package uy.edu.utec.iiss.engine.integracion;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.interception.AbstractInterceptHandler;
import io.moquette.interception.messages.InterceptConnectionLostMessage;
import io.moquette.interception.messages.InterceptDisconnectMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;
import io.moquette.interception.messages.InterceptUnsubscribeMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Broker MQTT en el mismo proceso (Moquette), en un puerto fijo durante toda la corrida: se puede
 * {@link #detener()} y volver a {@link #iniciar()} para simular que el broker cae y se recupera.
 *
 * <p>Sabe qué cliente está suscrito a qué topic, así el test espera a que el engine se suscriba
 * antes de publicar (un mensaje publicado antes se pierde: la sesión es limpia).
 */
public final class BrokerMqttEmbebido {

    private final int puerto = puertoLibre();
    private final Map<String, Set<String>> topicsPorCliente = new ConcurrentHashMap<>();
    private Server server;
    private MqttClient publicador;

    public String url() {
        return "tcp://127.0.0.1:" + puerto;
    }

    public synchronized void iniciar() {
        if (server != null) {
            return;
        }
        Properties p = new Properties();
        p.setProperty("host", "127.0.0.1");
        p.setProperty("port", String.valueOf(puerto));
        p.setProperty("websocket_port", "disabled");
        p.setProperty("allow_anonymous", "true");
        p.setProperty("persistence_enabled", "false");
        p.setProperty("telemetry_enabled", "false");
        Server nuevo = new Server();
        try {
            nuevo.startServer(new MemoryConfig(p), List.of(new Espia()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server = nuevo;
    }

    public synchronized void detener() {
        cerrarPublicador();
        if (server != null) {
            server.stopServer();
            server = null;
        }
        topicsPorCliente.clear();
    }

    public boolean haySuscriptor(String topic) {
        return topicsPorCliente.values().stream().anyMatch(t -> t.contains(topic));
    }

    /** Publica una lectura con el formato del termostato (§2), QoS 1, sin retener. */
    public synchronized void publicarTemperatura(String topic, double temperaturaC) {
        String json =
                "{\"id\":0,\"tC\":%s,\"tF\":%s,\"ts\":%s}"
                        .formatted(
                                temperaturaC,
                                temperaturaC * 9 / 5 + 32,
                                System.currentTimeMillis() / 1000.0);
        publicar(topic, json);
    }

    public synchronized void publicar(String topic, String payload) {
        try {
            if (publicador == null || !publicador.isConnected()) {
                cerrarPublicador();
                publicador =
                        new MqttClient(
                                url(),
                                "termostato-test-" + UUID.randomUUID(),
                                new MemoryPersistence());
                MqttConnectOptions opciones = new MqttConnectOptions();
                opciones.setCleanSession(true);
                publicador.connect(opciones);
            }
            publicador.publish(topic, payload.getBytes(StandardCharsets.UTF_8), 1, false);
        } catch (MqttException e) {
            throw new IllegalStateException("No se pudo publicar en " + topic, e);
        }
    }

    private void cerrarPublicador() {
        if (publicador == null) {
            return;
        }
        try {
            if (publicador.isConnected()) {
                publicador.disconnect();
            }
            publicador.close();
        } catch (MqttException e) {
            // el broker ya no está: no hay nada que cerrar
        }
        publicador = null;
    }

    private static int puertoLibre() {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Anota las suscripciones vivas de cada cliente. */
    private final class Espia extends AbstractInterceptHandler {
        @Override
        public String getID() {
            return "espia-suscripciones";
        }

        @Override
        public void onSessionLoopError(Throwable error) {}

        @Override
        public void onSubscribe(InterceptSubscribeMessage m) {
            topicsPorCliente
                    .computeIfAbsent(m.getClientID(), c -> ConcurrentHashMap.newKeySet())
                    .add(m.getTopicFilter());
        }

        @Override
        public void onUnsubscribe(InterceptUnsubscribeMessage m) {
            Set<String> topics = topicsPorCliente.get(m.getClientID());
            if (topics != null) {
                topics.remove(m.getTopicFilter());
            }
        }

        @Override
        public void onDisconnect(InterceptDisconnectMessage m) {
            topicsPorCliente.remove(m.getClientID());
        }

        @Override
        public void onConnectionLost(InterceptConnectionLostMessage m) {
            topicsPorCliente.remove(m.getClientID());
        }
    }
}
