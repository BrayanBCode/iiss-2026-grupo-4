package uy.edu.utec.iiss.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uy.edu.utec.iiss.core.CoreTestFixtures.*;

/**
 * Tests unitarios del criterio de selección "mayor déficit primero" (la letra
 * pide un criterio no azaroso y justificable cuando la carga no alcanza).
 */
@DisplayName("Criterio MayorDeficitPrimero")
class MayorDeficitPrimeroTest {

    private final MayorDeficitPrimero criterio = new MayorDeficitPrimero();

    private static EstadoHabitacion estado(String id, Double temperatura) {
        return new EstadoHabitacion(id, temperatura, false);
    }

    @Test
    @DisplayName("Ordena de mayor a menor déficit (esperada - actual)")
    void ordenaPorMayorDeficit() {
        List<ConfiguracionHabitacion> candidatas = List.of(
                hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0), hab(ROOM3, 21.0, 1.0));
        Map<String, EstadoHabitacion> estados = Map.of(
                ROOM1, estado(ROOM1, 20.0),   // déficit 1
                ROOM2, estado(ROOM2, 15.0),   // déficit 6
                ROOM3, estado(ROOM3, 18.0));  // déficit 3

        assertEquals(List.of(ROOM2, ROOM3, ROOM1), criterio.ordenarCandidatas(candidatas, estados));
    }

    @Test
    @DisplayName("El déficit se mide contra la esperada de cada habitación, no contra la temperatura absoluta")
    void deficitRelativoALaEsperadaPropia() {
        List<ConfiguracionHabitacion> candidatas = List.of(
                hab(ROOM1, 18.0, 1.0),        // 17 °C -> déficit 1
                hab(ROOM2, 24.0, 1.0));       // 20 °C -> déficit 4 (aunque está más caliente)
        Map<String, EstadoHabitacion> estados = Map.of(
                ROOM1, estado(ROOM1, 17.0),
                ROOM2, estado(ROOM2, 20.0));

        assertEquals(List.of(ROOM2, ROOM1), criterio.ordenarCandidatas(candidatas, estados));
    }

    @Test
    @DisplayName("Empate de déficit: desempata por id, sin importar el orden de entrada")
    void empateDesempataPorId() {
        Map<String, EstadoHabitacion> estados = Map.of(
                ROOM1, estado(ROOM1, 18.0),
                ROOM2, estado(ROOM2, 18.0));
        ConfiguracionHabitacion h1 = hab(ROOM1, 21.0, 1.0);
        ConfiguracionHabitacion h2 = hab(ROOM2, 21.0, 1.0);

        assertEquals(List.of(ROOM1, ROOM2), criterio.ordenarCandidatas(List.of(h1, h2), estados));
        assertEquals(List.of(ROOM1, ROOM2), criterio.ordenarCandidatas(List.of(h2, h1), estados));
    }

    @Test
    @DisplayName("Sin temperatura conocida el déficit es 0: queda detrás de las que sí tienen déficit")
    void sinTemperaturaQuedaAlFinal() {
        List<ConfiguracionHabitacion> candidatas = List.of(
                hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0));
        Map<String, EstadoHabitacion> estados = Map.of(
                ROOM1, estado(ROOM1, null),
                ROOM2, estado(ROOM2, 20.0));

        assertEquals(List.of(ROOM2, ROOM1), criterio.ordenarCandidatas(candidatas, estados));
    }
}