package uy.edu.utec.iiss.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Invariantes del modelo: valores que harían decidir mal se rechazan al construirlos. */
@DisplayName("Modelo del core (validaciones de construcción)")
class ModeloTest {

    @Test
    @DisplayName("ParametrosControl: histéresis negativa, vigencia no positiva o rango vacío se rechazan")
    void parametrosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new ParametrosControl(-0.1, Duration.ofMinutes(1), -40, 85));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosControl(Double.NaN, Duration.ofMinutes(1), -40, 85));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosControl(0.3, Duration.ZERO, -40, 85));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosControl(0.3, Duration.ofMinutes(-1), -40, 85));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosControl(0.3, Duration.ofMinutes(1), 50, 10));
        assertDoesNotThrow(() -> new ParametrosControl(0, Duration.ofSeconds(1), -40, 85));
    }

    @Test
    @DisplayName("FranjaHoraria: desde >= hasta se rechaza (no cruza la medianoche)")
    void franjaInvalida() {
        assertThrows(IllegalArgumentException.class,
                () -> new FranjaHoraria(LocalTime.of(23, 0), LocalTime.of(17, 0), DiasTarifa.HABILES));
        assertThrows(IllegalArgumentException.class,
                () -> new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(17, 0), DiasTarifa.TODOS));
    }

    @Test
    @DisplayName("Sitio sin zona usa la de Uruguay")
    void sitioSinZona() {
        FranjaHoraria f = new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES);
        assertEquals(Sitio.ZONA_URUGUAY, new Sitio("s", 1, f, null).zona());
    }
}
