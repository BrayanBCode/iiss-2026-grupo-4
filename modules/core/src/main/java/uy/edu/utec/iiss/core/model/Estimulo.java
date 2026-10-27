package uy.edu.utec.iiss.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Lo que el engine le puede mandar al core, ya traducido a su modelo (el core
 * no sabe de MQTT ni REST). Es {@code sealed} para que el {@code switch} de
 * {@code Core.procesar} sea exhaustivo.
 *
 * Los instantes son del reloj del control (§5.2), no del sistema.
 */
public sealed interface Estimulo {

    /** Lectura de temperatura; el engine ya resolvió la habitación por el topic (§2). */
    record NuevaLectura(String idHabitacion, double temperaturaC, Instant ts) implements Estimulo {
        public NuevaLectura {
            Objects.requireNonNull(idHabitacion, "idHabitacion");
            Objects.requireNonNull(ts, "ts");
        }
    }

    /**
     * Pasó el tiempo. El engine lo dispara periódicamente para reevaluar la
     * tarifa punta y el vencimiento de lecturas aunque no lleguen lecturas.
     */
    record Tick(Instant ts) implements Estimulo {
        public Tick {
            Objects.requireNonNull(ts, "ts");
        }
    }

    /** Reemplaza el inventario completo, como {@code PUT /sitio} (§5.1). */
    record ConfiguracionActualizada(Sitio sitio, List<ConfiguracionHabitacion> habitaciones) implements Estimulo {
        public ConfiguracionActualizada {
            Objects.requireNonNull(sitio, "sitio");
            habitaciones = List.copyOf(Objects.requireNonNull(habitaciones, "habitaciones"));
        }
    }

    /** INICIAR (§5.2): a diferencia de un Tick, el reloj puede ir hacia atrás y cambia el factor de tiempo. */
    record Sincronizacion(Instant ahora, double factorTiempo) implements Estimulo {
        public Sincronizacion {
            Objects.requireNonNull(ahora, "ahora");
            if (!(factorTiempo > 0) || Double.isInfinite(factorTiempo)) {
                throw new IllegalArgumentException("factorTiempo debe ser un número > 0");
            }
        }
    }
}
