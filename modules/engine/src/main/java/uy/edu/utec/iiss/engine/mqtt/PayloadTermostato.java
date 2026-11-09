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
 * <p>{@code ts} es el instante de la medición (epoch con 3 decimales), en hora real o ya en hora
 * del control según el entorno; se guarda exacto (null si no vino o no es un número). Qué instante
 * de la hora del control se le asigna a la lectura lo decide {@code
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
            if (json == null) {
                return Optional.empty();
            }
            // Termostatos fuera de contrato (F04): se acepta lo que se entiende sin riesgo, un tC
            // numérico escrito como texto y un ts como texto o en milisegundos. Sin tC se descarta
            // (el §2 lo exige; tF solo no alcanza). El rango del sensor se controla después
            // igual (Core.lecturaValida).
            BigDecimal temperaturaC = decimal(json.path("tC"));
            if (temperaturaC == null) {
                return Optional.empty();
            }
            BigDecimal ts = decimal(json.path("ts"));
            Instant instante = null;
            if (ts != null) {
                instante = deEpoch(enSegundos(ts));
            }
            return Optional.of(new PayloadTermostato(temperaturaC.doubleValue(), instante));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** El número del campo, sea un número JSON o un texto con un número; null si no hay. */
    private static BigDecimal decimal(JsonNode campo) {
        if (campo.isNumber()) {
            return campo.decimalValue();
        }
        if (campo.isTextual()) {
            try {
                return new BigDecimal(campo.asText().strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * El estándar manda segundos; un epoch de más de 11 cifras son milisegundos (en segundos sería
     * después del año 5000).
     */
    private static BigDecimal enSegundos(BigDecimal ts) {
        if (ts.compareTo(BigDecimal.valueOf(100_000_000_000L)) > 0) {
            return ts.movePointLeft(3);
        }
        return ts;
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
