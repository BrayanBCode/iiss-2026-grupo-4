package core.model;

/**
 * Estado interno que el core mantiene por habitación: la última temperatura
 * conocida (null si todavía no llegó ninguna lectura) y si el core la
 * considera encendida en este momento. Es "la representación interna del
 * estado" que pide la letra, separada de {@link Habitacion} porque esta
 * última es configuración (viene del inventario) y esto es estado vivo
 * (cambia con cada estímulo).
 */
public record EstadoHabitacion(String idHabitacion, Double temperaturaActual, boolean encendida) {
}
