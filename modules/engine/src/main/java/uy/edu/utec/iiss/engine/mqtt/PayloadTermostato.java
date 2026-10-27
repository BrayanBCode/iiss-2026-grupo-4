package uy.edu.utec.iiss.engine.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Mensaje del termostato (estándar v2, §2): {@code {"id":0,"tC":21.4,"tF":70.5,"ts":1786840680.123}}.
 *
 * Solo se usa {@code tC}: el instante de la lectura es el del reloj del control
 * al recibirla (§5.2), no el {@code ts} (epoch en segundos), que se parsea pero
 * no se usa para decidir. La habitación sale del topic, nunca del {@code id}.
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
