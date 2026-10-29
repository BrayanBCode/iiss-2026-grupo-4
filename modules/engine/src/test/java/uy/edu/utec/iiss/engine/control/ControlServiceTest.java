package uy.edu.utec.iiss.engine.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ControlService: período del tick escalado con el factor (T12)")
class ControlServiceTest {

    @Test
    @DisplayName("Cada tick avanza tickMs de tiempo de control: el período real es tickMs / factor")
    void periodoEscalado() {
        assertEquals(1000, ControlService.periodoTickMs(1000, 1));
        assertEquals(33, ControlService.periodoTickMs(1000, 30)); // 33 ms reales ≈ 1 s de control
        assertEquals(2000, ControlService.periodoTickMs(1000, 0.5)); // más lento que el real
    }

    @Test
    @DisplayName(
            "Con factores muy altos el período no baja de 10 ms: a ×600 cada tick son 6 s de"
                    + " control")
    void periodoMinimo() {
        assertEquals(10, ControlService.periodoTickMs(1000, 600));
        assertEquals(10, ControlService.periodoTickMs(100, 600));
        assertEquals(ControlService.TICK_MINIMO_MS, ControlService.periodoTickMs(1000, 1_000_000));
    }
}
