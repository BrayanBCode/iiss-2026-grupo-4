package uy.edu.utec.iiss.engine.control;

import java.time.Instant;

/**
 * Respuesta de {@code GET /control} (§5.2). Detenido ⇒ fechaHora, factorTiempo e iniciadoEn en
 * null.
 */
public record EstadoControl(
        String estado, Instant fechaHora, Double factorTiempo, Instant iniciadoEn) {

    public static final String EN_EJECUCION = "EN_EJECUCION";
    public static final String DETENIDO = "DETENIDO";

    public static EstadoControl detenido() {
        return new EstadoControl(DETENIDO, null, null, null);
    }
}
