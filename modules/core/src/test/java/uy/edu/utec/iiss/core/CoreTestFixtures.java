package uy.edu.utec.iiss.core;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Motivo;
import uy.edu.utec.iiss.core.model.Sitio;

/**
 * Utilitarios compartidos por los tests del core ({@code DecidirTest}, {@code
 * MayorDeficitPrimeroTest}; no es un test en sí).
 *
 * <p>Calendario usado: octubre de 2026, hora de Uruguay (UTC-3). lunes 5, martes 6, miércoles 7,
 * jueves 8, viernes 9, sábado 10, domingo 11.
 */
final class CoreTestFixtures {

    static final String ROOM1 = "room1";
    static final String ROOM2 = "room2";
    static final String ROOM3 = "room3";

    /** Punta 17:00-23:00, solo días hábiles. */
    static final FranjaHoraria PUNTA =
            new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES);

    private CoreTestFixtures() {}

    // ---------- configuración ----------

    static ConfiguracionHabitacion hab(String id, double temperaturaEsperada, double potenciaKW) {
        return new ConfiguracionHabitacion(id, id, temperaturaEsperada, potenciaKW);
    }

    // ---------- tiempo ----------

    static Instant dia(int diaDelMes, int hora, int minuto) {
        return LocalDateTime.of(2026, 10, diaDelMes, hora, minuto)
                .atZone(Sitio.ZONA_URUGUAY)
                .toInstant();
    }

    static Instant lunes(int hora, int minuto) {
        return dia(5, hora, minuto);
    }

    static Instant viernes(int hora, int minuto) {
        return dia(9, hora, minuto);
    }

    static Instant sabado(int hora, int minuto) {
        return dia(10, hora, minuto);
    }

    static Instant domingo(int hora, int minuto) {
        return dia(11, hora, minuto);
    }

    // ---------- lectura de resultados ----------

    static Decision decisionDe(List<Decision> decisiones, String idHabitacion) {
        return decisiones.stream()
                .filter(d -> d.idHabitacion().equals(idHabitacion))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay decisión para " + idHabitacion));
    }

    /** Acción decidida para una habitación; falla si no hay decisión sobre ella. */
    static Accion accionDe(List<Decision> decisiones, String idHabitacion) {
        return decisionDe(decisiones, idHabitacion).accion();
    }

    static Motivo motivoDe(List<Decision> decisiones, String idHabitacion) {
        return decisionDe(decisiones, idHabitacion).motivo();
    }
}
