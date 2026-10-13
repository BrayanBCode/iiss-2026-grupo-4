package uy.edu.utec.iiss.engine.registro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("RegistroEntorno (§6): broker informado en la respuesta")
class RegistroEntornoTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("Toma host y puerto del 'mqtt' de la respuesta 201")
    void tomaElBroker() throws Exception {
        var r = JSON.readTree("{\"id\":\"x\",\"mqtt\":{\"host\":\"mosquitto\",\"puerto\":1883}}");
        assertEquals(Optional.of("tcp://mosquitto:1883"), RegistroEntorno.brokerInformado(r));
    }

    @Test
    @DisplayName("Sin 'mqtt' (o incompleto) no inventa un broker")
    void sinBroker() throws Exception {
        assertEquals(Optional.empty(), RegistroEntorno.brokerInformado(JSON.readTree("{\"id\":\"x\"}")));
        assertEquals(Optional.empty(), RegistroEntorno.brokerInformado(JSON.readTree("{\"mqtt\":{\"host\":\"\"}}")));
        assertEquals(Optional.empty(), RegistroEntorno.brokerInformado(null));
    }
}
