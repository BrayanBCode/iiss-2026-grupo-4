package uy.edu.utec.iiss.engine.control;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ControlService: períodos del tick (T12) y la reconciliación (T36) escalados")
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

    @Test
    @DisplayName(
            "La reconciliación también se escala con el factor (T36), sin bajar de 1 s real: a ×60"
                    + " cada 1 s real, con factor 1 cada 15 s como antes")
    void reconciliacionEscalada() {
        assertEquals(15_000, ControlService.periodoReconciliacionMs(15, 1));
        assertEquals(5_000, ControlService.periodoReconciliacionMs(15, 3));
        assertEquals(30_000, ControlService.periodoReconciliacionMs(15, 0.5));
        assertEquals(1_000, ControlService.periodoReconciliacionMs(15, 60));
        assertEquals(
                ControlService.RECONCILIACION_MINIMA_MS,
                ControlService.periodoReconciliacionMs(15, 3600));
    }
}
