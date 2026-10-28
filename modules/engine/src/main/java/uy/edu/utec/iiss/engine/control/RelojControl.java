package uy.edu.utec.iiss.engine.control;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

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
     * Con qué instante (hora del control) se guarda una lectura cuyo termostato dice {@code ts}, o
     * vacío si la lectura se descarta. El simulador escribe el {@code ts} en hora real, así que
     * siempre se convierte con {@link #aTiempoDeControl}.
     *
     * <ul>
     *   <li>Sin {@code ts}: la hora del control al recibirla.
     *   <li>{@code ts} convertido en el futuro: reloj del termostato adelantado. Se usa la hora del
     *       control; si se guardara el {@code ts}, la lectura no vencería a tiempo y las siguientes
     *       parecerían anteriores.
     *   <li>{@code ts} convertido más viejo que la vigencia: es un dato obsoleto (por ejemplo, una
     *       lectura de las 19:00 que llega a las 21:00). <b>Se descarta</b> (D8): no puede sostener
     *       la calefacción, ni sellarse con la hora actual, porque haría que la próxima lectura
     *       buena pareciera fuera de orden.
     *   <li>En cualquier otro caso: el {@code ts} convertido.
     * </ul>
     *
     * @param vigencia vigencia de una lectura en tiempo de control, sin escalar (igual que en el
     *     core)
     * @return el instante de la lectura, o vacío si se descarta por vieja
     */
    public Optional<Instant> instanteDeLectura(Instant ts, Duration vigencia, Clock real) {
        Instant ahora = ahora(real);
        if (ts == null) {
            return Optional.of(ahora);
        }
        Instant medido = aTiempoDeControl(ts);
        if (medido.isAfter(ahora)) {
            return Optional.of(ahora);
        }
        if (medido.isBefore(ahora.minus(vigencia))) {
            return Optional.empty();
        }
        return Optional.of(medido);
    }
}
