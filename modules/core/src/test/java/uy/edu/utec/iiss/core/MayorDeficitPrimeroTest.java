package uy.edu.utec.iiss.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uy.edu.utec.iiss.core.CoreTestFixtures.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;

/**
 * Tests unitarios del criterio de selección "mayor déficit primero" (la letra pide un criterio no
 * azaroso y justificable cuando la carga no alcanza).
 */
@DisplayName("Criterio MayorDeficitPrimero")
class MayorDeficitPrimeroTest {

    private final MayorDeficitPrimero criterio = new MayorDeficitPrimero();

    private static EstadoHabitacion estado(String id, Double temperatura) {
        return new EstadoHabitacion(id, temperatura, false);
    }

    private static EstadoHabitacion encendida(String id, Double temperatura) {
        return new EstadoHabitacion(id, temperatura, true);
    }

    @Test
    @DisplayName("Ordena de mayor a menor déficit (esperada - actual)")
    void ordenaPorMayorDeficit() {
        List<ConfiguracionHabitacion> candidatas =
                List.of(hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0), hab(ROOM3, 21.0, 1.0));
        Map<String, EstadoHabitacion> estados =
                Map.of(
                        ROOM1, estado(ROOM1, 20.0), // déficit 1
                        ROOM2, estado(ROOM2, 15.0), // déficit 6
                        ROOM3, estado(ROOM3, 18.0)); // déficit 3

        assertEquals(List.of(ROOM2, ROOM3, ROOM1), criterio.ordenarCandidatas(candidatas, estados));
    }

    @Test
    @DisplayName(
            "El déficit se mide contra la esperada de cada habitación, no contra la temperatura"
                    + " absoluta")
    void deficitRelativoALaEsperadaPropia() {
        List<ConfiguracionHabitacion> candidatas =
                List.of(
                        hab(ROOM1, 18.0, 1.0), // 17 °C -> déficit 1
                        hab(ROOM2, 24.0, 1.0)); // 20 °C -> déficit 4 (aunque está más caliente)
        Map<String, EstadoHabitacion> estados =
                Map.of(
                        ROOM1, estado(ROOM1, 17.0),
                        ROOM2, estado(ROOM2, 20.0));

        assertEquals(List.of(ROOM2, ROOM1), criterio.ordenarCandidatas(candidatas, estados));
    }

    @Test
    @DisplayName("Empate de déficit: desempata por id, sin importar el orden de entrada")
    void empateDesempataPorId() {
        Map<String, EstadoHabitacion> estados =
                Map.of(
                        ROOM1, estado(ROOM1, 18.0),
                        ROOM2, estado(ROOM2, 18.0));
        ConfiguracionHabitacion h1 = hab(ROOM1, 21.0, 1.0);
        ConfiguracionHabitacion h2 = hab(ROOM2, 21.0, 1.0);

        assertEquals(List.of(ROOM1, ROOM2), criterio.ordenarCandidatas(List.of(h1, h2), estados));
        assertEquals(List.of(ROOM1, ROOM2), criterio.ordenarCandidatas(List.of(h2, h1), estados));
    }

    @Test
    @DisplayName(
            "Sin temperatura conocida el déficit es 0: queda detrás de las que sí tienen déficit")
    void sinTemperaturaQuedaAlFinal() {
        List<ConfiguracionHabitacion> candidatas =
                List.of(hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0));
        Map<String, EstadoHabitacion> estados =
                Map.of(
                        ROOM1, estado(ROOM1, null),
                        ROOM2, estado(ROOM2, 20.0));

        assertEquals(List.of(ROOM2, ROOM1), criterio.ordenarCandidatas(candidatas, estados));
    }

    @Test
    @DisplayName(
            "La que ya está encendida conserva el lugar si la otra la supera por menos del margen"
                    + " (0.5 °C)")
    void laEncendidaConservaElLugarDentroDelMargen() {
        List<ConfiguracionHabitacion> candidatas =
                List.of(hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0));
        Map<String, EstadoHabitacion> estados =
                Map.of(
                        ROOM1, encendida(ROOM1, 18.0), // déficit 3.0 (+0.5 por estar encendida)
                        ROOM2, estado(ROOM2, 17.6)); // déficit 3.4

        assertEquals(List.of(ROOM1, ROOM2), criterio.ordenarCandidatas(candidatas, estados));
    }

    @Test
    @DisplayName("La que ya está encendida pierde el lugar si la otra la supera por más del margen")
    void laEncendidaPierdeElLugarFueraDelMargen() {
        List<ConfiguracionHabitacion> candidatas =
                List.of(hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0));
        Map<String, EstadoHabitacion> estados =
                Map.of(
                        ROOM1, encendida(ROOM1, 18.0), // 3.0 + 0.5
                        ROOM2, estado(ROOM2, 17.0)); // 4.0

        assertEquals(List.of(ROOM2, ROOM1), criterio.ordenarCandidatas(candidatas, estados));
    }

    @Test
    @DisplayName("Con margen 0 el criterio es mayor déficit puro (la encendida no tiene ventaja)")
    void margenCero_esMayorDeficitPuro() {
        MayorDeficitPrimero puro = new MayorDeficitPrimero(0.0);
        List<ConfiguracionHabitacion> candidatas =
                List.of(hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0));
        Map<String, EstadoHabitacion> estados =
                Map.of(
                        ROOM1, encendida(ROOM1, 18.0),
                        ROOM2, estado(ROOM2, 17.9));

        assertEquals(List.of(ROOM2, ROOM1), puro.ordenarCandidatas(candidatas, estados));
    }

    @Test
    @DisplayName("Un margen negativo o no numérico se rechaza")
    void margenInvalido_seRechaza() {
        assertThrows(IllegalArgumentException.class, () -> new MayorDeficitPrimero(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new MayorDeficitPrimero(Double.NaN));
    }

    @Test
    @DisplayName("Sin candidatas devuelve una lista vacía")
    void sinCandidatas_listaVacia() {
        assertEquals(List.of(), criterio.ordenarCandidatas(List.of(), Map.of()));
    }
}
