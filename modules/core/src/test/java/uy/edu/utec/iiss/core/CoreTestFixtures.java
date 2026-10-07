package uy.edu.utec.iiss.core;

import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.Estimulo;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Sitio;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Utilitarios compartidos por los tests del core (no es un test en sí).
 *
 * Calendario usado: octubre de 2026, hora local.
 *   lunes 5, martes 6, miércoles 7, jueves 8, viernes 9, sábado 10, domingo 11.
 */
final class CoreTestFixtures {

    static final String ROOM1 = "room1";
    static final String ROOM2 = "room2";
    static final String ROOM3 = "room3";

    /** Punta 17:00-23:00, solo días hábiles. */
    static final FranjaHoraria PUNTA =
            new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES);

    private CoreTestFixtures() {
    }

    // ---------- construcción de core / configuración ----------

    static ConfiguracionHabitacion hab(String id, double temperaturaEsperada, double potenciaKW) {
        return new ConfiguracionHabitacion(id, id, temperaturaEsperada, potenciaKW);
    }

    static Core nuevoCore(double potenciaContratadaKW, ConfiguracionHabitacion... habitaciones) {
        Core core = new Core(new MayorDeficitPrimero());
        core.procesar(config(potenciaContratadaKW, habitaciones));
        return core;
    }

    static Estimulo.ConfiguracionActualizada config(double potenciaContratadaKW, ConfiguracionHabitacion... habitaciones) {
        return new Estimulo.ConfiguracionActualizada(
                new Sitio("sitio-test", potenciaContratadaKW, PUNTA), List.of(habitaciones));
    }

    // ---------- estímulos ----------

    static Estimulo.NuevaLectura lectura(String idHabitacion, double temperaturaC, Instant ts) {
        return new Estimulo.NuevaLectura(idHabitacion, temperaturaC, ts);
    }

    static Estimulo.Tick tick(Instant ts) {
        return new Estimulo.Tick(ts);
    }

    // ---------- tiempo ----------

    static Instant dia(int diaDelMes, int hora, int minuto) {
        return LocalDateTime.of(2026, 10, diaDelMes, hora, minuto).atZone(ZoneId.systemDefault()).toInstant();
    }

    static Instant lunes(int hora, int minuto)   { return dia(5, hora, minuto); }
    static Instant viernes(int hora, int minuto) { return dia(9, hora, minuto); }
    static Instant sabado(int hora, int minuto)  { return dia(10, hora, minuto); }
    static Instant domingo(int hora, int minuto) { return dia(11, hora, minuto); }

    // ---------- lectura de resultados ----------

    /** Acción decidida para una habitación; falla si no hay decisión sobre ella. */
    static Accion accionDe(List<Decision> decisiones, String idHabitacion) {
        return decisiones.stream()
                .filter(d -> d.idHabitacion().equals(idHabitacion))
                .map(Decision::accion)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay decisión para " + idHabitacion));
    }

    static long cantidadEncendidas(List<Decision> decisiones) {
        return decisiones.stream().filter(d -> d.accion() == Accion.ON).count();
    }

    /** Suma de potencia (kW) de las habitaciones que quedaron en ON. */
    static double potenciaEncendida(List<Decision> decisiones, List<ConfiguracionHabitacion> habitaciones) {
        return decisiones.stream()
                .filter(d -> d.accion() == Accion.ON)
                .mapToDouble(d -> habitaciones.stream()
                        .filter(h -> h.id().equals(d.idHabitacion()))
                        .mapToDouble(ConfiguracionHabitacion::potenciaKW)
                        .findFirst().orElse(0.0))
                .sum();
    }
}