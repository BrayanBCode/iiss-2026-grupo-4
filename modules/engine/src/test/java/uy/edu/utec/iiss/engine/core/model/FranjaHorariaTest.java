package uy.edu.utec.iiss.engine.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la franja de tarifa punta: de ella depende que el core corte y
 * restituya la energía en el momento correcto.
 */
@DisplayName("FranjaHoraria (tarifa punta)")
class FranjaHorariaTest {

    private final FranjaHoraria habiles =
            new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES);
    private final FranjaHoraria todos =
            new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.TODOS);

    @Test
    @DisplayName("El límite inferior (17:00) está incluido")
    void desdeEsInclusivo() {
        assertTrue(habiles.incluye(DayOfWeek.MONDAY, LocalTime.of(17, 0)));
    }

    @Test
    @DisplayName("Un segundo antes del inicio (16:59:59) no está incluido")
    void justoAntesDelInicio() {
        assertFalse(habiles.incluye(DayOfWeek.MONDAY, LocalTime.of(16, 59, 59)));
    }

    @Test
    @DisplayName("Un segundo antes del fin (22:59:59) todavía está incluido")
    void justoAntesDelFin() {
        assertTrue(habiles.incluye(DayOfWeek.MONDAY, LocalTime.of(22, 59, 59)));
    }

    @Test
    @DisplayName("El límite superior (23:00) está excluido")
    void hastaEsExclusivo() {
        assertFalse(habiles.incluye(DayOfWeek.MONDAY, LocalTime.of(23, 0)));
    }

    @Test
    @DisplayName("Medianoche y madrugada quedan fuera")
    void medianocheYMadrugada() {
        assertFalse(habiles.incluye(DayOfWeek.MONDAY, LocalTime.MIDNIGHT));
        assertFalse(habiles.incluye(DayOfWeek.MONDAY, LocalTime.of(3, 0)));
    }

    @Test
    @DisplayName("HABILES aplica de lunes a viernes")
    void habilesAplicaDeLunesAViernes() {
        for (DayOfWeek d : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            assertTrue(habiles.incluye(d, LocalTime.of(18, 0)), d.toString());
        }
    }

    @Test
    @DisplayName("HABILES no aplica sábado ni domingo")
    void habilesNoAplicaFinDeSemana() {
        assertFalse(habiles.incluye(DayOfWeek.SATURDAY, LocalTime.of(18, 0)));
        assertFalse(habiles.incluye(DayOfWeek.SUNDAY, LocalTime.of(18, 0)));
    }

    @Test
    @DisplayName("TODOS aplica los siete días")
    void todosAplicaTodosLosDias() {
        for (DayOfWeek d : DayOfWeek.values()) {
            assertTrue(todos.incluye(d, LocalTime.of(18, 0)), d.toString());
        }
    }

    @Test
    @DisplayName("TODOS respeta igualmente el horario: fuera de 17-23 no incluye")
    void todosRespetaElHorario() {
        assertFalse(todos.incluye(DayOfWeek.SUNDAY, LocalTime.of(10, 0)));
    }
}