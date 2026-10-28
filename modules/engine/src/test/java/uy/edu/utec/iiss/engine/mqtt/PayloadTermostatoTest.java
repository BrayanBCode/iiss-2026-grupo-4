package uy.edu.utec.iiss.engine.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PayloadTermostato (mensaje MQTT del §2)")
class PayloadTermostatoTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName(
            "Mensaje del estándar: ts en segundos con 3 decimales -> instante exacto (.123 = 123"
                    + " ms)")
    void mensajeDelEstandar() {
        PayloadTermostato p =
                PayloadTermostato.parsear(
                                b("{\"id\":0,\"tC\":21.4,\"tF\":70.5,\"ts\":1786840680.123}"))
                        .orElseThrow();

        assertEquals(21.4, p.temperaturaC(), 1e-9);
        assertEquals(Instant.ofEpochSecond(1_786_840_680L, 123_000_000L), p.ts());
    }

    @Test
    @DisplayName(
            "El ts es exacto con cualquier cantidad de decimales (no pasa por double): .001, .999,"
                    + " sin decimales y hasta nanosegundos")
    void tsExacto_sinErrorDeRedondeo() {
        assertEquals(
                Instant.ofEpochSecond(1_786_840_680L, 123_456_789L),
                PayloadTermostato.parsear(b("{\"tC\":20.0,\"ts\":1786840680.123456789}"))
                        .orElseThrow()
                        .ts());
        assertEquals(
                Instant.ofEpochSecond(1_786_840_680L, 1_000_000L),
                PayloadTermostato.parsear(b("{\"tC\":20.0,\"ts\":1786840680.001}"))
                        .orElseThrow()
                        .ts());
        assertEquals(
                Instant.ofEpochSecond(1_786_840_680L, 999_000_000L),
                PayloadTermostato.parsear(b("{\"tC\":20.0,\"ts\":1786840680.999}"))
                        .orElseThrow()
                        .ts());
        assertEquals(
                Instant.ofEpochSecond(1_786_840_680L),
                PayloadTermostato.parsear(b("{\"tC\":20.0,\"ts\":1786840680}")).orElseThrow().ts());
    }

    @Test
    @DisplayName("Sin ts, o con un ts que no es número, la lectura vale igual y el ts queda null")
    void sinTs() {
        assertNull(PayloadTermostato.parsear(b("{\"id\":0,\"tC\":19.0}")).orElseThrow().ts());
        assertNull(
                PayloadTermostato.parsear(b("{\"tC\":19.0,\"ts\":\"ayer\"}")).orElseThrow().ts());
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
