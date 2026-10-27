package uy.edu.utec.iiss.core.model;

import java.time.Instant;

/**
 * Estado vivo de una habitación (a diferencia de {@link ConfiguracionHabitacion}, que viene del
 * inventario): última temperatura (null si no hubo lectura), cuándo se midió y si está encendida.
 */
public record EstadoHabitacion(
        String idHabitacion, Double temperaturaActual, Instant ultimaLectura, boolean encendida) {

    /** Sin instante de lectura. */
    public EstadoHabitacion(String idHabitacion, Double temperaturaActual, boolean encendida) {
        this(idHabitacion, temperaturaActual, null, encendida);
    }
}
