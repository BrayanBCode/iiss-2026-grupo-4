package uy.edu.utec.iiss.engine.control;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Reloj del control (estándar v2, §5.2): tras INICIAR, todo lo que depende de la hora usa
 *
 * <pre>ahora = fechaHora + (tiempoRealTranscurrido × factorTiempo)</pre>
 *
 * y no el reloj del sistema. {@code zona} es la zona en la que se interpreta la franja tarifaria
 * (hora local, sin conversión: ver {@link ControlService}).
 */
public record RelojControl(
        Instant fechaHora, double factorTiempo, Instant iniciadoEn, ZoneId zona) {

    public RelojControl {
        Objects.requireNonNull(fechaHora, "fechaHora");
        Objects.requireNonNull(iniciadoEn, "iniciadoEn");
        Objects.requireNonNull(zona, "zona");
        if (!(factorTiempo > 0) || Double.isInfinite(factorTiempo)) {
            throw new IllegalArgumentException("factorTiempo debe ser un número > 0");
        }
    }

    /** Hora del control en el instante real que marca {@code real}. */
    public Instant ahora(Clock real) {
        Duration transcurrido = Duration.between(iniciadoEn, real.instant());
        long nanos = Math.round(transcurrido.toNanos() * factorTiempo);
        return fechaHora.plusNanos(nanos);
    }

    /** Convierte una duración REAL a duración en tiempo de control. */
    public Duration escalar(Duration real) {
        return Duration.ofNanos(Math.round(real.toNanos() * factorTiempo));
    }
}
