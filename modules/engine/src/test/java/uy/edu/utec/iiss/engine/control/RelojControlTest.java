package uy.edu.utec.iiss.engine.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

    // ---------- instante de una lectura (ts del termostato) ----------

    private static final Duration VIGENCIA = Duration.ofMinutes(10);

    @Test
    @DisplayName(
            "ts en tiempo de control y razonable (unos segundos antes de ahora) -> se usa tal cual")
    void ts_razonable_seUsa() {
        RelojControl r = new RelojControl(FECHA, 1, INICIO_REAL, ZoneOffset.UTC);
        Instant ts = FECHA.plusSeconds(28).plusMillis(123); // ahora = FECHA + 30 s

        assertEquals(ts, r.instanteDeLectura(ts, false, VIGENCIA, realEn(30)));
    }

    @Test
    @DisplayName("Sin ts -> la hora del control al recibirla")
    void sinTs_usaElReloj() {
        RelojControl r = new RelojControl(FECHA, 1, INICIO_REAL, ZoneOffset.UTC);

        assertEquals(FECHA.plusSeconds(30), r.instanteDeLectura(null, false, VIGENCIA, realEn(30)));
    }

    @Test
    @DisplayName(
            "ts en el futuro (reloj desfasado) -> la hora del control, sin descartar la lectura")
    void tsFuturo_usaElReloj() {
        RelojControl r = new RelojControl(FECHA, 1, INICIO_REAL, ZoneOffset.UTC);
        Instant futuro = FECHA.plusSeconds(3600);

        assertEquals(
                FECHA.plusSeconds(30), r.instanteDeLectura(futuro, false, VIGENCIA, realEn(30)));
    }

    @Test
    @DisplayName(
            "ts tan viejo que llegaría vencido (más que la vigencia) -> la hora del control; justo"
                    + " en el límite se usa el ts")
    void tsMasViejoQueLaVigencia_usaElReloj() {
        RelojControl r = new RelojControl(FECHA, 1, INICIO_REAL, ZoneOffset.UTC);
        Instant ahora = FECHA.plusSeconds(30);

        assertEquals(
                ahora,
                r.instanteDeLectura(
                        ahora.minus(VIGENCIA).minusSeconds(1), false, VIGENCIA, realEn(30)));
        assertEquals(
                ahora.minus(VIGENCIA),
                r.instanteDeLectura(ahora.minus(VIGENCIA), false, VIGENCIA, realEn(30)));
    }

    @Test
    @DisplayName(
            "ts en tiempo real con factor 60: se convierte a la hora del control antes de usarlo")
    void tsEnTiempoReal_seConvierte() {
        RelojControl r = new RelojControl(FECHA, 60, INICIO_REAL, ZoneOffset.UTC);
        Instant tsReal = INICIO_REAL.plusSeconds(10); // 10 s reales = 10 min de control

        assertEquals(
                FECHA.plusSeconds(600), r.instanteDeLectura(tsReal, true, VIGENCIA, realEn(11)));
    }

    @Test
    @DisplayName(
            "Con factor 60 la vigencia se escala: un ts de hace 1 h de control todavía es"
                    + " plausible")
    void vigenciaEscalada_conFactor() {
        RelojControl r = new RelojControl(FECHA, 60, INICIO_REAL, ZoneOffset.UTC);
        Instant ahora = FECHA.plusSeconds(60 * 60 * 2); // 2 min reales = 2 h de control
        Instant haceUnaHora = ahora.minusSeconds(3600);

        assertEquals(haceUnaHora, r.instanteDeLectura(haceUnaHora, false, VIGENCIA, realEn(120)));
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
