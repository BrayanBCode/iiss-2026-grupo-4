package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.engine.inventario.Inventario;

/**
 * Cambios de configuración con el control en ejecución (§5.1 en caliente): se adoptan sin reiniciar
 * y sin perder las lecturas que ya había.
 */
@DisplayName("Integración: cambios de configuración en ejecución")
class CambioConfiguracionTest extends IntegracionBase {

    @Test
    @DisplayName("Bajar la temperatura esperada por debajo de la actual apaga la habitación")
    void bajarTemperaturaEsperada_apaga() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 19.0);
        esperarSwitch("living", "ON");

        modificarHabitacion(habitacion("living", 18.0, 1.0));
        esperarSwitch("living", "OFF");
        esperarMotivo("living", "SATISFECHA");
    }

    @Test
    @DisplayName(
            "Subir la temperatura esperada enciende la habitación con la lectura que ya tenía, sin"
                    + " esperar una nueva")
    void subirTemperaturaEsperada_enciende() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 18.0, 1.0));
        iniciar();
        temperatura("living", 19.0);
        esperarMotivo("living", "SATISFECHA");

        modificarHabitacion(habitacion("living", 22.0, 1.0));
        esperarSwitch("living", "ON");
    }

    @Test
    @DisplayName("Una habitación agregada en caliente se suscribe a su termostato y se controla")
    void agregarHabitacion() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        mvc.perform(
                        conClave(post("/habitaciones"))
                                .content(json.writeValueAsString(habitacion("cocina", 20.0, 1.0))))
                .andExpect(status().isCreated());
        temperatura("cocina", 15.0);
        esperarSwitch("cocina", "ON");
        assertSwitch("living", "ON");
    }

    @Test
    @DisplayName(
            "Una habitación eliminada en caliente deja de controlarse: ni se comanda ni se escucha"
                    + " su termostato")
    void eliminarHabitacion() throws Exception {
        cargarSitio(
                POTENCIA_SITIO, habitacion("living", 21.0, 1.0), habitacion("cocina", 20.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        temperatura("cocina", 16.0);
        esperarSwitch("cocina", "ON");

        mvc.perform(conClave(delete("/habitaciones/cocina"))).andExpect(status().isNoContent());
        esperar(
                () -> !BROKER.haySuscriptor(topic("cocina")),
                () -> "sigue suscrito al termostato de la cocina");
        int comandosACocina = SWITCHES.comandosA("sw-cocina").size();
        BROKER.publicarTemperatura(topic("cocina"), 30.0);

        seMantiene(
                Duration.ofMillis(1500), // más que una reconciliación
                () -> SWITCHES.comandosA("sw-cocina").size() == comandosACocina,
                () -> "comandó el switch de una habitación eliminada: " + SWITCHES.comandos());
        assertTrue(adaptador.estados().stream().noneMatch(e -> e.idHabitacion().equals("cocina")));
    }

    @Test
    @DisplayName(
            "Bajar la potencia contratada en caliente apaga la de menor déficit y respeta el nuevo"
                    + " límite")
    void bajarPotenciaContratada() throws Exception {
        Inventario holgado =
                inventario(
                        POTENCIA_SITIO,
                        habitacion("living", 22.0, 1.5),
                        habitacion("dormitorio", 22.0, 1.5));
        cargarSitio(holgado);
        iniciar();
        temperatura("living", 16.0); // déficit 6
        temperatura("dormitorio", 20.0); // déficit 2
        esperarSwitch("living", "ON");
        esperarSwitch("dormitorio", "ON");

        Inventario ajustado =
                inventario(2.0, holgado.habitaciones().toArray(Inventario.Habitacion[]::new));
        cargarSitio(ajustado);
        esperarSwitch("dormitorio", "OFF");
        esperarMotivo("dormitorio", "SIN_POTENCIA_DISPONIBLE");
        assertSwitch("living", "ON");
    }

    @Test
    @DisplayName(
            "Cambiar el switch de una habitación: el nuevo se comanda y el viejo no se toca más")
    void cambiarSwitch() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        Inventario.Habitacion h = habitacion("living", 21.0, 1.0);
        modificarHabitacion(
                new Inventario.Habitacion(
                        h.id(),
                        h.nombre(),
                        h.temperaturaEsperada(),
                        h.potenciaKW(),
                        h.idTermostato(),
                        h.topicTermostato(),
                        "sw-living-nuevo",
                        h.urlSwitch()));
        esperar(
                () -> SWITCHES.estado("sw-living-nuevo").equals("ON"),
                () -> "no se encendió el switch nuevo: " + SWITCHES.comandos());
        int comandosAlViejo = SWITCHES.comandosA("sw-living").size();
        temperatura("living", 25.0);
        esperar(
                () -> SWITCHES.estado("sw-living-nuevo").equals("OFF"),
                () -> "no se apagó el switch nuevo: " + SWITCHES.comandos());
        assertTrue(
                SWITCHES.comandosA("sw-living").size() == comandosAlViejo,
                "se siguió comandando el switch viejo: " + SWITCHES.comandos());
    }

    @Test
    @DisplayName("Cambiar el topic del termostato: se escucha el nuevo y se deja el viejo")
    void cambiarTopic() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        Inventario.Habitacion h = habitacion("living", 21.0, 1.0);
        String topicNuevo = "ht-living-v2/status/temperature:0";
        modificarHabitacion(
                new Inventario.Habitacion(
                        h.id(),
                        h.nombre(),
                        h.temperaturaEsperada(),
                        h.potenciaKW(),
                        "ht-living-v2",
                        topicNuevo,
                        h.idSwitch(),
                        h.urlSwitch()));
        esperar(() -> BROKER.haySuscriptor(topicNuevo), () -> "no se suscribió al topic nuevo");
        esperar(() -> !BROKER.haySuscriptor(topic("living")), () -> "sigue en el topic viejo");

        BROKER.publicarTemperatura(topicNuevo, 25.0);
        esperarSwitch("living", "OFF");
    }
}
