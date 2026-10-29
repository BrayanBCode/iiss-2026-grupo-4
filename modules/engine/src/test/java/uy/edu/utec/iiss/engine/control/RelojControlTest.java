package uy.edu.utec.iiss.engine.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    // ---------- instante de una lectura (ts del termostato, en hora real) ----------

    private static final Duration VIGENCIA = Duration.ofMinutes(10);
    private static final Instant TRECE = Instant.parse("2026-10-13T13:00:00Z");

    private static Clock real(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }

    @Test
    @DisplayName(
            "Factor 2, INICIAR a las 13:00 reales con fechaHora 13:00: ts 13:05 real -> 13:10 de"
                    + " control (se escala lo transcurrido, no la hora)")
    void conversion_factorDos_mismaHora() {
        RelojControl r = new RelojControl(TRECE, 2, TRECE, ZoneOffset.UTC);
        Instant ts = TRECE.plusSeconds(5 * 60);

        assertEquals(
                Optional.of(TRECE.plusSeconds(10 * 60)),
                r.instanteDeLectura(ts, VIGENCIA, real(TRECE.plusSeconds(6 * 60))));
    }

    @Test
    @DisplayName("Factor 2 con fechaHora 08:00: el mismo ts 13:05 real -> 08:10 de control")
    void conversion_factorDos_otraFechaHora() {
        Instant ocho = Instant.parse("2026-10-13T08:00:00Z");
        RelojControl r = new RelojControl(ocho, 2, TRECE, ZoneOffset.UTC);

        assertEquals(
                Optional.of(ocho.plusSeconds(10 * 60)),
                r.instanteDeLectura(
                        TRECE.plusSeconds(5 * 60), VIGENCIA, real(TRECE.plusSeconds(6 * 60))));
    }

    @Test
    @DisplayName("Sin ts -> la hora del control al recibirla")
    void sinTs_usaElReloj() {
        RelojControl r = new RelojControl(FECHA, 1, INICIO_REAL, ZoneOffset.UTC);

        assertEquals(
                Optional.of(FECHA.plusSeconds(30)),
                r.instanteDeLectura(null, VIGENCIA, realEn(30)));
    }

    @Test
    @DisplayName(
            "ts en el futuro (reloj del termostato adelantado) -> la hora del control, sin"
                    + " descartar la lectura")
    void tsFuturo_usaElReloj() {
        RelojControl r = new RelojControl(FECHA, 1, INICIO_REAL, ZoneOffset.UTC);
        Instant futuro = INICIO_REAL.plusSeconds(3600);

        assertEquals(
                Optional.of(FECHA.plusSeconds(30)),
                r.instanteDeLectura(futuro, VIGENCIA, realEn(30)));
    }

    @Test
    @DisplayName("ts viejo pero dentro de la vigencia -> se usa el ts convertido")
    void tsViejoDentroDeLaVigencia_seUsa() {
        RelojControl r = new RelojControl(FECHA, 1, INICIO_REAL, ZoneOffset.UTC);
        Instant hace5Min = INICIO_REAL.plusSeconds(600 - 300); // ahora = FECHA + 600 s

        assertEquals(
                Optional.of(FECHA.plusSeconds(300)),
                r.instanteDeLectura(hace5Min, VIGENCIA, realEn(600)));
    }

    @Test
    @DisplayName(
            "ts más viejo que la vigencia -> se descarta (D8); justo en el límite todavía se usa")
    void tsMasViejoQueLaVigencia_seDescarta() {
        RelojControl r = new RelojControl(FECHA, 1, INICIO_REAL, ZoneOffset.UTC);
        Clock ahoraReal = realEn(3600); // ahora = FECHA + 1 h

        assertEquals(
                Optional.empty(),
                r.instanteDeLectura(INICIO_REAL.plusSeconds(3600 - 601), VIGENCIA, ahoraReal));
        assertEquals(
                Optional.of(FECHA.plusSeconds(3600 - 600)),
                r.instanteDeLectura(INICIO_REAL.plusSeconds(3600 - 600), VIGENCIA, ahoraReal));
    }

    @Test
    @DisplayName(
            "Factor 60: la vigencia no se escala; un ts de hace 30 s reales (30 min de control) se"
                    + " descarta")
    void vigenciaSinEscalar_conFactor() {
        RelojControl r = new RelojControl(FECHA, 60, INICIO_REAL, ZoneOffset.UTC);

        assertEquals(
                Optional.empty(),
                r.instanteDeLectura(INICIO_REAL.plusSeconds(90), VIGENCIA, realEn(120)));
        assertEquals(
                Optional.of(FECHA.plusSeconds(115 * 60)),
                r.instanteDeLectura(INICIO_REAL.plusSeconds(115), VIGENCIA, realEn(120)));
    }

    @Test
    @DisplayName(
            "Desfase de reloj de ±2 s a ×30 (1 min de control): con lecturas cada 10 s reales"
                    + " ninguna se descarta y los instantes no retroceden")
    void desfaseDeReloj_noDescartaNiDesordena() {
        RelojControl r = new RelojControl(FECHA, 30, INICIO_REAL, ZoneOffset.UTC);
        for (long desfase : new long[] {2, -2}) {
            Instant anterior = null;
            for (int i = 1; i <= 20; i++) {
                Instant llegada = INICIO_REAL.plusSeconds(10L * i);
                Optional<Instant> instante =
                        r.instanteDeLectura(llegada.plusSeconds(desfase), VIGENCIA, real(llegada));

                assertTrue(
                        instante.isPresent(),
                        "se descartó la lectura " + i + " (" + desfase + " s)");
                if (anterior != null) {
                    assertTrue(
                            instante.get().isAfter(anterior),
                            "la lectura " + i + " quedó antes que la anterior (" + desfase + " s)");
                }
                anterior = instante.get();
            }
        }
    }

    @Test
    @DisplayName("Factor no positivo se rechaza")
    void factorInvalido() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RelojControl(FECHA, 0, INICIO_REAL, ZoneOffset.UTC));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RelojControl(FECHA, -1, INICIO_REAL, ZoneOffset.UTC));
    }
}
