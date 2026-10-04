package core.model;

import java.time.Instant;
import java.util.List;

/**
 * Los estímulos que el engine le puede mandar al core. El core nunca lee
 * MQTT ni REST directamente — el engine resuelve todo eso (a qué habitación
 * pertenece un topic, cuándo disparar un Tick) y le manda al core solo
 * estos tres tipos, en términos de su propio modelo.
 *
 * {@code sealed} + pattern matching exhaustivo en {@code Core.procesar}
 * asegura, a nivel de compilador, que no nos olvidamos de manejar un caso
 * nuevo si el día de mañana se agrega un cuarto tipo de estímulo.
 */
public sealed interface Estimulo {

    /**
     * Llegó una lectura de temperatura. {@code idHabitacion} ya viene
     * resuelto por el engine a partir del topic MQTT (estándar §2: "la
     * habitación se resuelve por el topic, nunca por el id del payload") —
     * el core no sabe nada de topics ni de termostatos.
     */
    record NuevaLectura(String idHabitacion, double temperaturaC, Instant ts) implements Estimulo {
    }

    /**
     * Estímulo puramente temporal, sin datos de ningún sensor: el engine lo
     * dispara periódicamente (responsable de "eventos temporales" según la
     * letra) para que el core pueda reevaluar la franja de tarifa vigente y
     * recalcular decisiones aunque no haya llegado ninguna lectura nueva
     * — típicamente, justo al cruzar el horario de tarifa punta.
     */
    record Tick(Instant ts) implements Estimulo {
    }

    /**
     * Reemplaza el inventario completo (coherente con que {@code PUT /sitio}
     * del estándar, §5.1, es un reemplazo completo, no un merge parcial).
     */
    record ConfiguracionActualizada(Sitio sitio, List<Habitacion> habitaciones) implements Estimulo {
    }
}
