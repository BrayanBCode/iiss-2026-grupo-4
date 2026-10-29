package uy.edu.utec.iiss.core.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Invariantes del modelo: valores que harían decidir mal se rechazan al construirlos. */
@DisplayName("Modelo del core (validaciones de construcción)")
class ModeloTest {

    @Test
    @DisplayName(
            "ParametrosControl: histéresis negativa, vigencia no positiva o rango vacío se"
                    + " rechazan")
    void parametrosInvalidos() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ParametrosControl(-0.1, Duration.ofMinutes(1), -40, 85));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ParametrosControl(Double.NaN, Duration.ofMinutes(1), -40, 85));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ParametrosControl(0.3, Duration.ZERO, -40, 85));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ParametrosControl(0.3, Duration.ofMinutes(-1), -40, 85));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ParametrosControl(0.3, Duration.ofMinutes(1), 50, 10));
        assertDoesNotThrow(() -> new ParametrosControl(0, Duration.ofSeconds(1), -40, 85));
    }

    @Test
    @DisplayName("FranjaHoraria: desde >= hasta se rechaza (no cruza la medianoche)")
    void franjaInvalida() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FranjaHoraria(
                                LocalTime.of(23, 0), LocalTime.of(17, 0), DiasTarifa.HABILES));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FranjaHoraria(
                                LocalTime.of(17, 0), LocalTime.of(17, 0), DiasTarifa.TODOS));
    }

    @Test
    @DisplayName(
            "Sincronizacion (INICIAR): un factor de tiempo no positivo, NaN o infinito se rechaza")
    void sincronizacionFactorInvalido() {
        Instant ahora = Instant.parse("2026-10-05T13:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new Estimulo.Sincronizacion(ahora, 0));
        assertThrows(IllegalArgumentException.class, () -> new Estimulo.Sincronizacion(ahora, -2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Estimulo.Sincronizacion(ahora, Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Estimulo.Sincronizacion(ahora, Double.POSITIVE_INFINITY));
        assertEquals(60.0, new Estimulo.Sincronizacion(ahora, 60).factorTiempo());
    }

    @Test
    @DisplayName(
            "Estímulos: los campos obligatorios no pueden ser null y la configuración no se puede"
                    + " modificar desde afuera")
    void estimulosCamposObligatorios() {
        Instant ts = Instant.parse("2026-10-05T13:00:00Z");
        FranjaHoraria f =
                new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES);
        Sitio sitio = new Sitio("s", 3.7, f);

        assertThrows(NullPointerException.class, () -> new Estimulo.NuevaLectura(null, 20, ts));
        assertThrows(NullPointerException.class, () -> new Estimulo.NuevaLectura("a", 20, null));
        assertThrows(NullPointerException.class, () -> new Estimulo.Tick(null));
        assertThrows(
                NullPointerException.class,
                () -> new Estimulo.ConfiguracionActualizada(null, List.of()));
        assertThrows(
                NullPointerException.class,
                () -> new Estimulo.ConfiguracionActualizada(sitio, null));

        List<ConfiguracionHabitacion> original = new ArrayList<>();
        original.add(new ConfiguracionHabitacion("a", "A", 21, 1));
        Estimulo.ConfiguracionActualizada c =
                new Estimulo.ConfiguracionActualizada(sitio, original);
        original.clear();
        assertEquals(1, c.habitaciones().size()); // guardó una copia
        assertEquals(ts, new Estimulo.Tick(ts).ts());
        assertEquals(20.0, new Estimulo.NuevaLectura("a", 20, ts).temperaturaC());
    }

    @Test
    @DisplayName("Sitio sin zona usa la de Uruguay")
    void sitioSinZona() {
        FranjaHoraria f =
                new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES);
        assertEquals(Sitio.ZONA_URUGUAY, new Sitio("s", 1, f, null).zona());
    }
}
