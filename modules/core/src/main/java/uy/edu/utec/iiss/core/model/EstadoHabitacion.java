package uy.edu.utec.iiss.core.model;

import java.time.Instant;

/**
 * Estado interno que el core mantiene por habitación: la última temperatura
 * conocida (null si todavía no llegó ninguna lectura), cuándo se midió, y si
 * el core la considera encendida en este momento. Es "la representación
 * interna del estado" que pide la letra, separada de
 * {@link ConfiguracionHabitacion} porque esta última es configuración (viene
 * del inventario) y esto es estado vivo (cambia con cada estímulo).
 */
public record EstadoHabitacion(String idHabitacion, Double temperaturaActual, Instant ultimaLectura, boolean encendida) {

    /** Sin instante de lectura (útil para armar estados a mano, p. ej. en los tests del criterio). */
    public EstadoHabitacion(String idHabitacion, Double temperaturaActual, boolean encendida) {
        this(idHabitacion, temperaturaActual, null, encendida);
    }
}
