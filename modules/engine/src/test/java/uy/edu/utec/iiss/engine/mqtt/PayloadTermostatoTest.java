package uy.edu.utec.iiss.engine.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PayloadTermostato (mensaje MQTT del §2)")
class PayloadTermostatoTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Mensaje del estándar: tC y ts en segundos con 3 decimales -> milisegundos")
    void mensajeDelEstandar() {
        PayloadTermostato p =
                PayloadTermostato.parsear(
                                b("{\"id\":0,\"tC\":21.4,\"tF\":70.5,\"ts\":1786840680.123}"))
                        .orElseThrow();

        assertEquals(21.4, p.temperaturaC(), 1e-9);
        assertEquals(1_786_840_680_123L, p.tsEpochMilis());
    }

    @Test
    @DisplayName("Sin tF (opcional) se acepta igual")
    void sinTf() {
        assertTrue(
                PayloadTermostato.parsear(b("{\"id\":0,\"tC\":19.0,\"ts\":1786840680.1}"))
                        .isPresent());
    }

    @Test
    @DisplayName("Sin tC numérico, o lo que no es JSON, se descarta")
    void invalidos() {
        assertTrue(PayloadTermostato.parsear(b("{\"id\":0,\"tF\":70.5}")).isEmpty());
        assertTrue(PayloadTermostato.parsear(b("{\"tC\":\"veinte\"}")).isEmpty());
        assertTrue(PayloadTermostato.parsear(b("no es json")).isEmpty());
        assertTrue(PayloadTermostato.parsear(b("")).isEmpty());
    }
}
