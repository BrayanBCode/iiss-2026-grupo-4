package uy.edu.utec.iiss.engine.mqtt;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LecturaMqttListener: identificador del cliente MQTT")
class LecturaMqttListenerTest {

    @Test
    @DisplayName(
            "Como mucho 23 caracteres (límite de MQTT 3.1), empieza con la etiqueta y cambia en"
                    + " cada conexión")
    void clientId_cortoYUnico() {
        String id = LecturaMqttListener.clientId("ecowarm-grupo4");
        assertTrue(id.length() <= 23, id);
        assertTrue(id.startsWith("ecowarm-gr-"), id);
        assertNotEquals(id, LecturaMqttListener.clientId("ecowarm-grupo4"));
        assertTrue(LecturaMqttListener.clientId("g4").length() <= 23);
    }
}
