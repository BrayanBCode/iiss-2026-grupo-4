package uy.edu.utec.iiss.core.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Franja de tarifa punta (estándar §1): {@code [desde, hasta)} en hora local, sin cruzar la
 * medianoche, más los días a los que aplica.
 */
public record FranjaHoraria(LocalTime desde, LocalTime hasta, DiasTarifa dias) {

    public FranjaHoraria {
        Objects.requireNonNull(desde, "desde");
        Objects.requireNonNull(hasta, "hasta");
        Objects.requireNonNull(dias, "dias");
        if (!desde.isBefore(hasta)) {
            throw new IllegalArgumentException(
                    "La franja debe cumplir desde < hasta (es " + desde + " - " + hasta + ")");
        }
    }

    /** True si el instante (ya convertido a hora/día local) cae dentro de esta franja. */
    public boolean incluye(DayOfWeek dia, LocalTime hora) {
        boolean diaAplica =
                dias == DiasTarifa.TODOS
                        || (dias == DiasTarifa.HABILES
                                && dia.getValue() <= DayOfWeek.FRIDAY.getValue());
        return diaAplica && !hora.isBefore(desde) && hora.isBefore(hasta);
    }
}
