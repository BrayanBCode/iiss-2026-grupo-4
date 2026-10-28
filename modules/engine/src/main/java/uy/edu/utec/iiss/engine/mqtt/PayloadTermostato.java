package uy.edu.utec.iiss.engine.mqtt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * Mensaje del termostato (estándar v2, §2): {@code
 * {"id":0,"tC":21.4,"tF":70.5,"ts":1786840680.123}}.
 *
 * <p>{@code ts} es el instante de la medición, en epoch con 3 decimales; se guarda exacto (null si
 * no vino o no es un número). Qué instante se le asigna a la lectura lo decide {@code
 * RelojControl.instanteDeLectura}. La habitación sale del topic, nunca del {@code id}.
 */
public record PayloadTermostato(double temperaturaC, Instant ts) {

    // Los números con decimales se leen como BigDecimal: el ts se convierte sin pasar por double.
    // Con los 3 decimales del estándar double alcanza, pero con más (p. ej. 1786840680.123456789)
    // pierde los últimos dígitos.
    private static final ObjectMapper JSON =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    /** Vacío si el payload no es JSON o no trae un {@code tC} numérico. */
    public static Optional<PayloadTermostato> parsear(byte[] payload) {
        try {
            JsonNode json = JSON.readTree(new String(payload, StandardCharsets.UTF_8));
            if (json == null || !json.path("tC").isNumber()) {
                return Optional.empty();
            }
            JsonNode ts = json.path("ts");
            Instant instante = ts.isNumber() ? deEpoch(ts.decimalValue()) : null;
            return Optional.of(new PayloadTermostato(json.path("tC").asDouble(), instante));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Epoch en segundos con decimales a Instant, sin redondear: 1786840680.123 → …680 s + 123 ms.
     */
    static Instant deEpoch(BigDecimal segundos) {
        long enteros = segundos.longValue();
        long nanos = segundos.subtract(BigDecimal.valueOf(enteros)).movePointRight(9).longValue();
        return Instant.ofEpochSecond(enteros, nanos);
    }
}
