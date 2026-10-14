package uy.edu.utec.iiss.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Los estímulos que el engine le puede mandar al core. El core nunca lee
 * MQTT ni REST directamente — el engine resuelve todo eso (a qué habitación
 * pertenece un topic, cuándo disparar un Tick, qué hora marca el reloj del
 * control) y le manda al core solo estos tipos, en términos de su propio modelo.
 *
 * {@code sealed} + pattern matching exhaustivo en {@code Core.procesar}
 * asegura, a nivel de compilador, que no nos olvidamos de manejar un caso
 * nuevo si el día de mañana se agrega otro tipo de estímulo.
 *
 * Todos los instantes son los del RELOJ DEL CONTROL (estándar §5.2), no los
 * del sistema: el engine es quien los calcula.
 */
public sealed interface Estimulo {

    /**
     * Llegó una lectura de temperatura. {@code idHabitacion} ya viene
     * resuelto por el engine a partir del topic MQTT (estándar §2: "la
     * habitación se resuelve por el topic, nunca por el id del payload") —
     * el core no sabe nada de topics ni de termostatos.
     */
    record NuevaLectura(String idHabitacion, double temperaturaC, Instant ts) implements Estimulo {
        public NuevaLectura {
            Objects.requireNonNull(idHabitacion, "idHabitacion");
            Objects.requireNonNull(ts, "ts");
        }
    }

    /**
     * Estímulo puramente temporal, sin datos de ningún sensor: el engine lo
     * dispara periódicamente (responsable de "eventos temporales" según la
     * letra) para que el core pueda reevaluar la franja de tarifa vigente y
     * la vigencia de las lecturas aunque no haya llegado ninguna lectura
     * nueva — típicamente, justo al cruzar el horario de tarifa punta.
     */
    record Tick(Instant ts) implements Estimulo {
        public Tick {
            Objects.requireNonNull(ts, "ts");
        }
    }

    /**
     * Reemplaza el inventario completo (coherente con que {@code PUT /sitio}
     * del estándar, §5.1, es un reemplazo completo, no un merge parcial).
     */
    record ConfiguracionActualizada(Sitio sitio, List<ConfiguracionHabitacion> habitaciones) implements Estimulo {
        public ConfiguracionActualizada {
            Objects.requireNonNull(sitio, "sitio");
            habitaciones = List.copyOf(Objects.requireNonNull(habitaciones, "habitaciones"));
        }
    }

    /**
     * El reloj del control se (re)sincronizó: {@code INICIAR} del estándar
     * §5.2. A diferencia de un Tick, el tiempo PUEDE ir hacia atrás (se
     * re-sincroniza a otra fecha), y cambia el factor de aceleración.
     */
    record Sincronizacion(Instant ahora, double factorTiempo) implements Estimulo {
        public Sincronizacion {
            Objects.requireNonNull(ahora, "ahora");
            if (!(factorTiempo > 0) || Double.isInfinite(factorTiempo)) {
                throw new IllegalArgumentException("factorTiempo debe ser un número > 0");
            }
        }
    }
}
