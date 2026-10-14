package uy.edu.utec.iiss.engine.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Mensaje del termostato (estándar v2, §2): {@code {"id":0,"tC":21.4,"tF":70.5,"ts":1786840680.123}}.
 *
 * Solo {@code tC} es imprescindible para decidir. {@code ts} (epoch en
 * SEGUNDOS con 3 decimales) se conserva para los logs; el instante con el que
 * decide el core es el del reloj del control al recibir el mensaje, porque
 * tras INICIAR el controlador no usa otro reloj (§5.2). {@code tF} es
 * opcional y no se usa. El {@code id} del payload es el índice del sensor:
 * la habitación se resuelve por el topic, nunca por él.
 */
public record PayloadTermostato(double temperaturaC, Long tsEpochMilis) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Vacío si el payload no es JSON o no trae un {@code tC} numérico. */
    public static Optional<PayloadTermostato> parsear(byte[] payload) {
        try {
            JsonNode json = JSON.readTree(new String(payload, StandardCharsets.UTF_8));
            if (json == null || !json.path("tC").isNumber()) {
                return Optional.empty();
            }
            JsonNode ts = json.path("ts");
            Long tsMilis = ts.isNumber() ? Math.round(ts.asDouble() * 1000.0) : null;
            return Optional.of(new PayloadTermostato(json.path("tC").asDouble(), tsMilis));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
