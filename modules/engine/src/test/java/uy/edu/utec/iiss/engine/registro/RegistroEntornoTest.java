package uy.edu.utec.iiss.engine.registro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RegistroEntorno (§6): broker informado y baseUrl alcanzable")
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
        assertEquals(
                Optional.empty(), RegistroEntorno.brokerInformado(JSON.readTree("{\"id\":\"x\"}")));
        assertEquals(
                Optional.empty(),
                RegistroEntorno.brokerInformado(JSON.readTree("{\"mqtt\":{\"host\":\"\"}}")));
        assertEquals(Optional.empty(), RegistroEntorno.brokerInformado(null));
    }

    @Test
    @DisplayName(
            "baseUrl localhost con el entorno en otra máquina -> avisa (corrida del 2026-10-28)")
    void baseUrlLocal_conEntornoRemoto_avisa() {
        Optional<String> problema =
                RegistroEntorno.problemaBaseUrl(
                        "http://localhost:8080", "http://192.168.1.116:9094");
        assertTrue(problema.isPresent());
        assertTrue(problema.get().contains("192.168.1.116"), problema.get());
    }

    @Test
    @DisplayName("baseUrl con una IP de tres octetos -> avisa (corrida del 2026-10-28)")
    void ipMalEscrita_avisa() {
        assertTrue(
                RegistroEntorno.problemaBaseUrl(
                                "http://192.168.148:8080", "http://192.168.1.116:9094")
                        .isPresent());
        assertTrue(
                RegistroEntorno.problemaBaseUrl(
                                "http://192.168.1.300:8080", "http://192.168.1.116:9094")
                        .isPresent());
    }

    @Test
    @DisplayName("baseUrl que no es http(s) -> avisa")
    void baseUrlInvalida_avisa() {
        assertTrue(
                RegistroEntorno.problemaBaseUrl("ecowarm:8080", "http://iiss-simulator:9090")
                        .isPresent());
        assertTrue(RegistroEntorno.problemaBaseUrl("", "http://iiss-simulator:9090").isPresent());
    }

    @Test
    @DisplayName("Configuraciones que funcionan -> no avisa")
    void configuracionesQueFuncionan_noAvisan() {
        // Laboratorio: los dos en la red iiss-lab-net.
        assertEquals(
                Optional.empty(),
                RegistroEntorno.problemaBaseUrl(
                        "http://ecowarm-grupo4:8080", "http://iiss-simulator:9090"));
        // ecowarm-check en el anfitrión y el controlador en Docker con el puerto publicado.
        assertEquals(
                Optional.empty(),
                RegistroEntorno.problemaBaseUrl(
                        "http://localhost:8080", "http://host.docker.internal:9090"));
        // Los dos en la misma máquina, sin Docker.
        assertEquals(
                Optional.empty(),
                RegistroEntorno.problemaBaseUrl("http://localhost:8085", "http://localhost:9090"));
        // Simulador en otra máquina y el controlador con su IP de la red.
        assertEquals(
                Optional.empty(),
                RegistroEntorno.problemaBaseUrl(
                        "http://192.168.1.148:8080", "http://192.168.1.116:9094"));
    }
}
