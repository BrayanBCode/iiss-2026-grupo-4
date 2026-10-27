package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.engine.inventario.Inventario;

/**
 * Control de temperatura de punta a punta: lecturas por MQTT, decisiones del core y comandos REST a
 * los switches. Primero con potencia de sobra y después con la potencia contratada como límite
 * efectivo.
 */
@DisplayName("Integración: control de temperatura según la carga total")
class ControlCargaTest extends IntegracionBase {

    @Test
    @DisplayName(
            "Carga no limitante: se encienden todas las que tienen frío y cada una se apaga al"
                    + " llegar a su temperatura")
    void cargaNoLimitante() throws Exception {
        // 3 × 1.0 kW = 3.0 kW < 3.7 kW contratados
        cargarSitio(
                POTENCIA_SITIO,
                habitacion("living", 21.0, 1.0),
                habitacion("dormitorio", 20.0, 1.0),
                habitacion("estudio", 19.0, 1.0));
        iniciar();

        temperatura("living", 18.0);
        temperatura("dormitorio", 17.5);
        temperatura("estudio", 16.0);
        esperarSwitch("living", "ON");
        esperarSwitch("dormitorio", "ON");
        esperarSwitch("estudio", "ON");

        temperatura("dormitorio", 20.1);
        esperarSwitch("dormitorio", "OFF");
        assertEquals("SATISFECHA", motivo("dormitorio"));
        assertSwitch("living", "ON");
        assertSwitch("estudio", "ON");

        temperatura("living", 21.0);
        temperatura("estudio", 19.5);
        esperarSwitch("living", "OFF");
        esperarSwitch("estudio", "OFF");
    }

    @Test
    @DisplayName("Carga no limitante: una habitación ya caliente no se enciende")
    void habitacionCalienteNoSeEnciende() throws Exception {
        cargarSitio(
                POTENCIA_SITIO, habitacion("living", 21.0, 1.0), habitacion("cocina", 19.0, 1.0));
        iniciar();

        temperatura("living", 18.0);
        temperatura("cocina", 22.0);
        esperarSwitch("living", "ON");
        esperarMotivo("cocina", "SATISFECHA");
        seMantiene(
                Duration.ofMillis(500),
                () ->
                        SWITCHES.comandosA(idSwitch("cocina")).stream()
                                .noneMatch(c -> c.accion().equals("ON")),
                () -> "se encendió la cocina, que ya estaba caliente");
    }

    @Test
    @DisplayName(
            "Carga limitante: con 3 × 1.5 kW y 3.7 kW contratados se encienden solo las dos de"
                    + " mayor déficit")
    void cargaLimitante() throws Exception {
        Inventario inv =
                inventario(
                        POTENCIA_SITIO,
                        habitacion("living", 22.0, 1.5),
                        habitacion("dormitorio", 22.0, 1.5),
                        habitacion("estudio", 22.0, 1.5));
        cargarSitio(inv);
        iniciar();

        temperatura("living", 16.0); // déficit 6
        temperatura("dormitorio", 17.0); // déficit 5
        temperatura("estudio", 20.0); // déficit 2
        esperarSwitch("living", "ON");
        esperarSwitch("dormitorio", "ON");
        esperarMotivo("estudio", "SIN_POTENCIA_DISPONIBLE");
        assertSwitch("estudio", "OFF");
        seMantiene(
                Duration.ofMillis(500),
                () -> potenciaEncendida(inv) <= POTENCIA_SITIO,
                () -> "se superó la potencia contratada: " + estadosSwitches(inv));
        assertTrue(picoDePotencia(inv) <= POTENCIA_SITIO, "comandos: " + SWITCHES.comandos());
    }

    @Test
    @DisplayName(
            "Carga limitante: cuando una se satisface, la potencia liberada pasa a la que esperaba,"
                    + " sin superar nunca la contratada")
    void cargaLimitante_redistribuye() throws Exception {
        // La que espera va primera en el inventario: si el engine mandara su ON antes del OFF de
        // la que se satisface, habría un instante con 4.5 kW encendidos.
        Inventario inv =
                inventario(
                        POTENCIA_SITIO,
                        habitacion("estudio", 22.0, 1.5),
                        habitacion("living", 22.0, 1.5),
                        habitacion("dormitorio", 22.0, 1.5));
        cargarSitio(inv);
        iniciar();
        temperatura("living", 16.0);
        temperatura("dormitorio", 17.0);
        temperatura("estudio", 20.0);
        esperarSwitch("living", "ON");
        esperarSwitch("dormitorio", "ON");

        temperatura("living", 22.0);
        esperarSwitch("living", "OFF");
        esperarSwitch("estudio", "ON");
        assertSwitch("dormitorio", "ON");
        assertEquals(
                Map.of("living", "OFF", "dormitorio", "ON", "estudio", "ON"), estadosSwitches(inv));
        assertTrue(
                picoDePotencia(inv) <= POTENCIA_SITIO,
                "en algún momento se superó la potencia contratada; comandos: "
                        + SWITCHES.comandos());
    }

    @Test
    @DisplayName("Carga limitante: una habitación que sola supera la contratada nunca se enciende")
    void habitacionQueExcedeLaContratada() throws Exception {
        cargarSitio(
                POTENCIA_SITIO, habitacion("galpon", 20.0, 4.0), habitacion("living", 21.0, 1.0));
        iniciar();

        temperatura("galpon", 10.0);
        temperatura("living", 18.0);
        esperarSwitch("living", "ON");
        esperarMotivo("galpon", "EXCEDE_CONTRATADA");
        assertSwitch("galpon", "OFF");
    }

    @Test
    @DisplayName("En la franja punta no se calefacciona aunque sobre potencia")
    void franjaPunta() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar("2026-10-01T18:00:00Z", 1); // jueves 18:00, dentro de 17:00–23:00

        temperatura("living", 15.0);
        esperarMotivo("living", "TARIFA_PUNTA");
        assertSwitch("living", "OFF");
    }
}
