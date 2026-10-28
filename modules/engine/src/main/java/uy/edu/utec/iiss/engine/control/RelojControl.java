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
        return aTiempoDeControl(real.instant());
    }

    /** Convierte un instante REAL (del reloj del sistema) a la hora del control. */
    public Instant aTiempoDeControl(Instant instanteReal) {
        Duration transcurrido = Duration.between(iniciadoEn, instanteReal);
        long nanos = Math.round(transcurrido.toNanos() * factorTiempo);
        return fechaHora.plusNanos(nanos);
    }

    /**
     * Con qué instante (hora del control) se guarda una lectura cuyo termostato dice {@code ts}.
     *
     * <ul>
     *   <li>Sin {@code ts}: la hora del control al recibirla.
     *   <li>Con {@code ts}: ese instante, convertido a hora del control si el termostato escribe en
     *       tiempo real ({@code tsEnTiempoReal}). El estándar (§2) no dice si con tiempo acelerado
     *       el simulador escribe el {@code ts} en tiempo simulado o real: queda configurable
     *       ({@code termostato.ts-tiempo-real}) hasta confirmarlo.
     *   <li>Un {@code ts} en el futuro, o tan viejo que la lectura llegaría ya vencida, no puede
     *       ser la medición recién hecha de un sensor sano: es un reloj desfasado (o un {@code ts}
     *       en otra escala). Se usa la hora del control. La temperatura nunca se descarta por esto.
     * </ul>
     *
     * Así, el peor caso de una suposición equivocada sobre el {@code ts} es el comportamiento sin
     * {@code ts}: guardar la lectura con la hora de llegada.
     *
     * @param vigencia vigencia de una lectura en tiempo real (se escala con el factor, igual que en
     *     el core)
     */
    public Instant instanteDeLectura(
            Instant ts, boolean tsEnTiempoReal, Duration vigencia, Clock real) {
        Instant ahora = ahora(real);
        if (ts == null) {
            return ahora;
        }
        Instant medido = tsEnTiempoReal ? aTiempoDeControl(ts) : ts;
        if (medido.isAfter(ahora) || medido.isBefore(ahora.minus(escalar(vigencia)))) {
            return ahora;
        }
        return medido;
    }

    /** Convierte una duración REAL a duración en tiempo de control. */
    public Duration escalar(Duration real) {
        return Duration.ofNanos(Math.round(real.toNanos() * factorTiempo));
    }
}
