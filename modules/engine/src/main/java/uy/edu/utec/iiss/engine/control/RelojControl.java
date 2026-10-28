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
 * y no el reloj del sistema, que solo mide duraciones. {@code zona} es la zona en la que se
 * interpreta la franja tarifaria (hora local, sin conversión: ver {@link ControlService}).
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
     *   <li>Con {@code ts}: el simulador lo escribe en hora real, así que se convierte a hora del
     *       control con {@link #aTiempoDeControl}. {@code tsEnTiempoReal} ({@code
     *       termostato.ts-tiempo-real}, {@code true} por defecto) se elimina en la tarjeta T8b, que
     *       deja la conversión fija.
     *   <li>Un {@code ts} en el futuro, o tan viejo que la lectura llegaría ya vencida, no puede
     *       ser la medición recién hecha de un sensor sano: es un reloj desfasado (o un {@code ts}
     *       en otra escala). Se usa la hora del control. La temperatura nunca se descarta por esto.
     * </ul>
     *
     * Así, el peor caso de una suposición equivocada sobre el {@code ts} es el comportamiento sin
     * {@code ts}: guardar la lectura con la hora de llegada.
     *
     * @param vigencia vigencia de una lectura en tiempo de control, sin escalar (igual que en el
     *     core)
     */
    public Instant instanteDeLectura(
            Instant ts, boolean tsEnTiempoReal, Duration vigencia, Clock real) {
        Instant ahora = ahora(real);
        if (ts == null) {
            return ahora;
        }
        Instant medido = tsEnTiempoReal ? aTiempoDeControl(ts) : ts;
        if (medido.isAfter(ahora) || medido.isBefore(ahora.minus(vigencia))) {
            return ahora;
        }
        return medido;
    }
}
