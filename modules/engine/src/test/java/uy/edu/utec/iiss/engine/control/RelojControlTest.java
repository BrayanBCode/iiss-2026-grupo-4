package uy.edu.utec.iiss.engine.control;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RelojControl (ahora = fechaHora + transcurrido × factor, §5.2)")
class RelojControlTest {

    private static final Instant INICIO_REAL = Instant.parse("2026-10-13T12:00:00Z");
    private static final Instant FECHA = Instant.parse("2026-12-24T18:00:00Z");

    private static Clock realEn(long segundosDespues) {
        return Clock.fixed(INICIO_REAL.plusSeconds(segundosDespues), ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Factor 1: avanza a la par del tiempo real")
    void factorUno() {
        RelojControl r = new RelojControl(FECHA, 1, INICIO_REAL, ZoneOffset.UTC);
        assertEquals(FECHA.plusSeconds(30), r.ahora(realEn(30)));
    }

    @Test
    @DisplayName("Factor 10: 30 s reales son 5 min de control")
    void factorDiez() {
        RelojControl r = new RelojControl(FECHA, 10, INICIO_REAL, ZoneOffset.UTC);
        assertEquals(FECHA.plusSeconds(300), r.ahora(realEn(30)));
    }

    @Test
    @DisplayName("Factor fraccionario 0.5: 10 s reales son 5 s de control")
    void factorFraccionario() {
        RelojControl r = new RelojControl(FECHA, 0.5, INICIO_REAL, ZoneOffset.UTC);
        assertEquals(FECHA.plusSeconds(5), r.ahora(realEn(10)));
    }

    @Test
    @DisplayName("Factor no positivo se rechaza")
    void factorInvalido() {
        assertThrows(IllegalArgumentException.class, () -> new RelojControl(FECHA, 0, INICIO_REAL, ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> new RelojControl(FECHA, -1, INICIO_REAL, ZoneOffset.UTC));
    }
}
