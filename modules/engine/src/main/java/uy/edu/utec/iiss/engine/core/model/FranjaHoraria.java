package uy.edu.utec.iiss.engine.core.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Franja de tarifa de energía cara ("punta"), estándar §1: {@code desde} y
 * {@code hasta} en hora local (no cruza la medianoche), más los días a los
 * que aplica.
 *
 * Es un utilitario del modelo, no "la lógica de decisión" que hay que
 * probar con TDD — por eso está implementada de entrada, a diferencia de
 * {@link uy.edu.utec.iiss.engine.core.Core}.
 */
public record FranjaHoraria(LocalTime desde, LocalTime hasta, DiasTarifa dias) {

    /** True si el instante (ya convertido a hora/día local) cae dentro de esta franja. */
    public boolean incluye(DayOfWeek dia, LocalTime hora) {
        boolean diaAplica = dias == DiasTarifa.TODOS
                || (dias == DiasTarifa.HABILES && dia.getValue() <= DayOfWeek.FRIDAY.getValue());
        return diaAplica && !hora.isBefore(desde) && hora.isBefore(hasta);
    }
}
